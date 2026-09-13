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

data class DayTotal(val dayStart: Long, val paise: Long, val count: Int = 0)
data class CategoryDelta(val category: String, val paise: Long, val usualPaise: Long, val count: Int) {
    val deltaPaise get() = paise - usualPaise
}

/** Today, this week, and where the money is going faster than usual, with plain verdicts. */
data class DailyReport(
    val todayPaise: Long, val todayCount: Int, val todayByCategory: List<CategoryTotal>, val todayTop: Transaction?,
    val weekPaise: Long, val weekStart: Long, val daysLeftInWeek: Int,
    val last14: List<DayTotal>,
    val avgPerDayPaise: Long, val daysSoFar: Int,
    val weekdayAvgPaise: Long, val weekendAvgPaise: Long,
    val biggestDays: List<DayTotal>,
    val categoryDelta: List<CategoryDelta>,
    val lines: List<String>,
    val dailyLimitPaise: Long, val weeklyLimitPaise: Long,
) {
    val dailyLeftPaise get() = dailyLimitPaise - todayPaise
    val weeklyLeftPaise get() = weeklyLimitPaise - weekPaise
}

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
     * Everything that changes the money you have: bank payments, card swipes, cash you record by
     * hand, and every credit. A swipe is money gone the moment you pay, so paying the card bill
     * later is not counted again. A move between your own accounts has a debit and a credit that
     * cancel, so it needs no special case.
     */
    private fun movesYourMoney(t: Transaction) =
        t.category != Categories.CARD_BILL && t.bank !in com.financebrain.parser.BankSmsParser.investmentPlatforms

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
        /** Exactly the movements that were counted, so the figure can be shown as a sum you can check. */
        val rows: List<Transaction> = emptyList(),
    ) {
        val paise get() = basePaise + creditsPaise - debitsPaise
        val cardDebitsPaise get() = rows.filter { it.direction == Direction.DEBIT && it.accountKind == "CARD" }.sumOf { it.amountPaise }
        val bankDebitsPaise get() = debitsPaise - cardDebitsPaise
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
            .sortedByDescending { it.timestamp }
        return BalanceBuild(
            basePaise, baseAt, fromUser,
            rows.filter { it.direction == Direction.CREDIT }.sumOf { it.amountPaise },
            rows.filter { it.direction == Direction.DEBIT }.sumOf { it.amountPaise },
            rows,
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

    fun dailyReport(
        all: List<Transaction>, tracked: List<Transaction>, cycleStart: Long, now: Long,
        dailyLimit: Long, weeklyLimit: Long,
    ): DailyReport {
        val day = 86_400_000L
        val today0 = com.financebrain.ui.dayStart(now)
        fun between(list: List<Transaction>, from: Long, to: Long) = list.filter { isSpend(it) && it.timestamp >= from && it.timestamp < to }
        val todayRows = between(tracked, today0, today0 + day)
        val todayPaise = todayRows.sumOf { it.amountPaise }

        val cal = Calendar.getInstance().apply { timeInMillis = today0 }
        val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7           // Monday = 0 … Sunday = 6
        val weekStart = today0 - dow * day
        val weekPaise = between(tracked, weekStart, today0 + day).sumOf { it.amountPaise }

        val last14 = (13 downTo 0).map { i ->
            val s = today0 - i * day
            val rows = between(tracked, s, s + day)
            DayTotal(s, rows.sumOf { it.amountPaise }, rows.size)
        }

        val cycleRows = between(tracked, cycleStart, today0 + day)
        val daysSoFar = ((today0 - com.financebrain.ui.dayStart(cycleStart)) / day).toInt().coerceAtLeast(0) + 1
        val avgPerDay = cycleRows.sumOf { it.amountPaise } / daysSoFar

        // Weekday against weekend, over the last eight weeks of everything the app has seen.
        var wd = 0L; var wdDays = 0; var we = 0L; var weDays = 0
        for (i in 0 until 56) {
            val s = today0 - i * day
            val paise = between(all, s, s + day).sumOf { it.amountPaise }
            val d = Calendar.getInstance().apply { timeInMillis = s }.get(Calendar.DAY_OF_WEEK)
            if (d == Calendar.SATURDAY || d == Calendar.SUNDAY) { we += paise; weDays++ } else { wd += paise; wdDays++ }
        }
        val weekdayAvg = if (wdDays > 0) wd / wdDays else 0L
        val weekendAvg = if (weDays > 0) we / weDays else 0L

        val biggest = cycleRows.groupBy { com.financebrain.ui.dayStart(it.timestamp) }
            .map { (d, rows) -> DayTotal(d, rows.sumOf { it.amountPaise }, rows.size) }
            .sortedByDescending { it.paise }.take(3)

        // This cycle so far against the same number of days in the previous three cycles.
        val prev = (1..3).map { i -> val s = com.financebrain.ui.shiftMonth(cycleStart, -i); s to com.financebrain.ui.monthEnd(s) }
        val prevRows = prev.flatMap { (s, e) -> between(all, s, e) }
        val prevDays = prev.sumOf { (s, e) -> ((e - s) / day).toInt() }.coerceAtLeast(1)
        val usualPerDay = prevRows.groupBy { it.category }.mapValues { it.value.sumOf { t -> t.amountPaise } / prevDays }
        val deltas = categoryTotals(cycleRows).map { c ->
            CategoryDelta(c.category, c.paise, (usualPerDay[c.category] ?: 0L) * daysSoFar, c.count)
        }
        val hasHistory = prevRows.isNotEmpty()

        val f = { p: Long -> com.financebrain.ui.formatRupees(p) }
        val lines = ArrayList<String>()
        if (dailyLimit > 0) {
            lines += if (todayPaise > dailyLimit) "Over today's limit by ${f(todayPaise - dailyLimit)}. Nothing more today if you can help it."
            else "${f(dailyLimit - todayPaise)} of today's ${f(dailyLimit)} still available."
        }
        if (weeklyLimit > 0) {
            val left = 6 - dow
            lines += if (weekPaise > weeklyLimit) "Over this week's limit by ${f(weekPaise - weeklyLimit)} with $left day${if (left == 1) "" else "s"} still to go."
            else if (left > 0) "${f(weeklyLimit - weekPaise)} left for the week, about ${f((weeklyLimit - weekPaise) / left)} a day."
            else "${f(weeklyLimit - weekPaise)} of the week's limit unspent."
        }
        if (avgPerDay > 0 && todayPaise > 0) {
            val ratio = todayPaise.toDouble() / avgPerDay
            if (ratio >= 1.5) lines += "Today is ${"%.1f".format(ratio)}× a normal day for you this month (${f(avgPerDay)})."
            else if (ratio <= 0.5) lines += "Light day: well under your ${f(avgPerDay)} a day average."
        }
        val worst = deltas.filter { it.usualPaise > 0 }.maxByOrNull { it.deltaPaise }
        if (worst != null && worst.deltaPaise > 500_00 && worst.paise > worst.usualPaise * 1.25) {
            lines += "${worst.category} is running ${f(worst.deltaPaise)} above your usual for this point in the month. That is where the money is going."
        } else if (!hasHistory && deltas.isNotEmpty()) {
            lines += "${deltas.first().category} is the biggest bucket this month at ${f(deltas.first().paise)}. Comparisons against your usual start next month."
        }
        if (weekendAvg > 0 && weekdayAvg > 0 && weekendAvg > weekdayAvg * 1.5) {
            lines += "Weekends cost ${f(weekendAvg)} a day against ${f(weekdayAvg)} on weekdays. Plan weekends, not weekdays."
        }
        todayRows.maxByOrNull { it.amountPaise }?.let { if (todayRows.size > 1 && it.amountPaise * 2 > todayPaise) lines += "One payment, ${it.counterparty}, is most of today at ${f(it.amountPaise)}." }

        return DailyReport(
            todayPaise, todayRows.size, categoryTotals(todayRows), todayRows.maxByOrNull { it.amountPaise },
            weekPaise, weekStart, 6 - dow, last14, avgPerDay, daysSoFar, weekdayAvg, weekendAvg,
            biggest, deltas, lines, dailyLimit, weeklyLimit,
        )
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
