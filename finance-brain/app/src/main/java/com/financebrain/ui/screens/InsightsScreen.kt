package com.financebrain.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financebrain.data.Direction
import com.financebrain.data.Insights
import com.financebrain.data.Recurring
import com.financebrain.ui.HomeState
import com.financebrain.ui.components.CategoryDot
import com.financebrain.ui.components.EmptyHint
import com.financebrain.ui.components.SectionCard
import com.financebrain.ui.components.SectionTitle
import com.financebrain.ui.components.StatPill
import com.financebrain.ui.compactRupees
import com.financebrain.ui.formatDay
import com.financebrain.ui.formatMonth
import com.financebrain.ui.formatCycle
import com.financebrain.ui.formatMonthShort
import com.financebrain.ui.formatRupees
import com.financebrain.ui.theme.Coral
import com.financebrain.ui.theme.Leaf
import com.financebrain.ui.theme.Teal

private val segments = listOf("Trends", "Recurring", "Cards", "Calendar", "Investments", "Loans & Salary", "Goals", "Discipline")

@Composable
fun InsightsScreen(state: HomeState, plan: com.financebrain.ui.PlanState, vm: com.financebrain.ui.MainViewModel, padding: PaddingValues, initial: Int = 0, onOpenSettings: () -> Unit = {}) {
    var seg by rememberSaveable(initial) { mutableStateOf(initial) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Money", style = MaterialTheme.typography.headlineSmall) }
        item {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(segments.size) { i -> FilterChip(selected = seg == i, onClick = { seg = i }, label = { Text(segments[i]) }) }
            }
        }
        when (seg) {
            0 -> trends(state)
            1 -> recurring(state)
            2 -> cardsSection(plan, vm)
            3 -> calendarSection(state, plan)
            4 -> { holdingsSection(plan, vm); investments(state) }
            5 -> { formalLoansSection(plan, vm); loansAndSalary(state) }
            6 -> goalsSection(plan, vm)
            else -> disciplineSection(plan, vm, onOpenSettings)
        }
    }
}

