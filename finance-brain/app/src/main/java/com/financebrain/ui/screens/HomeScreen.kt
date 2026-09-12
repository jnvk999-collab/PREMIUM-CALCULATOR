package com.financebrain.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financebrain.data.Allocation
import com.financebrain.data.NetWorth
import com.financebrain.data.Transaction
import com.financebrain.sms.ScanProgress
import com.financebrain.ui.HomeState
import com.financebrain.ui.PlanState
import com.financebrain.ui.compactRupees
import com.financebrain.ui.components.BarChart
import com.financebrain.ui.components.BarRow
import com.financebrain.ui.components.CategoryDot
import com.financebrain.ui.components.EmptyHint
import com.financebrain.ui.components.SectionCard
import com.financebrain.ui.components.SectionTitle
import com.financebrain.ui.components.ShareBar
import com.financebrain.ui.components.TransactionRow
import com.financebrain.ui.components.UpdateCard
import com.financebrain.ui.components.categoryColor
import com.financebrain.ui.dayOfMonth
import com.financebrain.ui.daysInMonth
import com.financebrain.ui.formatCycle
import com.financebrain.ui.formatDay
import com.financebrain.ui.formatRupees
import com.financebrain.ui.monthStart
import com.financebrain.ui.theme.P
import com.financebrain.update.UpdateState
import java.util.Calendar

