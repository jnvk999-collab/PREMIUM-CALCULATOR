package com.financebrain.data

import com.financebrain.parser.Categorizer
import com.financebrain.parser.ParsedTransaction
import java.security.MessageDigest
import java.util.Calendar
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val db: AppDatabase) {
    companion object { const val IGNORED = "__ignored__" }

    val transactions: Flow<List<Transaction>> = db.transactions().all()
    val accounts: Flow<List<Account>> = db.accounts().all()
    val count: Flow<Int> = db.transactions().count()

    fun between(from: Long, to: Long) = db.transactions().between(from, to)

    /** Stores a parsed alert. Returns true when it was new. */
    suspend fun ingest(p: ParsedTransaction, raw: String, source: Source): Boolean {
        // An email for a payment the SMS already captured: keep the SMS row, but borrow the
        // merchant name if the SMS only had a generic one.
        if (source != Source.SMS) {
            val twin = db.transactions().similar(p.amountPaise, p.direction, p.timestamp - 36 * 3_600_000L, p.timestamp + 36 * 3_600_000L)
                .firstOrNull { it.source != source }
            if (twin != null) {
                if (twin.counterparty.endsWith("transaction") || twin.counterparty == "Bank Transfer") {
                    val cat = Categorizer.categorize(p.counterparty, twin.channel, twin.direction, raw)
                    db.transactions().update(twin.copy(counterparty = p.counterparty, category = if (twin.userEdited) twin.category else cat))
                }
                return false
            }
        }
        val key = dedupKey(p)
        val counterparty = p.counterparty
        val rule = db.merchantRules().get(Categorizer.merchantKey(counterparty))
        if (rule?.category == IGNORED) return false
        val category = rule?.category ?: Categorizer.categorize(counterparty, p.channel, p.direction, raw)
        val t = Transaction(
            amountPaise = p.amountPaise,
            direction = p.direction,
            timestamp = p.timestamp,
            bank = p.bank,
            accountTail = p.accountTail,
            counterparty = counterparty,
            category = category,
            channel = p.channel,
            reference = p.reference,
            balancePaise = p.balancePaise,
            source = source,
            rawText = raw,
            dedupKey = key,
            isTransfer = category == Categories.TRANSFER,
        )
        val id = db.transactions().insert(t)
        // An account exists only once a bank has reported a balance for it. Alerts that merely
        // mention a masked number (cards, promos that slipped through, one-off references) do not
        // create accounts.
        if (p.accountTail != null && p.accountKind == "BANK" && p.balancePaise != null) {
            val existing = db.accounts().get(p.bank, p.accountTail)
            val newer = existing?.balanceAt == null || p.timestamp >= existing.balanceAt
            if (existing == null || newer) {
                db.accounts().upsert(Account(p.bank, p.accountTail, p.balancePaise, p.timestamp, p.accountKind))
            }
        }
        return id != -1L
    }

    suspend fun addManual(amountPaise: Long, direction: Direction, counterparty: String, category: String, timestamp: Long, note: String?) {
        val t = Transaction(
            amountPaise = amountPaise,
            direction = direction,
            timestamp = timestamp,
            bank = "Cash",
            accountTail = null,
            counterparty = counterparty.ifBlank { "Cash" },
            category = category,
            channel = "CASH",
            reference = null,
            balancePaise = null,
            note = note,
            source = Source.MANUAL,
            rawText = null,
            dedupKey = "manual-${System.nanoTime()}",
            userEdited = true,
        )
        db.transactions().insert(t)
    }

    suspend fun setCategory(t: Transaction, category: String, rememberForMerchant: Boolean) {
        db.transactions().update(t.copy(category = category, isTransfer = category == Categories.TRANSFER, userEdited = true))
        if (rememberForMerchant) {
            db.merchantRules().upsert(MerchantRule(Categorizer.merchantKey(t.counterparty), category))
            db.transactions().recategorizeMerchant(t.counterparty, category)
        }
    }

    /** "Not mine / spam": delete it and never import this counterparty again. */
    suspend fun markSpam(t: Transaction) {
        db.merchantRules().upsert(MerchantRule(Categorizer.merchantKey(t.counterparty), IGNORED))
        db.transactions().deleteByCounterparty(t.counterparty)
    }

    /** Full rescan: drop everything that came from SMS (manual entries and rules survive). */
    suspend fun purgeSms() {
        db.transactions().deleteBySource(Source.SMS)
        db.accounts().clear()
        db.processedSms().clear()
    }

    /**
     * Money moved between your own accounts shows up twice: a debit on one account and a credit
     * on another, same amount, within a few days. Both legs are marked as transfers so they
     * never count as spending or income.
     */
    suspend fun detectInternalTransfers(): Int {
        val all = db.transactions().allNow().filter { !it.userEdited && it.source != Source.MANUAL }
        val debits = all.filter { it.direction == Direction.DEBIT && !it.isTransfer }
        val credits = all.filter { it.direction == Direction.CREDIT && !it.isTransfer }.groupBy { it.amountPaise }
        val usedCredits = HashSet<Long>()
        val changed = ArrayList<Transaction>()
        val window = 3 * 86_400_000L
        for (d in debits) {
            if (d.amountPaise < 50_000) continue // ignore tiny amounts; too many coincidences
            val c = credits[d.amountPaise]?.firstOrNull { c ->
                c.id !in usedCredits &&
                    kotlin.math.abs(c.timestamp - d.timestamp) <= window &&
                    (c.bank != d.bank || c.accountTail != d.accountTail)
            } ?: continue
            usedCredits += c.id
            changed += d.copy(isTransfer = true, category = Categories.TRANSFER)
            changed += c.copy(isTransfer = true, category = Categories.TRANSFER)
        }
        if (changed.isNotEmpty()) db.transactions().updateAll(changed)
        return changed.size / 2
    }

    /** Drop account rows that never received a balance (created by older parser versions). */
    suspend fun pruneAccounts() = db.accounts().deleteWithoutBalance()

    suspend fun setNote(t: Transaction, note: String?) = db.transactions().update(t.copy(note = note))
    suspend fun delete(t: Transaction) = db.transactions().delete(t.id)
    suspend fun processedSmsIds(): Set<Long> = db.processedSms().ids().toSet()
    suspend fun markProcessed(rows: List<ProcessedSms>) = db.processedSms().insertAll(rows)
    suspend fun resetSmsProgress() = db.processedSms().clear()
    suspend fun isEmailProcessed(id: String) = db.processedEmail().exists(id) > 0
    suspend fun markEmailProcessed(id: String, parsed: Boolean) = db.processedEmail().insert(ProcessedEmail(id, parsed))

    private fun dedupKey(p: ParsedTransaction): String {
        val cal = Calendar.getInstance().apply { timeInMillis = p.timestamp }
        val day = "%04d%02d%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        val base = if (p.reference != null) "${p.bank}|${p.amountPaise}|${p.direction}|${p.reference}"
        else "${p.bank}|${p.amountPaise}|${p.direction}|$day|${Categorizer.merchantKey(p.counterparty)}"
        return MessageDigest.getInstance("SHA-1").digest(base.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
