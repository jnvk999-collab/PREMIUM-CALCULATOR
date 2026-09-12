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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financebrain.data.Insights
import com.financebrain.ui.HomeState
import com.financebrain.ui.components.EmptyHint
import com.financebrain.ui.components.SectionCard
import com.financebrain.ui.components.SectionTitle
import com.financebrain.ui.components.StatPill
import com.financebrain.ui.compactRupees
import com.financebrain.ui.formatMonth
import com.financebrain.ui.formatMonthShort
import com.financebrain.ui.formatRupees
import com.financebrain.ui.theme.Coral
import com.financebrain.ui.theme.Leaf
import com.financebrain.ui.theme.Teal

@Composable
fun InsightsScreen(state: HomeState, padding: PaddingValues) {
    val months = state.months.takeLast(6)
    val prev = months.dropLast(1).lastOrNull()
    val cur = months.lastOrNull()
    val delta = if (prev != null && cur != null && prev.expensePaise > 0) ((cur.expensePaise - prev.expensePaise) * 100 / prev.expensePaise) else null
    val avgSpend = months.filter { it.expensePaise > 0 }.map { it.expensePaise }.average().takeIf { !it.isNaN() }?.toLong() ?: 0
    val savingsRate = if (state.incomePaise > 0) (state.savedPaise * 100 / state.incomePaise).toInt() else null
    val chanels = state.monthTransactions.filter(Insights::isSpend).groupBy { it.channel }.map { it.key to it.value.sumOf { t -> t.amountPaise } }.sortedByDescending { it.second }

    LazyColumn(
        contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Insights", style = MaterialTheme.typography.headlineSmall) }
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
                        val bw = slot * 0.28f
                        months.forEachIndexed { i, m ->
                            val x0 = i * slot + slot * 0.18f
                            val hi = m.incomePaise / max * size.height
                            val he = m.expensePaise / max * size.height
                            drawRoundRect(Leaf, Offset(x0, size.height - hi), Size(bw, hi.coerceAtLeast(2f)), CornerRadius(6f, 6f))
                            drawRoundRect(Coral, Offset(x0 + bw + 4f, size.height - he), Size(bw, he.coerceAtLeast(2f)), CornerRadius(6f, 6f))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        months.forEach { Text(formatMonthShort(it.monthStart), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend(Leaf, "Income"); Legend(Coral, "Spent")
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
                        withBal.forEach { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatMonthShort(it.monthStart), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(compactRupees(it.endBalancePaise!!), style = MaterialTheme.typography.labelSmall)
                        } }
                    }
                }
            }
        }
        item {
            SectionCard {
                SectionTitle("Top merchants · ${formatMonth(state.month)}")
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
                if (chanels.isEmpty()) EmptyHint("No spending this month.")
                chanels.forEach { (c, p) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(c, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(formatRupees(p), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
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