// ---------------------------------------------------------------- Trends
private fun LazyListScope.trends(state: HomeState) {
    val months = state.months.takeLast(6)
    val prev = months.dropLast(1).lastOrNull()
    val cur = months.lastOrNull()
    val delta = if (prev != null && cur != null && prev.expensePaise > 0) ((cur.expensePaise - prev.expensePaise) * 100 / prev.expensePaise) else null
    val avgSpend = months.filter { it.expensePaise > 0 }.map { it.expensePaise }.average().takeIf { !it.isNaN() }?.toLong() ?: 0
    val savingsRate = if (state.incomePaise > 0) ((state.incomePaise - state.expensePaise) * 100 / state.incomePaise).toInt() else null
    val channels = state.monthTransactions.filter(Insights::isSpend).groupBy { it.channel }.map { it.key to it.value.sumOf { t -> t.amountPaise } }.sortedByDescending { it.second }

    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill("vs last month", delta?.let { (if (it >= 0) "+" else "") + "$it%" } ?: "—", if ((delta ?: 0) > 0) Coral else Leaf, Modifier.weight(1f))
            StatPill("Savings rate", savingsRate?.let { "$it%" } ?: "—", Teal, Modifier.weight(1f))
            StatPill("Avg monthly", compactRupees(avgSpend), Teal, Modifier.weight(1f))
        }
    }
    item {
        SectionCard {
            SectionTitle("Income vs spending · 6 months")
            if (months.all { it.incomePaise == 0L && it.expensePaise == 0L }) EmptyHint("Data appears as months are tracked.")
            else {
                val max = months.maxOf { maxOf(it.incomePaise, it.expensePaise) }.coerceAtLeast(1).toFloat()
                Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                    val slot = size.width / months.size
                    val bw = slot * 0.22f
                    months.forEachIndexed { i, m ->
                        val x0 = i * slot + slot * 0.14f
                        val hi = m.incomePaise / max * size.height
                        val he = m.expensePaise / max * size.height
                        val hv = m.investedPaise / max * size.height
                        drawRoundRect(Leaf, Offset(x0, size.height - hi), Size(bw, hi.coerceAtLeast(2f)), CornerRadius(6f, 6f))
                        drawRoundRect(Coral, Offset(x0 + bw + 3f, size.height - he), Size(bw, he.coerceAtLeast(2f)), CornerRadius(6f, 6f))
                        drawRoundRect(Teal, Offset(x0 + 2 * bw + 6f, size.height - hv), Size(bw, hv.coerceAtLeast(2f)), CornerRadius(6f, 6f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    months.forEach { Text(formatMonthShort(it.monthStart), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { Legend(Leaf, "Income"); Legend(Coral, "Spent"); Legend(Teal, "Invested") }
            }
        }
    }
    item {
        SectionCard {
            SectionTitle("Balance at month end")
            val withBal = months.filter { it.endBalancePaise != null }
            if (withBal.isEmpty()) EmptyHint("Appears once bank alerts report balances.")
            else {
                val max = withBal.maxOf { it.endBalancePaise!! }.coerceAtLeast(1).toFloat()
                Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                    val slot = size.width / withBal.size
                    val bw = slot * 0.5f
                    withBal.forEachIndexed { i, m ->
                        val h = m.endBalancePaise!! / max * size.height
                        drawRoundRect(Teal, Offset(i * slot + (slot - bw) / 2, size.height - h), Size(bw, h.coerceAtLeast(2f)), CornerRadius(8f, 8f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    withBal.forEach {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatMonthShort(it.monthStart), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(compactRupees(it.endBalancePaise!!), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
    item {
        SectionCard {
            SectionTitle("Monthly totals", "income · spent · invested")
            months.reversed().forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatMonth(m.monthStart), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("+${compactRupees(m.incomePaise)}", style = MaterialTheme.typography.bodyMedium, color = Leaf, modifier = Modifier.width(78.dp))
                    Text("-${compactRupees(m.expensePaise)}", style = MaterialTheme.typography.bodyMedium, color = Coral, modifier = Modifier.width(78.dp))
                    Text(compactRupees(m.investedPaise), style = MaterialTheme.typography.bodyMedium, color = Teal, modifier = Modifier.width(70.dp))
                }
            }
        }
    }
    item {
        SectionCard {
            SectionTitle("Top merchants · ${formatCycle(state.month)}")
            if (state.topMerchants.isEmpty()) EmptyHint("No spending this month.")
            val max = state.topMerchants.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
            state.topMerchants.forEach { (m, p) ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(m, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatRupees(p), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                        Box(Modifier.fillMaxWidth(p.toFloat() / max).height(6.dp).background(Teal, CircleShape))
                    }
                }
            }
        }
    }
    item {
        SectionCard {
            SectionTitle("By payment method")
            if (channels.isEmpty()) EmptyHint("No spending this month.")
            channels.forEach { (c, p) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(c, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(formatRupees(p), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Recurring
private fun LazyListScope.recurring(state: HomeState) {
    item {
        var showIncome by rememberSaveable { mutableStateOf(false) }
        val debits = state.recurring.filter { it.direction == Direction.DEBIT }
        val credits = state.recurring.filter { it.direction == Direction.CREDIT }
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showIncome, onClick = { showIncome = false }, label = { Text("Expenses · ${debits.size}") })
                FilterChip(selected = showIncome, onClick = { showIncome = true }, label = { Text("Incomes · ${credits.size}") })
            }
            Spacer(Modifier.height(12.dp))
            val list = if (showIncome) credits else debits
            SectionCard {
                val total = list.sumOf { it.amountPaise }
                SectionTitle(if (showIncome) "Money that comes in every month" else "Money that goes out every month", "≈ ${formatRupees(total)}/mo")
                if (list.isEmpty()) EmptyHint(if (showIncome) "No repeating credits found yet. Salary, rent received and interest show up here after two months." else "No repeating payments found yet. EMIs, SIPs, rent and subscriptions show up here after two months.")
                list.forEach { RecurringRow(it) }
            }
        }
    }
}

@Composable
private fun RecurringRow(r: Recurring) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryDot(r.category, 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${r.category} · ${r.bank} · seen ${r.monthsSeen} months", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Last ${formatDay(r.lastAt)} · next ${formatDay(r.nextDue)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            (if (r.direction == Direction.CREDIT) "+" else "") + formatRupees(r.amountPaise),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = if (r.direction == Direction.CREDIT) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---------------------------------------------------------------- Investments
private fun LazyListScope.investments(state: HomeState) {
    val lines = state.investments
    val invested = lines.sumOf { it.investedPaise }
    val redeemed = lines.sumOf { it.redeemedPaise }
    val monthly = state.recurring.filter { it.direction == Direction.DEBIT && it.category == com.financebrain.data.Categories.INVESTMENT }.sumOf { it.amountPaise }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill("Total invested", compactRupees(invested), Teal, Modifier.weight(1f))
            StatPill("Redeemed", compactRupees(redeemed), Leaf, Modifier.weight(1f))
            StatPill("SIPs / month", compactRupees(monthly), Teal, Modifier.weight(1f))
        }
    }
    item {
        SectionCard {
            SectionTitle("By platform")
            if (lines.isEmpty()) EmptyHint("No investments detected yet. Groww, Zerodha, Coin, Upstox, Kuvera, ETMoney, INDmoney, BSE/NSE mandates, FD and RD are recognised from SMS and email.")
            lines.groupBy { it.platform }.map { (p, ls) -> p to ls.sumOf { it.investedPaise } }.sortedByDescending { it.second }.forEach { (p, amt) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(p, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(formatRupees(amt), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    item {
        SectionCard {
            SectionTitle("Money sent to investments", "from bank alerts, cost basis")
            lines.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${l.platform} · ${l.count} payments · last ${formatDay(l.lastAt)}" + if (l.monthly) " · SIP" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatRupees(l.investedPaise), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (l.redeemedPaise > 0) Text("-${formatRupees(l.redeemedPaise)} out", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Market value needs a statement. Statement import is the next milestone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- Loans & Salary
private fun LazyListScope.loansAndSalary(state: HomeState) {
    item {
        SectionCard {
            SectionTitle("Salary")
            val sal = state.salary
            if (sal == null) EmptyHint("No salary credit identified yet. It is detected from credits marked as salary, or from the largest credit that repeats monthly.")
            else {
                Text(sal.employer, style = MaterialTheme.typography.titleMedium)
                Text("${formatRupees(sal.amountPaise)} · ${sal.bank}", style = MaterialTheme.typography.bodyMedium)
                Text("Last credited ${formatDay(sal.lastAt)} · next expected ${formatDay(sal.nextExpected)} · seen ${sal.months} months" + if (!sal.confirmed) " · inferred" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!sal.confirmed) Text("Open one of these credits and set its category to Salary to confirm.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    item {
        SectionCard {
            val loans = state.loans
            SectionTitle("Loans & EMI", "≈ ${formatRupees(loans.sumOf { it.emiPaise })}/mo")
            if (loans.isEmpty()) EmptyHint("No EMIs found. Repeating payments to a lender, or filed under EMI & Loans, appear here.")
            loans.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryDot(com.financebrain.data.Categories.EMI, 36)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l.lender, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Next due ${formatDay(l.nextDue)} · ${l.installments} paid in 12 months (${formatRupees(l.paidLast12Paise)})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatRupees(l.emiPaise), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            val sal = state.salary
            if (loans.isNotEmpty() && sal != null) {
                val share = (loans.sumOf { it.emiPaise } * 100 / sal.amountPaise.coerceAtLeast(1)).toInt()
                Spacer(Modifier.height(6.dp))
                Text("EMIs take $share% of salary." + if (share > 40) " Above 40% is considered stretched." else "", style = MaterialTheme.typography.bodySmall, color = if (share > 40) Coral else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape)); Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
