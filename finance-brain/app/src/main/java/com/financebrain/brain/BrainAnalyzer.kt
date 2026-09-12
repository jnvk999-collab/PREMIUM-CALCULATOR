package com.financebrain.brain

import com.financebrain.data.Categories
import com.financebrain.data.Insights
import com.financebrain.data.MonthSummary
import com.financebrain.data.Recurring
import com.financebrain.data.Transaction
import com.financebrain.ui.compactRupees
import com.financebrain.ui.daysInMonth
import com.financebrain.ui.formatDay
import com.financebrain.ui.formatMonthShort
import com.financebrain.ui.formatRupees
import com.financebrain.ui.monthStart
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Tone { GOOD, WARN, BAD, NEUTRAL }

data class BrainSection(val title: String, val lines: List<String>, val tone: Tone = Tone.NEUTRAL)

data class BrainReport(
    val score: Int,
    val scoreLabel: String,
    val headline: String,
    val sections: List<BrainSection>,
    val actions: List<String>,
)

/**
 * Rule-based financial analysis that runs entirely on the phone. It answers: what happened,
 * where the money went, what changed, what is unusual, and what to do about it.
 */
object BrainAnalyzer {

    private val discretionary = setOf(Categories.FOOD, Categories.SHOPPING, Categories.ENTERTAINMENT, Categories.TRAVEL, Categories.OTHER)

    fun analyze(all: List<Transaction>, months: List<MonthSummary>, month: Long, recurringAll: List<Recurring>, now: Long): BrainReport {
        val recurring = recurringAll.filter { it.direction == com.financebrain.data.Direction.DEBIT }
        val end = Calendar.getInstance().apply { timeInMillis = month; add(Calendar.MONTH, 1) }.timeInMillis
        val inMonth = all.filter { it.timestamp in month until end }
        val isCurrent = month == monthStart(now)
        val dayOfMonth = if (isCurrent) Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_MONTH) else daysInMonth(month)
        val daysTotal = daysInMonth(month)

        val income = inMonth.filter(Insights::isIncome).sumOf { it.amountPaise }
        val spent = inMonth.filter(Insights::isSpend).sumOf { it.amountPaise }
        val invested = inMonth.filter(Insights::isInvestment).sumOf { it.amountPaise }
        val saved = income - spent - invested

        val prior = months.filter { it.monthStart < month }.takeLast(3)
        val avgSpend = prior.map { it.expensePaise }.filter { it > 0 }.average().takeIf { !it.isNaN() }?.toLong() ?: 0L
        val avgIncome = prior.map { it.incomePaise }.filter { it > 0 }.average().takeIf { !it.isNaN() }?.toLong() ?: 0L
        val avgInvest = prior.map { it.investedPaise }.average().takeIf { !it.isNaN() }?.toLong() ?: 0L
        val projectedSpend = if (isCurrent && dayOfMonth > 0) spent * daysTotal / dayOfMonth else spent
        val incomeBase = if (income > 0) income else avgIncome

        val sections = ArrayList<BrainSection>()
        val actions = ArrayList<String>()

        // ---- Cash flow
        run {
            val lines = ArrayList<String>()
            lines += "Income ${formatRupees(income)} · Spent ${formatRupees(spent)} · Invested ${formatRupees(invested)}"
            lines += if (saved >= 0) "You kept ${formatRupees(saved)} after spending and investing." else "You spent ${formatRupees(-saved)} more than you earned this month."
            if (isCurrent && dayOfMonth < daysTotal) {
                lines += "Day $dayOfMonth of $daysTotal. At this pace the month ends near ${compactRupees(projectedSpend)} spent" +
                    (if (avgSpend > 0) ", against a ${compactRupees(avgSpend)} average for the last ${prior.size} months." else ".")
            } else if (avgSpend > 0) {
                val d = pct(spent - avgSpend, avgSpend)
                lines += "Spending was ${abs(d)}% ${if (d >= 0) "higher" else "lower"} than your ${prior.size}-month average of ${compactRupees(avgSpend)}."
            }
            val tone = when {
                income == 0L && spent > 0 -> Tone.WARN
                saved < 0 -> Tone.BAD
                incomeBase > 0 && saved * 100 / incomeBase >= 20 -> Tone.GOOD
                else -> Tone.NEUTRAL
            }
            sections += BrainSection("Cash flow", lines, tone)
        }

