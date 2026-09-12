package com.financebrain.data

import com.financebrain.parser.Categorizer
import com.financebrain.ui.monthStart
import java.util.Calendar

data class CategoryTotal(val category: String, val paise: Long, val share: Float, val count: Int)

data class Recurring(
    val name: String, val amountPaise: Long, val nextDue: Long, val category: String, val occurrences: Int,
    val direction: Direction = Direction.DEBIT, val lastAt: Long = 0, val bank: String = "", val monthsSeen: Int = 0,
)

data class SalaryInfo(val employer: String, val amountPaise: Long, val lastAt: Long, val nextExpected: Long, val months: Int, val bank: String, val confirmed: Boolean)

data class LoanInfo(val lender: String, val emiPaise: Long, val nextDue: Long, val paidLast12Paise: Long, val installments: Int, val bank: String)

data class InvestmentLine(val name: String, val platform: String, val investedPaise: Long, val redeemedPaise: Long, val count: Int, val lastAt: Long, val monthly: Boolean)

data class MonthBalance(val startPaise: Long?, val endPaise: Long?, val nowPaise: Long?)

data class MonthSummary(val monthStart: Long, val incomePaise: Long, val expensePaise: Long, val investedPaise: Long = 0, val endBalancePaise: Long? = null) {
    val savedPaise get() = incomePaise - expensePaise - investedPaise
}

object Insights {

    /** Spending only: debits that are neither transfers between own accounts nor investments. */
    fun isSpend(t: Transaction) = t.direction == Direction.DEBIT && !t.isTransfer && t.category != Categories.INVESTMENT && t.category != Categories.CARD_BILL
    fun isIncome(t: Transaction) = t.direction == Direction.CREDIT && !t.isTransfer && t.category != Categories.INVESTMENT
    fun isInvestment(t: Transaction) = t.direction == Direction.DEBIT && !t.isTransfer && t.category == Categories.INVESTMENT
    /** Money coming back from investments (redemptions) is not income. */
    fun isRedemption(t: Transaction) = t.direction == Direction.CREDIT && t.category == Categories.INVESTMENT

    fun categoryTotals(list: List<Transaction>): List<CategoryTotal> {
        val spends = list.filter(::isSpend)
        val total = spends.sumOf { it.amountPaise }.coerceAtLeast(1)
        return spends.groupBy { it.category }
            .map { (c, ts) -> CategoryTotal(c, ts.sumOf { it.amountPaise }, ts.sumOf { it.amountPaise }.toFloat() / total, ts.size) }
            .sortedByDescending { it.paise }
    }

    fun dailySpend(list: List<Transaction>, days: Int): LongArray {
        val arr = LongArray(days)
        for (t in list.filter(::isSpend)) {
            val d = com.financebrain.ui.dayOfMonth(t.timestamp) - 1
            if (d in 0 until days) arr[d] += t.amountPaise
        }
        return arr
    }

