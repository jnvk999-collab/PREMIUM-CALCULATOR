package com.financebrain.data

import com.financebrain.parser.Categorizer
import com.financebrain.ui.monthStart
import java.util.Calendar

data class CategoryTotal(val category: String, val paise: Long, val share: Float, val count: Int)

data class Recurring(val name: String, val amountPaise: Long, val nextDue: Long, val category: String, val occurrences: Int)

data class MonthSummary(val monthStart: Long, val incomePaise: Long, val expensePaise: Long)

object Insights {

    /** Spending only: debits that are not transfers between own accounts. */
    fun isSpend(t: Transaction) = t.direction == Direction.DEBIT && !t.isTransfer
    fun isIncome(t: Transaction) = t.direction == Direction.CREDIT && !t.isTransfer

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
            out += MonthSummary(start, inMonth.filter(::isIncome).sumOf { it.amountPaise }, inMonth.filter(::isSpend).sumOf { it.amountPaise })
        }
        return out
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
