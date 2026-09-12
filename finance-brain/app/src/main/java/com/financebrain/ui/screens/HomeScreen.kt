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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financebrain.data.ReviewItem
import com.financebrain.data.Transaction
import com.financebrain.sms.ScanProgress
import com.financebrain.ui.HomeState
import com.financebrain.ui.PlanState
import com.financebrain.ui.compactRupees
import com.financebrain.ui.components.SectionCard
import com.financebrain.ui.components.SectionTitle
import com.financebrain.ui.components.TransactionRow
import com.financebrain.ui.components.UpdateCard
import com.financebrain.ui.formatCycle
import com.financebrain.ui.formatDay
import com.financebrain.ui.formatRupees
import com.financebrain.ui.monthStart
import com.financebrain.ui.theme.P
import com.financebrain.update.UpdateState

/** Home answers three questions: how much can I spend, how much do I have, what needs me. */
@Composable
fun HomeScreen(
    state: HomeState, plan: PlanState, scan: ScanProgress?, padding: PaddingValues, update: UpdateState,
    onUpdateDownload: () -> Unit, onUpdateInstall: () -> Unit, onUpdateDismiss: () -> Unit,
    onShiftMonth: (Int) -> Unit,
    onOpenTransaction: (Transaction) -> Unit,
    onOpen: (String) -> Unit,
    onSetBalance: (String?) -> Unit,
    onReview: (ReviewItem, String) -> Unit,
) {
    val p = P
    val now = System.currentTimeMillis()
    val isCurrent = state.month == monthStart(now)
    val a = plan.allocation
    val income = a?.incomePaise ?: state.incomePaise
    val left = if (a != null) a.remainingPaise else income - state.expensePaise - state.investedPaise
    val leftColor = if (left < 0) p.red else if (a != null && left < a.freeToSpendPaise / 4) p.orange else p.green

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onShiftMonth(-1) }, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", tint = p.t2) }
                Text(formatCycle(state.month), style = MaterialTheme.typography.titleMedium, color = p.t1)
                IconButton(onClick = { onShiftMonth(1) }, enabled = !isCurrent, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", tint = if (isCurrent) p.t3 else p.t2) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onOpen("settings") }) { Icon(Icons.Default.Settings, "Settings", tint = p.t2) }
            }
        }
        item { UpdateCard(update, onUpdateDownload, onUpdateInstall, onUpdateDismiss) }
        if (scan != null && !scan.done) item {
            SectionCard(tint = p.blue) {
                Text("Reading your messages · ${scan.scanned}/${scan.total}", style = MaterialTheme.typography.labelMedium, color = p.blue)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { if (scan.total > 0) scan.scanned.toFloat() / scan.total else 0f }, modifier = Modifier.fillMaxWidth(), color = p.blue, trackColor = p.bd)
            }
        }

        // 1. How much can I spend
        item {
            SectionCard(tint = leftColor) {
                Text((if (isCurrent) "LEFT TO SPEND THIS MONTH" else "LEFT OVER THAT MONTH"), style = MaterialTheme.typography.labelSmall, color = p.t2)
                Text(formatRupees(left), style = MaterialTheme.typography.displaySmall, color = leftColor, fontFamily = FontFamily.Monospace)
                Text(
                    when {
                        a == null -> "Income ${compactRupees(income)} − spent ${compactRupees(state.expensePaise)} − invested ${compactRupees(state.investedPaise)}"
                        a.daysLeft > 0 -> "${formatRupees(a.perDayPaise.coerceAtLeast(0))} a day for ${a.daysLeft} more days · spent ${compactRupees(a.spentSoFarPaise)} of ${compactRupees(a.freeToSpendPaise)}"
                        else -> "Spent ${compactRupees(a.spentSoFarPaise)} of ${compactRupees(a.freeToSpendPaise)} that was free after EMIs, bills and investing"
                    },
                    style = MaterialTheme.typography.bodySmall, color = p.t2
                )
                Spacer(Modifier.height(10.dp))
                MonthBar(income, state.expensePaise, a?.emiPaise ?: 0, a?.cardDuePaise ?: 0, state.investedPaise, a?.fixedBillsPaise ?: 0)
            }
        }

        // 2. How much do I have
        plan.netWorth?.let { nw ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile("BANK", compactRupees(nw.bankPaise), p.teal, Modifier.weight(1f)) { onOpen("money:accounts") }
                    Tile("INVESTED", compactRupees(nw.holdingsPaise), p.green, Modifier.weight(1f)) { onOpen("money:invest") }
                    Tile("LOANS", compactRupees(nw.loansPaise + nw.cardsPaise), p.red, Modifier.weight(1f)) { onOpen("money:loans") }
                }
                Text("Net worth ${formatRupees(nw.net)}", style = MaterialTheme.typography.labelMedium, color = p.t2, modifier = Modifier.padding(top = 6.dp, start = 2.dp).clickable { onOpen("money:accounts") })
            }
        }

        // 3. What needs me
        if (plan.review.isNotEmpty()) item {
            SectionCard(tint = p.gold) {
                SectionTitle("Needs your answer · ${plan.review.size}")
                plan.review.take(4).forEach { r -> ReviewRow(r, onReview, onSetBalance, onOpen) }
                if (plan.review.size > 4) Text("${plan.review.size - 4} more after these", style = MaterialTheme.typography.labelSmall, color = p.t3)
            }
        }

        state.report?.let { r ->
            item {
                SectionCard {
                    SectionTitle("🧠 Brain", "More") { onOpen("brain") }
                    Text(r.headline, style = MaterialTheme.typography.bodyMedium)
                    (r.actions + plan.wealthActions).firstOrNull()?.let { Spacer(Modifier.height(4.dp)); Text("→ $it", style = MaterialTheme.typography.bodySmall, color = p.t2) }
                }
            }
        }

        item {
            SectionCard {
                SectionTitle("Recent", "All activity") { onOpen("spend:activity") }
                if (state.monthTransactions.isEmpty()) Text("Nothing yet this month.", style = MaterialTheme.typography.bodySmall, color = p.t2)
                var lastDay = ""
                state.monthTransactions.take(6).forEach { t ->
                    val d = formatDay(t.timestamp)
                    if (d != lastDay) { Text(d, style = MaterialTheme.typography.labelSmall, color = p.t2, modifier = Modifier.padding(top = 6.dp)); lastDay = d }
                    TransactionRow(t) { onOpenTransaction(t) }
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val p = P
    Column(modifier.background(p.s2, RoundedCornerShape(12.dp)).border(1.dp, p.bd, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = p.t2)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One bar: where this month's income went. */
@Composable
private fun MonthBar(income: Long, spent: Long, emi: Long, cardDue: Long, invested: Long, bills: Long) {
    val p = P
    val total = maxOf(income, spent + emi + cardDue + invested + bills).coerceAtLeast(1).toFloat()
    val parts = listOf("Spent" to spent, "EMIs" to emi, "Bills" to bills, "Card bills" to cardDue, "Invested" to invested).filter { it.second > 0 }
    val colors = mapOf("Spent" to p.red, "EMIs" to p.orange, "Bills" to p.purple, "Card bills" to p.blue, "Invested" to p.gold)
    val used = parts.sumOf { it.second }
    Row(Modifier.fillMaxWidth().height(10.dp).background(p.bd, RoundedCornerShape(5.dp))) {
        parts.forEach { (k, v) -> Box(Modifier.fillMaxWidth(0f).weight((v / total).coerceAtLeast(0.001f)).height(10.dp).background(colors[k]!!)) }
        if (income > used) Box(Modifier.weight(((income - used) / total).coerceAtLeast(0.001f)).height(10.dp).background(p.green.copy(alpha = 0.5f)))
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        parts.forEach { (k, v) -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).background(colors[k]!!, CircleShape)); Spacer(Modifier.width(4.dp)); Text("$k ${compactRupees(v)}", style = MaterialTheme.typography.labelSmall, color = p.t2) } }
        if (income > used) Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).background(p.green.copy(alpha = 0.5f), CircleShape)); Spacer(Modifier.width(4.dp)); Text("Left ${compactRupees(income - used)}", style = MaterialTheme.typography.labelSmall, color = p.t2) }
    }
}

