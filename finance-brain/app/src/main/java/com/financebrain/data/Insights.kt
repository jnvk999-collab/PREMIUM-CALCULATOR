package com.financebrain.data

import com.financebrain.parser.Categorizer
import com.financebrain.ui.monthStart
import java.util.Calendar

data class CategoryTotal(val category: String, val paise: Long, val share: Float, val count: Int)

data class Recurring(val name: String, val amountPaise: Long, val nextDue: Long, val category: String, val occurrences: Int)

data class MonthSummary(val monthStart: Long, val incomePaise: Long, val expensePaise: Long, val investedPaise: Long = 0, val endBalancePaise: Long? = null) {
    val savedPaise get() = incomePaise - expensePaise - investedPaise
}

object Insights {

    /** Spending only: debits that are neither transfers between own accounts nor investments. */
    fun isSpend(t: Transaction) = t.direction == Direction.DEBIT && !t.isTransfer && t.category != Categories.INVESTMENT
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
            val d = Calendar.getInstance().apply { timeInMillis = t.timestamp }.get(Calendar.DAY_OF_MONTH) - 1
            if (d in 0 until days) arr[d] += t.amountPaise
        }
        return arr
    }

    fun monthSeries(all: List<Transaction>, months: Int, now: Long): List<MonthSummary> {
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
                endBalance(all, end),
            )
        }
        return out
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
    fun recurring(all: List<Transaction>, now: Long): List<Recurring> {
        val debits = all.filter { it.direction == Direction.DEBIT }
        val groups = debits.groupBy { Categorizer.merchantKey(it.counterparty) to (it.amountPaise / 5000) }
        val out = ArrayList<Recurring>()
        for ((_, ts) in groups) {
            if (ts.size < 2) continue
            val byMonth = ts.groupBy { monthStart(it.timestamp) }
            if (byMonth.size < 2) continue
            val days = ts.map { Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.DAY_OF_MONTH) }
            val spread = (days.max() - days.min())
            if (spread > 6 && spread < 24) continue
            val last = ts.maxBy { it.timestamp }
            val next = Calendar.getInstance().apply { timeInMillis = last.timestamp; add(Calendar.MONTH, 1) }.timeInMillis
            if (next < now - 5 * 86_400_000L) continue
            out += Recurring(last.counterparty, ts.map { it.amountPaise }.average().toLong(), next, last.category, ts.size)
        }
        return out.sortedBy { it.nextDue }
    }
}