/**
 * Home, laid out like FinanceOS: safe-to-spend today, alerts, cycle cash flow with bar
 * rows, card breakdown, accounts strip, net worth, brain summary, categories, recent.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    plan: PlanState,
    scan: ScanProgress?,
    padding: PaddingValues,
    update: UpdateState,
    onUpdateDownload: () -> Unit, onUpdateInstall: () -> Unit, onUpdateDismiss: () -> Unit,
    onShiftMonth: (Int) -> Unit,
    onOpenTransaction: (Transaction) -> Unit,
    onOpen: (String) -> Unit,          // tab key
    onSetBalance: () -> Unit,
) {
    val p = P
    val now = System.currentTimeMillis()
    val isCurrent = state.month == monthStart(now)
    val a = plan.allocation
    val dayStart = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val spentToday = state.allTransactions.filter { it.timestamp >= dayStart && com.financebrain.data.Insights.isSpend(it) }.sumOf { it.amountPaise }
    val dailyBudget = when {
        plan.budgetPaise > 0 -> plan.budgetPaise / daysInMonth(state.month)
        a != null && a.daysLeft > 0 -> (a.freeToSpendPaise - (a.spentSoFarPaise - spentToday)).coerceAtLeast(0) / a.daysLeft
        else -> null
    }
    val safeToday = dailyBudget?.let { it - spentToday }
    val safeColor = when { safeToday == null -> p.t2; safeToday < 0 -> p.red; safeToday < (dailyBudget ?: 1) / 3 -> p.orange; else -> p.green }

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 6.dp, bottom = padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Cycle header
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("CYCLE", style = MaterialTheme.typography.labelSmall, color = p.t2)
                    Text(formatCycle(state.month), style = MaterialTheme.typography.titleMedium)
                }
                Row(Modifier.background(p.s2, RoundedCornerShape(50)).border(1.dp, p.bd, RoundedCornerShape(50)), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onShiftMonth(-1) }, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous", tint = p.t1) }
                    Text(if (isCurrent) "now" else "past", style = MaterialTheme.typography.labelMedium, color = p.t2)
                    IconButton(onClick = { onShiftMonth(1) }, enabled = !isCurrent, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next", tint = if (isCurrent) p.t3 else p.t1) }
                }
            }
        }

        item { UpdateCard(update, onUpdateDownload, onUpdateInstall, onUpdateDismiss) }
        if (scan != null && !scan.done) item {
            SectionCard(tint = p.blue) {
                Text("Reading SMS history · ${scan.scanned}/${scan.total} · ${scan.found} found", style = MaterialTheme.typography.labelMedium, color = p.blue)
                Spacer(Modifier.height(6.dp))
                if (scan.total > 0) LinearProgressIndicator(progress = { scan.scanned.toFloat() / scan.total }, modifier = Modifier.fillMaxWidth(), color = p.blue, trackColor = p.bd) else LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.blue, trackColor = p.bd)
            }
        }

        // Safe to spend today
        if (isCurrent) item {
            SectionCard(tint = safeColor) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SAFE TO SPEND TODAY", style = MaterialTheme.typography.labelSmall, color = p.t2)
                        Text(safeToday?.let { formatRupees(it) } ?: "—", style = MaterialTheme.typography.headlineMedium, color = safeColor, fontFamily = FontFamily.Monospace)
                        Text(dailyBudget?.let { "of ${formatRupees(it)} daily budget" } ?: "Set a salary day and budget in Settings", style = MaterialTheme.typography.labelSmall, color = p.t3)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Spent today", style = MaterialTheme.typography.labelSmall, color = p.t2)
                        Text(formatRupees(spentToday), style = MaterialTheme.typography.titleMedium, color = p.red, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Alerts
        item {
            val alerts = buildList {
                if (a != null && a.remainingPaise < 0) add(p.red to "Over the free-to-spend line by ${formatRupees(-a.remainingPaise)} this cycle.")
                plan.cards.filter { it.outstandingPaise > 0 && it.dueAt - now < 5 * 86_400_000L }.forEach { add(p.orange to "${it.card.name} bill ${formatRupees(it.outstandingPaise)} due ${formatDay(it.dueAt)}.") }
                plan.discipline?.breaches?.takeIf { it.isNotEmpty() }?.let { add(p.red to "${it.size} zero-tolerance rule${if (it.size > 1) "s" else ""} broken this cycle.") }
                plan.loanStatuses.filter { kotlin.math.abs(it.loan.dueDay - dayOfMonthCal(now)) <= 2 }.forEach { add(p.orange to "${it.loan.lender} EMI ${formatRupees(it.loan.emiPaise)} around day ${it.loan.dueDay}.") }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                alerts.take(3).forEach { (c, msg) ->
                    Text("⚠ $msg", style = MaterialTheme.typography.labelMedium, color = c, modifier = Modifier.fillMaxWidth().background(c.copy(alpha = 0.10f), RoundedCornerShape(10.dp)).border(1.dp, c.copy(alpha = 0.3f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }

        // Cycle cash flow
        item {
            val income = a?.incomePaise ?: state.incomePaise
            val net = income - state.expensePaise - state.investedPaise - (a?.emiPaise ?: 0) - plan.cards.sumOf { it.paidSincePaise }
            val sr = if (income > 0) ((income - state.expensePaise - (a?.emiPaise ?: 0)) * 100 / income).toInt() else 0
            val sc = when { sr >= 30 -> p.green; sr >= 15 -> p.gold; sr >= 0 -> p.orange; else -> p.red }
            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1A2040), Color(0xFF0F1830))), RoundedCornerShape(16.dp)).border(1.dp, p.bd2, RoundedCornerShape(16.dp)).padding(14.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("THIS CYCLE NET CASH FLOW", style = MaterialTheme.typography.labelSmall, color = p.t2)
                            Text(formatRupees(net), style = MaterialTheme.typography.displaySmall, color = if (net >= 0) p.green else p.red, fontFamily = FontFamily.Monospace)
                            Text((if (a?.incomeIsEstimate == true) "Expected income" else "Income") + " − spend − EMIs − card bills − invested", style = MaterialTheme.typography.labelSmall, color = p.t3)
                        }
                        Column(Modifier.background(sc.copy(alpha = 0.15f), RoundedCornerShape(10.dp)).border(1.dp, sc.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$sr%", style = MaterialTheme.typography.titleMedium, color = sc, fontFamily = FontFamily.Monospace)
                            Text("SAVED", style = MaterialTheme.typography.labelSmall, color = sc, fontSize = 9.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val base = income.coerceAtLeast(1)
                    BarRow("Income", if (a?.incomeIsEstimate == true) "expected" else "received", "+" + compactRupees(income), 1f, p.green)
                    BarRow("Spend", "UPI / cash / debit", "-" + compactRupees(state.expensePaise), state.expensePaise.toFloat() / base, p.red)
                    BarRow("EMIs", "loans", "-" + compactRupees(a?.emiPaise ?: 0), (a?.emiPaise ?: 0).toFloat() / base, p.orange)
                    BarRow("Card bills", "paid this cycle", "-" + compactRupees(plan.cards.sumOf { it.paidSincePaise }), plan.cards.sumOf { it.paidSincePaise }.toFloat() / base, p.blue)
                    BarRow("Invested", "SIPs, funds, FD", compactRupees(state.investedPaise), state.investedPaise.toFloat() / base, p.gold)
                    if (plan.cards.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.fillMaxWidth().background(p.blue.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).border(1.dp, p.blue.copy(alpha = 0.2f), RoundedCornerShape(10.dp)).padding(10.dp)) {
                            Text("💳 CREDIT CARD BREAKDOWN", style = MaterialTheme.typography.labelSmall, color = p.blue)
                            Spacer(Modifier.height(4.dp))
                            BarRow("CC spends", "this cycle, unbilled", compactRupees(plan.cards.sumOf { it.currentSpendPaise }), plan.cards.sumOf { it.currentSpendPaise }.toFloat() / base, p.blue)
                            BarRow("CC due", "outstanding bills", compactRupees(plan.cards.sumOf { it.outstandingPaise }), plan.cards.sumOf { it.outstandingPaise }.toFloat() / base, p.purple)
                        }
                    }
                    if (a != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Free to spend ${formatRupees(a.freeToSpendPaise)} · left ${formatRupees(a.remainingPaise)}" + if (a.daysLeft > 0) " · ${formatRupees(a.perDayPaise)}/day for ${a.daysLeft}d" else "", style = MaterialTheme.typography.labelSmall, color = p.t2, modifier = Modifier.weight(1f))
                            Text("Plan ›", style = MaterialTheme.typography.labelMedium, color = p.gold, modifier = Modifier.clickable { onOpen("cards") })
                        }
                    }
                }
            }
        }

        // Accounts strip
        item {
            SectionCard {
                SectionTitle("Accounts & cards", if (state.anchors.isEmpty()) "Set balance" else "Adjust", onSetBalance)
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("TOTAL BANK BALANCE", style = MaterialTheme.typography.labelSmall, color = p.t2)
                        Text(formatRupees(state.totalBalancePaise), style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
                        val mb = state.monthBalance
                        if (mb.startPaise != null && mb.endPaise != null) {
                            val d = mb.endPaise - mb.startPaise
                            Text("cycle ${if (d >= 0) "+" else "-"}${compactRupees(kotlin.math.abs(d))} since ${compactRupees(mb.startPaise)}", style = MaterialTheme.typography.labelSmall, color = if (d >= 0) p.green else p.red)
                        } else Text(if (state.hasTotalAnchor) "running from the balance you set" else "tap Set balance to start from today's figure", style = MaterialTheme.typography.labelSmall, color = p.t3)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (state.accountList.isEmpty()) EmptyHint("Accounts appear as bank alerts come in.")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.accountList) { acc ->
                        val cardSt = plan.cards.firstOrNull { it.card.bank == acc.bank && it.card.tail == acc.tail }
                        Column(Modifier.background(p.s3, RoundedCornerShape(12.dp)).border(1.dp, p.bd, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp).clickable { onOpen(if (acc.isCard) "cards" else "set-balance") }) {
                            Text((if (acc.isCard) "💳 " else "🏦 ") + acc.bank + " ··" + acc.tail, style = MaterialTheme.typography.labelSmall, color = p.t2, maxLines = 1)
                            Text(
                                if (acc.isCard) (cardSt?.let { "due " + compactRupees(it.outstandingPaise) } ?: "card") else acc.balancePaise?.let { formatRupees(it) } ?: "—",
                                style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace, color = if (acc.isCard) p.blue else p.t1
                            )
                            Text(if (acc.isCard) (cardSt?.let { "unbilled " + compactRupees(it.currentSpendPaise) } ?: "") else acc.source, style = MaterialTheme.typography.labelSmall, color = p.t3, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        // Net worth
        plan.netWorth?.let { nw -> item { NetWorthCard(nw) { onOpen("invest") } } }

        // Brain says
        state.report?.let { r ->
            item {
                SectionCard(tint = p.gold) {
                    SectionTitle("🧠 Brain says", "Open") { onOpen("brain") }
                    Text(r.headline, style = MaterialTheme.typography.bodyMedium)
                    (r.actions + plan.wealthActions).firstOrNull()?.let { Spacer(Modifier.height(6.dp)); Text("→ $it", style = MaterialTheme.typography.bodySmall, color = p.t2) }
                }
            }
        }

        // Categories
        item {
            SectionCard {
                SectionTitle("Spending by category", "Trends") { onOpen("trends") }
                if (state.categories.isEmpty()) EmptyHint("No spending recorded for this cycle yet.")
                else {
                    ShareBar(state.categories.take(8).map { categoryColor(it.category) to it.share })
                    Spacer(Modifier.height(10.dp))
                    state.categories.take(5).forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryDot(c.category, 28); Spacer(Modifier.width(10.dp))
                            Text(c.category, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${(c.share * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = p.t2, modifier = Modifier.width(36.dp))
                            Text(formatRupees(c.paise), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Daily spend
        item {
            SectionCard {
                val days = state.daily.size
                val today = if (isCurrent) dayOfMonth(now) - 1 else -1
                SectionTitle("Daily spend", "avg ${formatRupees(if (days > 0) state.expensePaise / (if (isCurrent) dayOfMonth(now) else days).coerceAtLeast(1) else 0)}/day")
                BarChart(state.daily, today, p.gold, labels = listOf("1", "${days / 4}", "${days / 2}", "${3 * days / 4}", "$days"))
            }
        }

        // Recent
        item {
            SectionCard {
                SectionTitle("Recent activity", "See all") { onOpen("activity") }
                if (state.monthTransactions.isEmpty()) EmptyHint("Nothing yet. Bank alerts will appear here automatically.")
                var lastDay = ""
                state.monthTransactions.take(8).forEach { t ->
                    val d = formatDay(t.timestamp)
                    if (d != lastDay) { Text(d, style = MaterialTheme.typography.labelSmall, color = p.t2, modifier = Modifier.padding(top = 6.dp)); lastDay = d }
                    TransactionRow(t) { onOpenTransaction(t) }
                }
            }
        }
    }
}

private fun dayOfMonthCal(ts: Long) = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_MONTH)

@Composable
fun NetWorthCard(nw: NetWorth, onOpen: () -> Unit) {
    val p = P
    SectionCard {
        SectionTitle("Net worth", "Holdings", onOpen)
        Text(formatRupees(nw.net), style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace, color = if (nw.net >= 0) p.t1 else p.red)
        Spacer(Modifier.height(8.dp))
        val base = (nw.assets).coerceAtLeast(1)
        BarRow("Bank", null, compactRupees(nw.bankPaise), nw.bankPaise.toFloat() / base, p.teal)
        BarRow("Investments", null, compactRupees(nw.holdingsPaise), nw.holdingsPaise.toFloat() / base, p.green)
        if (nw.receivablesPaise > 0) BarRow("Owed to you", null, compactRupees(nw.receivablesPaise), nw.receivablesPaise.toFloat() / base, p.green)
        BarRow("Loans", null, "-" + compactRupees(nw.loansPaise), nw.loansPaise.toFloat() / base, p.red)
        if (nw.cardsPaise > 0) BarRow("Card dues", null, "-" + compactRupees(nw.cardsPaise), nw.cardsPaise.toFloat() / base, p.orange)
        if (nw.informalPaise > 0) BarRow("Borrowed", null, "-" + compactRupees(nw.informalPaise), nw.informalPaise.toFloat() / base, p.orange)
        val g = nw.holdingsPaise - nw.holdingsInvestedPaise
        if (nw.holdingsInvestedPaise > 0) Text("Investments ${if (g >= 0) "up" else "down"} ${compactRupees(kotlin.math.abs(g))} on ${compactRupees(nw.holdingsInvestedPaise)} invested", style = MaterialTheme.typography.labelSmall, color = p.t3)
    }
}