@Composable
private fun ReviewRow(r: ReviewItem, onReview: (ReviewItem, String) -> Unit, onSetBalance: (String?) -> Unit, onOpen: (String) -> Unit) {
    val p = P
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(r.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(r.detail, style = MaterialTheme.typography.bodySmall, color = p.t2)
        Row {
            when (r) {
                is ReviewItem.BigUnknown -> { Pill("Spend") { onReview(r, "spend") }; Pill("Transfer to me") { onReview(r, "transfer") }; Pill("Investment") { onReview(r, "investment") }; Pill("Not mine") { onReview(r, "spam") } }
                is ReviewItem.BigCredit -> { Pill("Income") { onReview(r, "income") }; Pill("Salary") { onReview(r, "salary") }; Pill("Transfer to me") { onReview(r, "transfer") }; Pill("Not mine") { onReview(r, "spam") } }
                is ReviewItem.ConfirmSalary -> { Pill("Yes, salary") { onReview(r, "yes") }; Pill("No") { onReview(r, "dismiss") } }
                is ReviewItem.NoBalance -> { Pill("Enter balance") { onSetBalance("${r.bank}|${r.tail}") }; Pill("Skip") { onReview(r, "dismiss") } }
                is ReviewItem.NoLimit -> { Pill("Set limit") { onOpen("money:cards") }; Pill("Skip") { onReview(r, "dismiss") } }
                is ReviewItem.NoSalaryDay -> { Pill("Set it") { onOpen("settings") }; Pill("It's the 1st") { onReview(r, "dismiss") } }
            }
        }
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    val p = P
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, color = p.gold) }
}