    fun monthSeries(all: List<Transaction>, months: Int, now: Long, anchors: List<BalanceAnchor> = emptyList()): List<MonthSummary> {
        val out = ArrayList<MonthSummary>()
        for (i in (months - 1) downTo 0) {
            val start = Calendar.getInstance().apply { timeInMillis = monthStart(now); add(Calendar.MONTH, -i) }.timeInMillis
            val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.MONTH, 1) }.timeInMillis
            val inMonth = all.filter { it.timestamp in start until end }
            out += MonthSummary(
                start,
                inMonth.filter(::isIncome).sumOf { it.amountPaise },
                inMonth.filter(::isSpend).sumOf { it.amountPaise },
                inMonth.filter(::isInvestment).sumOf { it.amountPaise },
                balanceAt(all, anchors, minOf(end, now + 1)),
            )
        }
        return out
    }

    /** Movements that change a bank balance: bank-account alerts, not cash entries, card spends or platform confirmations. */
    private fun movesBankMoney(t: Transaction) =
        t.source != Source.MANUAL && t.accountKind != "CARD" && t.bank !in com.financebrain.parser.BankSmsParser.investmentPlatforms

    /**
     * Movements that change what you actually have. A card spend is money gone even though it leaves
     * the bank later, so it counts here; paying the card bill afterwards would count it twice, so it
     * does not. Money moved between your own accounts is not a movement at all.
     */
    private fun movesYourMoney(t: Transaction) =
        t.source != Source.MANUAL && !t.isTransfer && t.category != Categories.CARD_BILL &&
            t.bank !in com.financebrain.parser.BankSmsParser.investmentPlatforms

    private fun netFlow(rows: List<Transaction>, from: Long, to: Long, inclusive: Boolean = false): Long =
        rows.filter { (if (inclusive) it.timestamp >= from else it.timestamp > from) && it.timestamp <= to && movesBankMoney(it) }
            .sumOf { if (it.direction == Direction.CREDIT) it.amountPaise else -it.amountPaise }

    /** Balance at [at] carried forward (or backward) from a user-entered anchor. */
    fun runningBalance(rows: List<Transaction>, anchor: BalanceAnchor, at: Long): Long =
        if (at >= anchor.at) anchor.amountPaise + netFlow(rows, anchor.at, at, inclusive = true)
        else anchor.amountPaise - netFlow(rows, at, anchor.at)

    /**
     * How one account's balance was arrived at: a starting figure (what you entered, or the last
     * balance the bank itself reported, whichever is newer) with every movement since applied.
     */
    data class BalanceBuild(
        val basePaise: Long, val baseAt: Long, val fromUser: Boolean,
        val creditsPaise: Long, val debitsPaise: Long,
    ) {
        val paise get() = basePaise + creditsPaise - debitsPaise
    }

    /** Null when neither you nor the bank has ever given this account a figure to start from. */
    fun accountBalance(rows: List<Transaction>, anchor: BalanceAnchor?, at: Long): BalanceBuild? {
        val bankRows = rows.filter(::movesBankMoney)
        val reported = bankRows.filter { it.balancePaise != null && it.timestamp <= at }.maxByOrNull { it.timestamp }
        // What you typed wins: you looked at the bank, so run it forward rather than second-guessing it.
        val useAnchor = anchor != null && anchor.at <= at
        val basePaise: Long
        val baseAt: Long
        when {
            useAnchor -> { basePaise = anchor!!.amountPaise; baseAt = anchor.at }
            reported != null -> { basePaise = reported.balancePaise!!; baseAt = reported.timestamp }
            anchor != null -> return BalanceBuild(anchor.amountPaise, anchor.at, true, 0, 0)
            else -> return null
        }
        // Your own figure is the opening balance for that moment, so everything from it counts;
        // a bank's figure already includes the alert that quoted it, so that one does not.
        val after = bankRows.filter { (if (useAnchor) it.timestamp >= baseAt else it.timestamp > baseAt) && it.timestamp <= at }
        return BalanceBuild(
            basePaise, baseAt, useAnchor,
            after.filter { it.direction == Direction.CREDIT }.sumOf { it.amountPaise },
            after.filter { it.direction == Direction.DEBIT }.sumOf { it.amountPaise },
        )
    }

    /**
     * What you have, and how it got there: a starting figure and everything that has moved since,
     * across every account and card. Card spends count as money gone; card bill payments do not,
     * because that money was already counted when the card was swiped.
     */
    fun wallet(all: List<Transaction>, anchors: List<BalanceAnchor>, at: Long): BalanceBuild? {
        val total = anchors.firstOrNull { it.isTotal }
        val basePaise: Long
        val baseAt: Long
        val fromUser: Boolean
        if (total != null) {
            basePaise = total.amountPaise; baseAt = total.at; fromUser = true
        } else {
            val builds = all.filter { it.accountTail != null && it.accountKind != "CARD" }
                .groupBy { it.bank + "|" + it.accountTail }
                .mapNotNull { (key, rows) -> accountBalance(rows, anchors.firstOrNull { it.key == key }, at) }
            if (builds.isEmpty()) return null
            basePaise = builds.sumOf { it.basePaise }
            baseAt = builds.maxOf { it.baseAt }
            fromUser = builds.any { it.fromUser }
        }
        val rows = all.filter { (if (fromUser) it.timestamp >= baseAt else it.timestamp > baseAt) && it.timestamp <= at && movesYourMoney(it) }
        return BalanceBuild(
            basePaise, baseAt, fromUser,
            rows.filter { it.direction == Direction.CREDIT }.sumOf { it.amountPaise },
            rows.filter { it.direction == Direction.DEBIT }.sumOf { it.amountPaise },
        )
    }

    /**
     * Balance across accounts at [at]. A total you entered wins and runs forward from there.
     * Otherwise every account starts from its newest known figure and every movement since is applied,
     * so the total keeps moving even when the bank stops quoting a balance in its alerts.
     */
    fun balanceAt(all: List<Transaction>, anchors: List<BalanceAnchor>, at: Long): Long? {
        anchors.firstOrNull { it.isTotal }?.let { return runningBalance(all, it, at) }
        val byKey = all.filter { it.accountTail != null }.groupBy { it.bank + "|" + it.accountTail }
        val totals = HashMap<String, Long>()
        for ((key, rows) in byKey) {
            accountBalance(rows, anchors.firstOrNull { it.key == key }, at)?.let { totals[key] = it.paise }
        }
        for (a in anchors) if (!a.isTotal && a.key !in totals && a.at <= at) totals[a.key] = a.amountPaise
        if (totals.isEmpty()) return null
        return totals.values.sum()
    }

    /** Sum of each account's last reported balance on or before [at]. Null when no account has reported one. */
    fun endBalance(all: List<Transaction>, at: Long): Long? {
        val latest = HashMap<String, Transaction>()
        for (t in all) {
            if (t.balancePaise == null || t.accountTail == null || t.timestamp >= at) continue
            val key = t.bank + "|" + t.accountTail
            val cur = latest[key]
            if (cur == null || t.timestamp > cur.timestamp) latest[key] = t
        }
        if (latest.isEmpty()) return null
        return latest.values.sumOf { it.balancePaise!! }
    }

    fun topMerchants(list: List<Transaction>, n: Int = 5): List<Pair<String, Long>> =
        list.filter(::isSpend).groupBy { it.counterparty }
            .map { (m, ts) -> m to ts.sumOf { it.amountPaise } }
            .sortedByDescending { it.second }.take(n)

    /**
     * A payment to the same merchant, for roughly the same amount, seen in at least two
     * different months around the same day of month is treated as recurring.
     */
    fun recurring(all: List<Transaction>, now: Long): List<Recurring> = recurringFor(all, now, Direction.DEBIT) + recurringFor(all, now, Direction.CREDIT)

    /**
     * Same counterparty, similar amount (within ~10%), seen in at least two different months
     * around the same day of month. Works for outgoings (EMI, SIP, rent, subscriptions) and
     * incomings (salary, rent received, interest).
     */
    fun recurringFor(all: List<Transaction>, now: Long, direction: Direction): List<Recurring> {
        val rows = all.filter { it.direction == direction && !it.isTransfer }
        val groups = rows.groupBy { Categorizer.merchantKey(it.counterparty) }
        val out = ArrayList<Recurring>()
        for ((_, tsAll) in groups) {
            if (tsAll.size < 2) continue
            // Split by amount band so a merchant with both a ₹199 plan and a ₹5,000 EMI yields two lines.
            val bands = tsAll.sortedBy { it.amountPaise }.fold(ArrayList<ArrayList<Transaction>>()) { acc, t ->
                val cur = acc.lastOrNull()
                if (cur != null && t.amountPaise <= cur.first().amountPaise * 1.12) cur += t else acc += arrayListOf(t)
                acc
            }
            for (ts in bands) {
                if (ts.size < 2) continue
                val byMonth = ts.groupBy { monthStart(it.timestamp) }
                if (byMonth.size < 2) continue
                val days = ts.map { Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.DAY_OF_MONTH) }
                val spread = days.max() - days.min()
                if (spread > 7 && spread < 23) continue
                val last = ts.maxBy { it.timestamp }
                val next = Calendar.getInstance().apply { timeInMillis = last.timestamp; add(Calendar.MONTH, 1) }.timeInMillis
                if (next < now - 45 * 86_400_000L) continue   // stopped a while ago
                out += Recurring(
                    last.counterparty, ts.map { it.amountPaise }.average().toLong(), next, last.category, ts.size,
                    direction, last.timestamp, last.bank, byMonth.size,
                )
            }
        }
        return out.sortedBy { it.nextDue }
    }

    /** Salary: a credit categorised as salary, else the largest monthly recurring credit. */
    fun salary(all: List<Transaction>, recurringCredits: List<Recurring>, now: Long): SalaryInfo? {
        val explicit = all.filter { isIncome(it) && it.category == Categories.SALARY }.sortedByDescending { it.timestamp }
        if (explicit.isNotEmpty()) {
            val last = explicit.first()
            val months = explicit.map { monthStart(it.timestamp) }.distinct().size
            val next = Calendar.getInstance().apply { timeInMillis = last.timestamp; add(Calendar.MONTH, 1) }.timeInMillis
            return SalaryInfo(last.counterparty, last.amountPaise, last.timestamp, next, months, last.bank, true)
        }
        val guess = recurringCredits.filter { it.monthsSeen >= 2 && it.amountPaise >= 1_000_000 }.maxByOrNull { it.amountPaise } ?: return null
        return SalaryInfo(guess.name, guess.amountPaise, guess.lastAt, guess.nextDue, guess.monthsSeen, guess.bank, false)
    }

    /** Loans and EMIs: recurring debits filed under EMI, or whose name reads like a lender. */
    fun loans(all: List<Transaction>, recurringDebits: List<Recurring>, now: Long): List<LoanInfo> {
        val lenderRe = Regex("""\b(emi|loan|finance|fin\b|finserv|capital|housing|hdfc ltd|lic hfl|bajaj|tata cap|home credit|kreditbee|moneyview|navi|lending|nbfc|ach d|nach|ecs)\b""", RegexOption.IGNORE_CASE)
        val yearAgo = now - 365L * 86_400_000L
        return recurringDebits.filter { r -> r.category == Categories.EMI || lenderRe.containsMatchIn(r.name) }
            .map { r ->
                val key = Categorizer.merchantKey(r.name)
                val paid = all.filter { it.direction == Direction.DEBIT && it.timestamp >= yearAgo && Categorizer.merchantKey(it.counterparty) == key }
                LoanInfo(r.name, r.amountPaise, r.nextDue, paid.sumOf { it.amountPaise }, paid.size, r.bank)
            }.sortedByDescending { it.emiPaise }
    }

    /** Investments grouped by fund/platform, from money actually sent (cost basis, not market value). */
    fun investments(all: List<Transaction>, recurringDebits: List<Recurring>): List<InvestmentLine> {
        val rows = all.filter { it.category == Categories.INVESTMENT && !it.isTransfer }
        val monthlyKeys = recurringDebits.filter { it.category == Categories.INVESTMENT }.map { Categorizer.merchantKey(it.name) }.toSet()
        return rows.groupBy { Categorizer.merchantKey(it.counterparty) }.map { (key, ts) ->
            val last = ts.maxBy { it.timestamp }
            val platform = when {
                last.bank in com.financebrain.parser.BankSmsParser.investmentPlatforms -> last.bank
                last.counterparty.contains("groww", true) -> "Groww"
                last.counterparty.contains("zerodha", true) || last.counterparty.contains("coin", true) -> "Zerodha"
                last.counterparty.contains("bse", true) || last.counterparty.contains("nse", true) || last.counterparty.contains("clearing", true) -> "Exchange mandate"
                else -> last.bank
            }
            InvestmentLine(
                last.counterparty.substringAfter(" · ", last.counterparty), platform,
                ts.filter { it.direction == Direction.DEBIT }.sumOf { it.amountPaise },
                ts.filter { it.direction == Direction.CREDIT }.sumOf { it.amountPaise },
                ts.size, last.timestamp, key in monthlyKeys,
            )
        }.sortedByDescending { it.investedPaise }
    }

    fun monthBalance(all: List<Transaction>, anchors: List<BalanceAnchor>, monthStartTs: Long, monthEndTs: Long, now: Long): MonthBalance =
        MonthBalance(balanceAt(all, anchors, monthStartTs), balanceAt(all, anchors, minOf(monthEndTs, now + 1)), balanceAt(all, anchors, now + 1))
}
