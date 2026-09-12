package com.financebrain.data

import com.financebrain.parser.Categorizer
import com.financebrain.parser.ParsedTransaction
import java.security.MessageDigest
import java.util.Calendar
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val db: AppDatabase) {

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
        if (p.accountTail != null && p.accountKind == "BANK") {
            val existing = db.accounts().get(p.bank, p.accountTail)
            val newer = existing?.balanceAt == null || p.timestamp >= existing.balanceAt
            if (existing == null || (p.balancePaise != null && newer)) {
                db.accounts().upsert(
                    Account(
                        bank = p.bank,
                        accountTail = p.accountTail,
                        balancePaise = p.balancePaise ?: existing?.balancePaise,
                        balanceAt = if (p.balancePaise != null) p.timestamp else existing?.balanceAt,
                        kind = p.accountKind,
                    )
                )
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
