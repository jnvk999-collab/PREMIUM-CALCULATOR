package com.financebrain.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financebrain.data.Transaction
import com.financebrain.ui.HomeState
import com.financebrain.ui.MainViewModel
import com.financebrain.ui.PlanState
import com.financebrain.ui.compactRupees
import com.financebrain.ui.components.BarChart
import com.financebrain.ui.components.CategoryDot
import com.financebrain.ui.components.EmptyHint
import com.financebrain.ui.components.SectionCard
import com.financebrain.ui.components.SectionTitle
import com.financebrain.ui.components.ShareBar
import com.financebrain.ui.components.categoryColor
import com.financebrain.ui.dayOfMonth
import com.financebrain.ui.formatCycle
import com.financebrain.ui.formatRupees
import com.financebrain.ui.monthStart
import com.financebrain.ui.theme.P

/** Segment pills used by Spend, Money and Plan. */
@Composable
fun Pills(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 14.dp)) {
        items(options) { (k, label) -> FilterChip(selected = selected == k, onClick = { onSelect(k) }, label = { Text(label) }) }
    }
}

@Composable
fun ScreenHeader(title: String, sub: String? = null) {
    val p = P
    Column(Modifier.padding(horizontal = 14.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = p.t1)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = p.t2)
    }
}

// ------------------------------------------------------------------ Spend
@Composable
fun SpendScreen(state: HomeState, padding: PaddingValues, section: String, onSection: (String) -> Unit, onOpen: (Transaction) -> Unit) {
    val p = P
    Column(Modifier.padding(top = padding.calculateTopPadding() + 4.dp)) {
        ScreenHeader("Spend", formatCycle(state.month))
        Spacer(Modifier.height(8.dp))
        Pills(listOf("activity" to "Activity", "categories" to "Categories", "recurring" to "Recurring"), section, onSection)
        Spacer(Modifier.height(4.dp))
        when (section) {
            "activity" -> TransactionsScreen(state.allTransactions, PaddingValues(bottom = padding.calculateBottomPadding()), onOpen, showHeader = false, trackingStart = state.trackingStart)
            "categories" -> LazyColumn(contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, padding.calculateBottomPadding() + 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    SectionCard {
                        SectionTitle("Where it went", "spent ${formatRupees(state.expensePaise)}")
                        if (state.categories.isEmpty()) EmptyHint("No spending recorded for this month yet.")
                        else {
                            ShareBar(state.categories.take(8).map { categoryColor(it.category) to it.share })
                            Spacer(Modifier.height(10.dp))
                            state.categories.forEach { c ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CategoryDot(c.category, 30); Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(c.category, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${c.count} payments · ${(c.share * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = p.t2)
                                    }
                                    Text(formatRupees(c.paise), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                item {
                    SectionCard {
                        val days = state.daily.size
                        val now = System.currentTimeMillis()
                        val isCurrent = state.month == monthStart(now)
                        SectionTitle("Day by day", "avg ${formatRupees(if (days > 0) state.expensePaise / (if (isCurrent) dayOfMonth(now) else days).coerceAtLeast(1) else 0)}/day")
                        BarChart(state.daily, if (isCurrent) dayOfMonth(now) - 1 else -1, p.gold, labels = listOf("1", "${days / 4}", "${days / 2}", "${3 * days / 4}", "$days"))
                    }
                }
                trends(state)
            }
            else -> LazyColumn(contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, padding.calculateBottomPadding() + 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { recurring(state) }
        }
    }
}

// ------------------------------------------------------------------ Money
@Composable
fun MoneyScreen(state: HomeState, plan: PlanState, vm: MainViewModel, padding: PaddingValues, section: String, onSection: (String) -> Unit, onSetBalance: (String?) -> Unit) {
    val p = P
    Column(Modifier.padding(top = padding.calculateTopPadding() + 4.dp)) {
        ScreenHeader("Money", plan.netWorth?.let { "Net worth ${formatRupees(it.net)} · have ${compactRupees(it.assets)} · owe ${compactRupees(it.liabilities)}" })
        Spacer(Modifier.height(8.dp))
        Pills(listOf("accounts" to "Accounts", "cards" to "Cards", "loans" to "Loans", "invest" to "Investments", "goals" to "Goals"), section, onSection)
        Spacer(Modifier.height(4.dp))
        LazyColumn(contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, padding.calculateBottomPadding() + 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (section) {
                "accounts" -> item {
                    SectionCard {
                        SectionTitle("Bank accounts", "Update balance") { onSetBalance(null) }
                        Text(formatRupees(state.totalBalancePaise), style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
                        val mb = state.monthBalance
                        if (mb.startPaise != null && mb.endPaise != null) {
                            val d = mb.endPaise - mb.startPaise
                            Text("${if (d >= 0) "Up" else "Down"} ${compactRupees(kotlin.math.abs(d))} this month, from ${compactRupees(mb.startPaise)}", style = MaterialTheme.typography.labelSmall, color = if (d >= 0) p.green else p.red)
                        }
                        Spacer(Modifier.height(8.dp))
                        val banks = state.accountList.filter { !it.isCard }
                        if (banks.isEmpty()) EmptyHint("Accounts appear as bank alerts come in.")
                        banks.forEach { acc ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${acc.bank} ··${acc.tail}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(acc.source, style = MaterialTheme.typography.labelSmall, color = p.t2)
                                    acc.build?.let { b ->
                                        if (b.creditsPaise > 0 || b.debitsPaise > 0) Text(
                                            "since then −${compactRupees(b.debitsPaise)} paid" + (if (b.creditsPaise > 0) ", +${compactRupees(b.creditsPaise)} received" else ""),
                                            style = MaterialTheme.typography.labelSmall, color = p.t3
                                        )
                                    }
                                }
                                Text(acc.balancePaise?.let { formatRupees(it) } ?: "—", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = { onSetBalance("${acc.bank}|${acc.tail}") }) { Text(if (acc.balancePaise == null) "Enter" else "Edit", color = p.gold) }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Each account starts from the newest figure you or the bank gave it, then every payment and credit since is applied. Tap Edit to correct one.", style = MaterialTheme.typography.labelSmall, color = p.t3)
                    }
                }
                "cards" -> cardsSection(plan, vm)
                "loans" -> { formalLoansSection(plan, vm); loansAndSalary(state) }
                "invest" -> { holdingsSection(plan, vm); investments(state) }
                else -> goalsSection(plan, vm)
            }
        }
    }
}

// ------------------------------------------------------------------ Plan
@Composable
fun PlanScreen(state: HomeState, plan: PlanState, vm: MainViewModel, padding: PaddingValues, section: String, onSection: (String) -> Unit, onOpenSettings: () -> Unit) {
    val p = P
    Column(Modifier.padding(top = padding.calculateTopPadding() + 4.dp)) {
        ScreenHeader("Plan", formatCycle(state.month))
        Spacer(Modifier.height(8.dp))
        Pills(listOf("budget" to "Budget", "calendar" to "Calendar", "discipline" to "Discipline"), section, onSection)
        Spacer(Modifier.height(4.dp))
        LazyColumn(contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, padding.calculateBottomPadding() + 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (section) {
                "budget" -> item {
                    val a = plan.allocation
                    SectionCard {
                        SectionTitle("This month's money plan", "Settings", onOpenSettings)
                        if (a == null) EmptyHint("Once a salary is seen (or set) the plan appears: income minus EMIs, bills and your investment target gives what is free to spend.")
                        else {
                            PlanLine("Income", a.incomePaise, p.green, if (a.incomeIsEstimate) "expected" else "received")
                            PlanLine("− EMIs", a.emiPaise, p.orange, null)
                            PlanLine("− Card bills due", a.cardDuePaise, p.blue, null)
                            PlanLine("− Fixed bills", a.fixedBillsPaise, p.purple, "recurring bills, rent, utilities")
                            PlanLine("− Invest target", a.investTargetPaise, p.gold, "${plan.investPct}% of income")
                            Spacer(Modifier.height(6.dp))
                            PlanLine("= Free to spend", a.freeToSpendPaise, p.t1, null, bold = true)
                            PlanLine("Spent so far", a.spentSoFarPaise, if (a.spentSoFarPaise > a.freeToSpendPaise) p.red else p.t1, null)
                            PlanLine("Left", a.remainingPaise, if (a.remainingPaise < 0) p.red else p.green, if (a.daysLeft > 0) "${formatRupees(a.perDayPaise.coerceAtLeast(0))} a day for ${a.daysLeft} days" else null, bold = true)
                        }
                    }
                }
                "calendar" -> calendarSection(state, plan)
                else -> disciplineSection(plan, vm, onOpenSettings)
            }
        }
    }
}

@Composable
private fun PlanLine(label: String, paise: Long, color: androidx.compose.ui.graphics.Color, sub: String?, bold: Boolean = false) {
    val p = P
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = p.t2)
        }
        Text(formatRupees(paise), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, color = color, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}
