package com.financebrain.data

import com.financebrain.parser.Categorizer
import com.financebrain.ui.formatRupees

/** Something the app is unsure about. Answering it fixes the numbers. */
sealed class ReviewItem(val id: String, val title: String, val detail: String) {
    class BigUnknown(val t: Transaction) : ReviewItem("tx-${t.id}", "What was this ${formatRupees(t.amountPaise)}?", "${t.counterparty} · ${t.bank} · filed under ${t.category}")
    class ConfirmSalary(val t: Transaction) : ReviewItem("sal-${t.id}", "Is this your salary?", "${formatRupees(t.amountPaise)} from ${t.counterparty} into ${t.bank}, seen monthly")
    class NoBalance(val bank: String, val tail: String) : ReviewItem("bal-$bank|$tail", "Balance for $bank ··$tail", "No alert has reported it yet. Enter today's balance once.")
    class NoLimit(val card: CreditCard) : ReviewItem("lim-${card.key}", "Limit for ${card.name}", "Needed to show how much of the card is used.")
    class NoSalaryDay : ReviewItem("salary-day", "When is your salary day?", "The app counts the month from that day.")
}

object Review {
    fun build(all: List<Transaction>, cycleStart: Long, accounts: List<com.financebrain.ui.AccountView>, cards: List<CreditCard>, salary: SalaryInfo?, salaryDaySet: Boolean, dismissed: Set<String>): List<ReviewItem> {
        val out = ArrayList<ReviewItem>()
        if (!salaryDaySet) out += ReviewItem.NoSalaryDay()
        // Large debits with a vague label, newest first, this cycle and last.
        val debits = all.filter { Insights.isSpend(it) }.map { it.amountPaise }.sorted()
        val median = if (debits.isNotEmpty()) debits[debits.size / 2] else 0L
        val threshold = maxOf(median * 8, 25_000_00L)
        all.filter { !it.userEdited && it.direction == Direction.DEBIT && it.amountPaise >= threshold && (it.category == Categories.OTHER || it.category == Categories.TRANSFER && !it.isTransfer) }
            .sortedByDescending { it.timestamp }.take(5).forEach { out += ReviewItem.BigUnknown(it) }
        // Salary confirmation
        if (salary != null && !salary.confirmed) {
            all.firstOrNull { it.direction == Direction.CREDIT && Categorizer.merchantKey(it.counterparty) == Categorizer.merchantKey(salary.employer) }?.let { out += ReviewItem.ConfirmSalary(it) }
        }
        accounts.filter { !it.isCard && it.balancePaise == null }.forEach { out += ReviewItem.NoBalance(it.bank, it.tail) }
        cards.filter { it.limitPaise == null }.forEach { out += ReviewItem.NoLimit(it) }
        return out.filter { it.id !in dismissed }
    }
}
