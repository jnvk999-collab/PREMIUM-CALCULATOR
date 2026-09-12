package com.financebrain.data

import com.financebrain.parser.Categorizer
import com.financebrain.parser.ParsedTransaction
import java.security.MessageDigest
import java.util.Calendar
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val db: AppDatabase,
    private val ignoredBanks: () -> Set<String> = { emptySet() },
    private val ignoredAccounts: () -> Set<String> = { emptySet() },
) {
    val accountRefs: Flow<List<AccountRef>> = db.transactions().accountRefs()

    /** Remove everything from one account the user does not want tracked. */
    suspend fun purgeAccount(bank: String, tail: String) {
        db.transactions().deleteByAccount(bank, tail)
        db.accounts().deleteByAccount(bank, tail)
        db.cards().delete("$bank|$tail")
    }
    companion object { const val IGNORED = "__ignored__" }

    /** Remove everything from a bank the user does not want tracked. */
    suspend fun purgeBank(bank: String) {
        db.transactions().deleteByBank(bank)
        db.accounts().deleteByBank(bank)
    }

    val transactions: Flow<List<Transaction>> = db.transactions().all()
    val accounts: Flow<List<Account>> = db.accounts().all()
    val count: Flow<Int> = db.transactions().count()
    val balanceAnchors: Flow<List<BalanceAnchor>> = db.balanceAnchors().all()

    val cards: Flow<List<CreditCard>> = db.cards().all()
    val goals: Flow<List<Goal>> = db.goals().all()
    val receivables: Flow<List<Receivable>> = db.receivables().all()
    val informalLoans: Flow<List<InformalLoan>> = db.informalLoans().all()
    val controlled: Flow<List<ControlledCategory>> = db.discipline().controlled()
    val zeroTolerance: Flow<List<ZeroTolerance>> = db.discipline().zero()
    val disciplineEntries: Flow<List<DisciplineEntry>> = db.discipline().entries()

    val holdings: Flow<List<Holding>> = db.holdings().all()
    val loans: Flow<List<Loan>> = db.loans().all()
    suspend fun saveHolding(h: Holding) = db.holdings().upsert(h)
    suspend fun deleteHolding(id: Long) = db.holdings().delete(id)
    suspend fun saveLoan(l: Loan) = db.loans().upsert(l)
    suspend fun deleteLoan(id: Long) = db.loans().delete(id)

    /** One-time seed of the owner's known holdings and loan, entered from their broker screens. */
    suspend fun seedOwnerPortfolio() {
        if (db.holdings().count() > 0 || db.loans().count() > 0) return
        val at = System.currentTimeMillis()
        listOf(
            Holding(name = "Invesco India Mid Cap Fund Direct Growth", type = "MUTUAL_FUND", account = "Groww (N)", investedPaise = 63_316_00, currentPaise = 68_835_00, updatedAt = at),
            Holding(name = "Groww mutual funds · 10 holdings (Bandhan Small Cap, Nippon Growth Mid Cap, Parag Parikh Flexi Cap …)", type = "MUTUAL_FUND", account = "Groww (k)", investedPaise = 38_98_180_00, currentPaise = 41_58_990_00, updatedAt = at, notes = "Split into individual funds any time"),
            Holding(name = "Hy-Tech Engineers", type = "STOCK", account = "Groww (k)", units = 927.0, investedPaise = 67_272_00, currentPaise = 79_351_00, updatedAt = at),
            Holding(name = "Chembond Material Technologies", type = "STOCK", account = "Groww (k)", units = 246.0, investedPaise = 53_348_00, currentPaise = 53_412_00, updatedAt = at),
            Holding(name = "ASK Automotive", type = "STOCK", account = "Groww (k)", units = 70.0, investedPaise = 43_219_00, currentPaise = 43_719_00, updatedAt = at),
            Holding(name = "Tempsens Instruments", type = "STOCK", account = "Groww (k)", units = 21.0, investedPaise = 11_918_00, currentPaise = 12_281_00, updatedAt = at),
            Holding(name = "NSE (unlisted) · 200 shares @ ₹1,970", type = "UNLISTED", account = "Unlisted", units = 200.0, investedPaise = 3_94_000_00, currentPaise = 3_94_000_00, updatedAt = at, notes = "Update the current price when you have a quote"),
            Holding(name = "Bank deposits", type = "DEPOSIT", account = "Bank FD", investedPaise = 8_00_000_00, currentPaise = 8_00_000_00, updatedAt = at),
        ).forEach { db.holdings().upsert(it) }
        db.loans().upsert(Loan(lender = "Personal loan", type = "PERSONAL", outstandingPaise = 33_50_000_00, asOf = at, annualRatePct = 8.5, emiPaise = 60_000_00))
    }
    suspend fun saveCard(c: CreditCard) = db.cards().upsert(c)
    suspend fun deleteCard(key: String) = db.cards().delete(key)
    suspend fun saveGoal(g: Goal) = db.goals().upsert(g)
    suspend fun deleteGoal(id: Long) = db.goals().delete(id)
    suspend fun saveReceivable(r: Receivable) = db.receivables().upsert(r)
    suspend fun deleteReceivable(id: Long) = db.receivables().delete(id)
    suspend fun saveInformalLoan(l: InformalLoan) = db.informalLoans().upsert(l)
    suspend fun deleteInformalLoan(id: Long) = db.informalLoans().delete(id)
    suspend fun saveControlled(c: ControlledCategory) = db.discipline().upsertControlled(c)
    suspend fun deleteControlled(category: String) = db.discipline().deleteControlled(category)
    suspend fun saveZero(z: ZeroTolerance) = db.discipline().upsertZero(z)
    suspend fun deleteZero(category: String) = db.discipline().deleteZero(category)
    suspend fun addDisciplineEntry(e: DisciplineEntry) = db.discipline().insertEntry(e)
    suspend fun deleteDisciplineEntry(id: Long) = db.discipline().deleteEntry(id)

    suspend fun setBalance(key: String, amountPaise: Long, at: Long) = db.balanceAnchors().upsert(BalanceAnchor(key, amountPaise, at))
    suspend fun clearBalance(key: String) = db.balanceAnchors().delete(key)

    fun between(from: Long, to: Long) = db.transactions().between(from, to)

    /** Stores a parsed alert. Returns true when it was new. */
    suspend fun ingest(p: ParsedTransaction, raw: String, source: Source): Boolean {
        if (p.bank in ignoredBanks()) return false
        if (p.accountTail != null && "${p.bank}|${p.accountTail}" in ignoredAccounts()) return false
        // An email for a payment the SMS already captured: keep the SMS row, but borrow the
        // merchant name if the SMS only had a generic one.
        val platformConfirmation = p.accountKind == "INVEST"
        if (source != Source.SMS || platformConfirmation) {
            val twin = db.transactions().similar(p.amountPaise, p.direction, p.timestamp - 36 * 3_600_000L, p.timestamp + 36 * 3_600_000L)
                .firstOrNull { (it.source != source || platformConfirmation) && it.bank != p.bank }
            if (twin != null) {
                // The bank already recorded the debit: label it with the fund/platform and file it as an investment.
                if (platformConfirmation && !twin.userEdited) {
                    db.transactions().update(twin.copy(counterparty = p.counterparty, category = Categories.INVESTMENT, isTransfer = false))
                    return false
                }
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
            isTransfer = category == Categories.TRANSFER || category == Categories.CARD_BILL,
            accountKind = p.accountKind,
        )
        val id = db.transactions().insert(t)
        if (id != -1L && p.accountKind == "CARD" && p.accountTail != null) {
            val ck = "${p.bank}|${p.accountTail}"
            if (db.cards().get(ck) == null) db.cards().upsert(CreditCard(ck, "${p.bank} Card ··${p.accountTail}", p.bank, p.accountTail, null, 1))
        }
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