        // ---- Where the money went
        val cats = Insights.categoryTotals(inMonth)
        if (cats.isNotEmpty()) {
            val lines = ArrayList<String>()
            val priorCats = prior.flatMap { m ->
                val e = Calendar.getInstance().apply { timeInMillis = m.monthStart; add(Calendar.MONTH, 1) }.timeInMillis
                Insights.categoryTotals(all.filter { it.timestamp in m.monthStart until e })
            }.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.paise } / prior.size.coerceAtLeast(1) }
            var worst: Pair<String, Long>? = null
            cats.take(4).forEach { c ->
                val base = priorCats[c.category] ?: 0L
                val note = if (base > 0) {
                    val d = pct(c.paise - base, base)
                    if (abs(d) >= 15) " (${if (d > 0) "+" else ""}$d% vs usual)" else " (about usual)"
                } else if (prior.isNotEmpty()) " (new this month)" else ""
                lines += "${c.category}: ${formatRupees(c.paise)}, ${(c.share * 100).toInt()}% of spending$note"
                if (base > 0 && c.paise - base > (worst?.second ?: 0L) && c.category in discretionary) worst = c.category to (c.paise - base)
            }
            val w = worst
            val tone = if (w != null && w.second > (avgSpend / 10).coerceAtLeast(100_000)) Tone.WARN else Tone.NEUTRAL
            sections += BrainSection("Where the money went", lines, tone)
            if (w != null && w.second >= 100_000) {
                actions += "${w.first} is running ${formatRupees(w.second)} above your usual. Bringing it back to normal saves that much every month."
            }
        }

        // ---- Fixed commitments
        if (recurring.isNotEmpty()) {
            val monthly = recurring.sumOf { it.amountPaise }
            val share = if (incomeBase > 0) (monthly * 100 / incomeBase).toInt() else null
            val lines = ArrayList<String>()
            lines += "${recurring.size} recurring payments add up to about ${formatRupees(monthly)} a month" + (share?.let { ", $it% of income." } ?: ".")
            recurring.take(4).forEach { lines += "${it.name}: ${formatRupees(it.amountPaise)}, next around ${formatDay(it.nextDue)}" }
            val subs = recurring.filter { it.category == Categories.ENTERTAINMENT || it.category == Categories.BILLS }
            if (subs.size >= 3) actions += "You have ${subs.size} subscriptions and recharges on repeat (${formatRupees(subs.sumOf { it.amountPaise })}/month). Cancel any you did not use last month."
            sections += BrainSection("Fixed commitments", lines, if ((share ?: 0) > 50) Tone.WARN else Tone.NEUTRAL)
        }

        // ---- Unusual
        run {
            val debits = inMonth.filter(Insights::isSpend).map { it.amountPaise }.sorted()
            if (debits.size >= 5) {
                val median = debits[debits.size / 2]
                val big = inMonth.filter { Insights.isSpend(it) && it.amountPaise >= (median * 8).coerceAtLeast(500_000) }
                    .sortedByDescending { it.amountPaise }.take(3)
                if (big.isNotEmpty()) {
                    val lines = big.map { "${formatRupees(it.amountPaise)} to ${it.counterparty} on ${formatDay(it.timestamp)} (${it.category})" }
                    val bigSum = big.sumOf { it.amountPaise }
                    sections += BrainSection("Unusual payments", lines + "These ${big.size} alone are ${(bigSum * 100 / spent.coerceAtLeast(1))}% of this month's spending.", Tone.WARN)
                    if (big.any { it.category == Categories.OTHER }) actions += "One large payment is filed under Other. Open it and set the right category (or mark it as a transfer) so the analysis stays accurate."
                }
            }
        }

        // ---- Investments
        run {
            val lines = ArrayList<String>()
            val last6 = months.filter { it.monthStart <= month }.takeLast(6)
            val total6 = last6.sumOf { it.investedPaise }
            val rate = if (incomeBase > 0) (invested * 100 / incomeBase).toInt() else null
            if (invested > 0 || total6 > 0) {
                lines += "Invested ${formatRupees(invested)} this month" + (rate?.let { " ($it% of income)" } ?: "") + "."
                lines += "${formatRupees(total6)} invested over the last ${last6.size} months."
                if (avgInvest > 0 && invested < avgInvest * 8 / 10 && !isCurrent) lines += "That is below your usual ${compactRupees(avgInvest)}."
            } else {
                lines += "No investments detected yet. SIPs, mutual funds, Zerodha, Groww, FD and RD are recognised automatically once an alert mentions them."
            }
            val tone = when {
                rate == null -> Tone.NEUTRAL
                rate >= 20 -> Tone.GOOD
                rate >= 10 -> Tone.NEUTRAL
                else -> Tone.WARN
            }
            if (rate != null && rate < 15 && saved > 0) actions += "You have ${formatRupees(saved)} left over this month. Moving ${formatRupees(saved / 2)} of it into a SIP would lift your investment rate to about ${rate + (saved / 2 * 100 / incomeBase).toInt()}%."
            sections += BrainSection("Investments", lines, tone)
        }

        // ---- Balance trend
        run {
            val withBal = months.filter { it.endBalancePaise != null }.takeLast(4)
            if (withBal.size >= 2) {
                val first = withBal.first().endBalancePaise!!; val last = withBal.last().endBalancePaise!!
                val lines = withBal.map { "${formatMonthShort(it.monthStart)}: ${compactRupees(it.endBalancePaise!!)}" }.toMutableList()
                val d = last - first
                lines += if (d >= 0) "Balances grew by ${formatRupees(d)} over this period." else "Balances fell by ${formatRupees(-d)} over this period."
                sections += BrainSection("Balance trend", lines, if (d >= 0) Tone.GOOD else Tone.WARN)
            }
        }

        // ---- Savings opportunities
        run {
            val lines = ArrayList<String>()
            val food = cats.firstOrNull { it.category == Categories.FOOD }
            if (food != null && incomeBase > 0 && food.paise * 100 / incomeBase > 12) {
                lines += "Food & Dining is ${(food.paise * 100 / incomeBase)}% of income. Cutting deliveries by a third saves about ${formatRupees(food.paise / 3)} a month."
            }
            val shop = cats.firstOrNull { it.category == Categories.SHOPPING }
            if (shop != null && shop.count >= 8) lines += "${shop.count} shopping payments this month. A 48-hour rule before non-essential buys usually trims 20% (about ${formatRupees(shop.paise / 5)})."
            val merchants = Insights.topMerchants(inMonth, 1)
            if (merchants.isNotEmpty() && spent > 0 && merchants[0].second * 100 / spent >= 25 && merchants[0].second < 500_000) {
                lines += "${merchants[0].first} alone took ${(merchants[0].second * 100 / spent)}% of your spending."
            }
            val cash = cats.firstOrNull { it.category == Categories.CASH }
            if (cash != null && spent > 0 && cash.paise * 100 / spent >= 20) lines += "${(cash.paise * 100 / spent)}% of spending is ATM cash, which the app cannot see. Paying by UPI keeps the picture complete."
            if (lines.isNotEmpty()) sections += BrainSection("Where you can save", lines, Tone.NEUTRAL)
        }

        // ---- Score
        val savingsRate = if (incomeBase > 0) ((income - spent) * 100 / incomeBase).toInt() else 0
        val investRate = if (incomeBase > 0) (invested * 100 / incomeBase).toInt() else 0
        var score = 50
        score += savingsRate.coerceIn(-30, 40)
        score += (investRate / 2).coerceIn(0, 15)
        if (avgSpend > 0 && spent > avgSpend * 12 / 10 && !isCurrent) score -= 10
        if (recurring.isNotEmpty() && incomeBase > 0 && recurring.sumOf { it.amountPaise } * 100 / incomeBase > 50) score -= 10
        if (income == 0L) score = 50
        score = score.coerceIn(0, 100)
        val label = when {
            income == 0L -> "No income seen yet"
            score >= 80 -> "Strong"
            score >= 60 -> "Healthy"
            score >= 40 -> "Needs attention"
            else -> "Under strain"
        }
        val headline = when {
            income == 0L && spent > 0 -> "No income has landed this month yet, but ${compactRupees(spent)} has gone out."
            saved < 0 -> "You are spending more than you earn this month. The sections below show exactly where."
            savingsRate >= 30 -> "You kept $savingsRate% of your income. That is excellent; make sure it is working for you."
            savingsRate >= 15 -> "You kept $savingsRate% of your income. Solid, with room to trim."
            else -> "Only $savingsRate% of income was kept this month. Small cuts in the top category would change that fast."
        }
        if (actions.isEmpty()) actions += if (saved > 0) "Keep the current pattern. Review the recurring list once a quarter." else "Set a monthly limit for your top category and check this screen weekly."
        return BrainReport(score, label, headline, sections, actions.take(4))
    }

    /** Balance-sheet view: assets, liabilities, loan cost against returns. */
    fun wealth(nw: com.financebrain.data.NetWorth, loans: List<com.financebrain.data.LoanStatus>, holdings: List<com.financebrain.data.Holding>, monthlyIncome: Long): Pair<List<BrainSection>, List<String>> {
        val sections = ArrayList<BrainSection>(); val actions = ArrayList<String>()
        val lines = ArrayList<String>()
        lines += "Assets ${compactRupees(nw.assets)} (bank ${compactRupees(nw.bankPaise)}, investments ${compactRupees(nw.holdingsPaise)}) − liabilities ${compactRupees(nw.liabilities)} = net worth ${formatRupees(nw.net)}."
        if (monthlyIncome > 0) lines += "That is ${"%.1f".format(nw.net / monthlyIncome.toDouble())} months of income."
        if (nw.liabilities > 0) lines += "Debt is ${(nw.liabilities * 100 / nw.assets.coerceAtLeast(1))}% of assets."
        sections += BrainSection("Net worth", lines, if (nw.net > 0) Tone.GOOD else Tone.BAD)

        if (loans.isNotEmpty()) {
            val l = ArrayList<String>()
            loans.forEach { st ->
                l += "${st.loan.lender}: ${compactRupees(st.outstandingNowPaise)} left at ${"%.1f".format(st.loan.annualRatePct)}%, ${st.monthsLeft} months to go, ${compactRupees(st.totalInterestLeftPaise)} more interest."
            }
            val emi = loans.sumOf { it.loan.emiPaise }
            if (monthlyIncome > 0) {
                val share = (emi * 100 / monthlyIncome).toInt()
                l += "EMIs are $share% of income." + if (share > 40) " Above 40% leaves little room for shocks." else ""
            }
            val gain = nw.holdingsPaise - nw.holdingsInvestedPaise
            val retPct = if (nw.holdingsInvestedPaise > 0) gain * 100.0 / nw.holdingsInvestedPaise else 0.0
            val hi = loans.maxBy { it.loan.annualRatePct }
            if (holdings.isNotEmpty()) {
                l += "Your investments show ${"%.1f".format(retPct)}% total gain so far, while the loan costs ${"%.1f".format(hi.loan.annualRatePct)}% a year, guaranteed."
                if (hi.extra5kSavesPaise > 0) actions += "Prepaying ${hi.loan.lender} is a guaranteed ${"%.1f".format(hi.loan.annualRatePct)}% return. ₹5,000 extra a month saves ${compactRupees(hi.extra5kSavesPaise)} and ${hi.extra5kMonthsSaved} months. Consider directing part of any bonus or the deposits there."
            }
            sections += BrainSection("Loans", l, if (monthlyIncome > 0 && emi * 100 / monthlyIncome > 40) Tone.WARN else Tone.NEUTRAL)
        }

        if (holdings.isNotEmpty()) {
            val byType = holdings.groupBy { it.type }.mapValues { it.value.sumOf { h -> h.currentPaise } }
            val total = nw.holdingsPaise.coerceAtLeast(1)
            val l = byType.entries.sortedByDescending { it.value }.map { (t, v) -> "${t.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }}: ${compactRupees(v)} (${(v * 100 / total)}%)" }.toMutableList()
            val eq = (byType["MUTUAL_FUND"] ?: 0L) + (byType["STOCK"] ?: 0L) + (byType["UNLISTED"] ?: 0L)
            val safe = (byType["DEPOSIT"] ?: 0L) + (byType["GOLD"] ?: 0L)
            if (eq > 0 || safe > 0) l += "Equity ${(eq * 100 / total)}% · deposits and gold ${(safe * 100 / total)}%."
            val stale = holdings.filter { System.currentTimeMillis() - it.updatedAt > 45L * 86_400_000L }
            if (stale.isNotEmpty()) actions += "${stale.size} holding${if (stale.size > 1) "s have" else " has"} not been updated in over 6 weeks. Refresh the values so net worth stays honest."
            val unl = byType["UNLISTED"] ?: 0L
            if (unl * 100 / total > 15) l += "Unlisted shares are ${(unl * 100 / total)}% of investments; they are hard to sell quickly, so keep an emergency buffer elsewhere."
            sections += BrainSection("Investment mix", l, Tone.NEUTRAL)
        }
        return sections to actions
    }

    private fun pct(delta: Long, base: Long): Int = if (base == 0L) 0 else (delta * 100.0 / base).roundToInt()
}
