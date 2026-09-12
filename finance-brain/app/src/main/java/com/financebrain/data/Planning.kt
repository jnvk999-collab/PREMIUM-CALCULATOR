package com.financebrain.data

import com.financebrain.ui.dayOfMonth
import com.financebrain.ui.daysInMonth
import com.financebrain.ui.monthEnd
import com.financebrain.ui.monthStart
import java.util.Calendar

data class CardStatus(
    val card: CreditCard,
    val cycleStart: Long, val cycleEnd: Long,       // current statement cycle [start, end)
    val currentSpendPaise: Long,                    // unbilled, this cycle
    val lastStatementPaise: Long,                   // previous cycle's spend = bill due
    val paidSincePaise: Long,                       // card bill payments seen since last statement
    val nextBillingAt: Long, val dueAt: Long,
) {
    val outstandingPaise get() = (lastStatementPaise - paidSincePaise).coerceAtLeast(0)
    val utilisation: Float? get() = card.limitPaise?.takeIf { it > 0 }?.let { (currentSpendPaise + outstandingPaise).toFloat() / it }
    val availablePaise: Long? get() = card.limitPaise?.let { (it - currentSpendPaise - outstandingPaise).coerceAtLeast(0) }
}

data class Allocation(
    val incomePaise: Long, val incomeIsEstimate: Boolean,
    val emiPaise: Long, val cardDuePaise: Long, val investTargetPaise: Long, val fixedBillsPaise: Long,
    val spentSoFarPaise: Long, val daysLeft: Int,
) {
    val freeToSpendPaise get() = incomePaise - emiPaise - cardDuePaise - investTargetPaise - fixedBillsPaise
    val remainingPaise get() = freeToSpendPaise - spentSoFarPaise
    val perDayPaise get() = if (daysLeft > 0) remainingPaise / daysLeft else remainingPaise
}

data class CalendarEvent(val day: Int, val type: String, val label: String, val amountPaise: Long)  // type: income, emi, card, sip, bill

data class DisciplineStatus(
    val controlled: List<Triple<ControlledCategory, Long, Float>>,   // rule, spent this cycle, share of limit
    val breaches: List<Pair<ZeroTolerance, List<Transaction>>>,      // zero-tolerance rules broken this cycle
    val cleanDays: Int,                                              // days since last breach (any zero rule)
    val wastedPaise: Long,                                           // spend in zero-tolerance categories this cycle
    val fineJarPaise: Long, val regretPaise: Long,
)

object Planning {

    fun dayToTs(base: Long, day: Int): Long = Calendar.getInstance().apply {
        timeInMillis = base; set(Calendar.DAY_OF_MONTH, minOf(day, getActualMaximum(Calendar.DAY_OF_MONTH)))
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun cardStatus(card: CreditCard, all: List<Transaction>, now: Long): CardStatus {
        val bd = card.billingDay.coerceIn(1, 28)
        var start = dayToTs(now, bd)
        if (start > now) start = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.MONTH, -1) }.timeInMillis
        val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.MONTH, 1) }.timeInMillis
        val prevStart = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.MONTH, -1) }.timeInMillis
        val spends = all.filter { it.accountKind == "CARD" && it.bank == card.bank && it.accountTail == card.tail && it.direction == Direction.DEBIT }
        val refunds = all.filter { it.accountKind == "CARD" && it.bank == card.bank && it.accountTail == card.tail && it.direction == Direction.CREDIT }
        fun net(from: Long, to: Long) = spends.filter { it.timestamp in from until to }.sumOf { it.amountPaise } - refunds.filter { it.timestamp in from until to }.sumOf { it.amountPaise }
        val current = net(start, end).coerceAtLeast(0)
        val last = net(prevStart, start).coerceAtLeast(0)
        // Bill payments: debits categorised as card bill, from any bank, mentioning this card's tail or bank.
        val paid = all.filter {
            it.category == Categories.CARD_BILL && it.timestamp >= start &&
                (it.counterparty.contains(card.tail) || it.counterparty.contains(card.bank, true) || (it.rawText?.contains(card.tail) == true))
        }.sumOf { it.amountPaise }
        val due = start + card.dueDaysAfter * 86_400_000L
        return CardStatus(card, start, end, current, last, paid, end, due)
    }

    fun allocation(
        all: List<Transaction>, cycleStart: Long, now: Long, salary: SalaryInfo?, loans: List<LoanInfo>,
        cards: List<CardStatus>, recurringDebits: List<Recurring>, investPct: Int,
    ): Allocation? {
        val end = monthEnd(cycleStart)
        val inCycle = all.filter { it.timestamp in cycleStart until end }
        val actualIncome = inCycle.filter(Insights::isIncome).sumOf { it.amountPaise }
        val income = if (actualIncome > 0) actualIncome else salary?.amountPaise ?: return null
        val emi = loans.sumOf { it.emiPaise }
        val cardDue = cards.sumOf { it.outstandingPaise }
        val loanKeys = loans.map { com.financebrain.parser.Categorizer.merchantKey(it.lender) }.toSet()
        val bills = recurringDebits.filter {
            it.category in setOf(Categories.BILLS, Categories.UTILITIES, Categories.RENT, Categories.ENTERTAINMENT, Categories.EDUCATION, Categories.HEALTH) &&
                com.financebrain.parser.Categorizer.merchantKey(it.name) !in loanKeys
        }.sumOf { it.amountPaise }
        val spent = inCycle.filter(Insights::isSpend).sumOf { it.amountPaise }
        val daysLeft = if (now in cycleStart until end) daysInMonth(cycleStart) - dayOfMonth(now) + 1 else 0
        return Allocation(income, actualIncome == 0L, emi, cardDue, income * investPct / 100, bills, spent, daysLeft)
    }

    fun calendar(monthTs: Long, salaryDay: Int, salary: SalaryInfo?, loans: List<LoanInfo>, cards: List<CardStatus>, recurring: List<Recurring>): List<CalendarEvent> {
        val out = ArrayList<CalendarEvent>()
        if (salary != null) out += CalendarEvent(salaryDay, "income", salary.employer, salary.amountPaise)
        loans.forEach { out += CalendarEvent(Calendar.getInstance().apply { timeInMillis = it.nextDue }.get(Calendar.DAY_OF_MONTH), "emi", it.lender, it.emiPaise) }
        cards.forEach { out += CalendarEvent(it.card.billingDay, "card", "${it.card.name} bill", it.currentSpendPaise) }
        val loanKeys = loans.map { com.financebrain.parser.Categorizer.merchantKey(it.lender) }.toSet()
        recurring.filter { it.direction == Direction.DEBIT && com.financebrain.parser.Categorizer.merchantKey(it.name) !in loanKeys }.forEach {
            val day = Calendar.getInstance().apply { timeInMillis = it.nextDue }.get(Calendar.DAY_OF_MONTH)
            out += CalendarEvent(day, if (it.category == Categories.INVESTMENT) "sip" else "bill", it.name, it.amountPaise)
        }
        recurring.filter { it.direction == Direction.CREDIT && (salary == null || com.financebrain.parser.Categorizer.merchantKey(it.name) != com.financebrain.parser.Categorizer.merchantKey(salary.employer)) }.forEach {
            out += CalendarEvent(Calendar.getInstance().apply { timeInMillis = it.nextDue }.get(Calendar.DAY_OF_MONTH), "income", it.name, it.amountPaise)
        }
        return out.sortedBy { it.day }
    }

    fun discipline(all: List<Transaction>, cycleStart: Long, controlled: List<ControlledCategory>, zero: List<ZeroTolerance>, entries: List<DisciplineEntry>, now: Long): DisciplineStatus {
        val end = monthEnd(cycleStart)
        val inCycle = all.filter { it.timestamp in cycleStart until end && Insights.isSpend(it) }
        val c = controlled.map { rule ->
            val spent = inCycle.filter { it.category == rule.category }.sumOf { it.amountPaise }
            Triple(rule, spent, if (rule.monthlyLimitPaise > 0) spent.toFloat() / rule.monthlyLimitPaise else 0f)
        }
        val breaches = zero.map { z -> z to inCycle.filter { it.category == z.category && it.timestamp >= z.since } }.filter { it.second.isNotEmpty() }
        val zeroCats = zero.map { it.category }.toSet()
        val lastBreach = all.filter { Insights.isSpend(it) && it.category in zeroCats && zero.any { z -> z.category == it.category && it.timestamp >= z.since } }.maxOfOrNull { it.timestamp }
        val since = lastBreach ?: zero.minOfOrNull { it.since } ?: now
        val cleanDays = ((now - since) / 86_400_000L).toInt().coerceAtLeast(0)
        val wasted = breaches.sumOf { it.second.sumOf { t -> t.amountPaise } }
        return DisciplineStatus(c, breaches, cleanDays, wasted,
            entries.filter { it.kind == "FINE" }.sumOf { it.amountPaise }, entries.filter { it.kind == "REGRET" }.sumOf { it.amountPaise })
    }
}
