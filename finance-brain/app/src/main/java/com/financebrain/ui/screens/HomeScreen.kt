package com.financebrain.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financebrain.data.Transaction
import com.financebrain.sms.ScanProgress
import com.financebrain.ui.HomeState
import com.financebrain.ui.compactRupees
import com.financebrain.ui.components.BarChart
import com.financebrain.ui.components.CategoryDot
import com.financebrain.ui.components.EmptyHint
import com.financebrain.ui.components.SectionCard
import com.financebrain.ui.components.SectionTitle
import com.financebrain.ui.components.ShareBar
import com.financebrain.ui.components.StatPill
import com.financebrain.ui.components.TransactionRow
import com.financebrain.ui.components.categoryColor
import com.financebrain.ui.dayOfMonth
import com.financebrain.ui.formatDay
import com.financebrain.ui.formatMonth
import com.financebrain.ui.formatRupees
import com.financebrain.ui.monthStart
import com.financebrain.ui.theme.Coral
import com.financebrain.ui.theme.Leaf
import com.financebrain.ui.theme.Mint
import com.financebrain.ui.theme.Teal
import com.financebrain.ui.theme.TealDark

@Composable
fun HomeScreen(
    state: HomeState,
    scan: ScanProgress?,
    padding: PaddingValues,
    onShiftMonth: (Int) -> Unit,
    onOpenTransaction: (Transaction) -> Unit,
    onSeeAll: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val isCurrentMonth = state.month == monthStart(now)
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Finance Brain", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${state.totalCount} transactions tracked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MonthSwitcher(state.month, isCurrentMonth, onShiftMonth)
            }
        }

        if (scan != null && !scan.done) item { ScanBanner(scan) }

        item { HeroCard(state) }

        item { AccountsRow(state) }

        item {
            SectionCard {
                SectionTitle("Spending by category")
                if (state.categories.isEmpty()) EmptyHint("No spending recorded for ${formatMonth(state.month)} yet.")
                else {
                    ShareBar(state.categories.take(8).map { categoryColor(it.category) to it.share })
                    Spacer(Modifier.height(14.dp))
                    state.categories.take(6).forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryDot(c.category, 34)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("${c.count} payments · ${(c.share * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatRupees(c.paise), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item {
            SectionCard {
                val days = state.daily.size
                val today = if (isCurrentMonth) dayOfMonth(now) - 1 else -1
                val avg = if (days > 0) state.expensePaise / (if (isCurrentMonth) dayOfMonth(now) else days).coerceAtLeast(1) else 0
                SectionTitle("Daily spend", "avg ${formatRupees(avg)}/day")
                BarChart(
                    state.daily, today, Teal,
                    labels = listOf("1", "${days / 4}", "${days / 2}", "${3 * days / 4}", "$days")
                )
            }
        }

        if (state.recurring.isNotEmpty()) item {
            SectionCard {
                SectionTitle("Upcoming & recurring")
                state.recurring.take(5).forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryDot(r.category, 34)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Due ${formatDay(r.nextDue)} · seen ${r.occurrences}×", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatRupees(r.amountPaise), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            SectionCard {
                SectionTitle("Recent activity", "See all", onSeeAll)
                if (state.monthTransactions.isEmpty()) EmptyHint("Nothing yet. Bank alerts will appear here automatically.")
                var lastDay = ""
                state.monthTransactions.take(8).forEach { t ->
                    val d = formatDay(t.timestamp)
                    if (d != lastDay) {
                        Text(d, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        lastDay = d
                    }
                    TransactionRow(t) { onOpenTransaction(t) }
                }
            }
        }
    }
}

@Composable
private fun MonthSwitcher(month: Long, isCurrent: Boolean, onShift: (Int) -> Unit) {
    Row(
        Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50)).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onShift(-1) }, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
        Text(formatMonth(month), style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = { onShift(1) }, enabled = !isCurrent, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
    }
}

@Composable
private fun ScanBanner(scan: ScanProgress) {
    SectionCard {
        Text("Reading your SMS history…", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text("${scan.scanned} of ${scan.total} messages · ${scan.found} transactions found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        if (scan.total > 0) LinearProgressIndicator(progress = { scan.scanned.toFloat() / scan.total }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun HeroCard(state: HomeState) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Teal),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(Teal, TealDark)))) {
            Column(Modifier.padding(20.dp)) {
                Text("Total balance", style = MaterialTheme.typography.labelLarge, color = Mint)
                Text(
                    formatRupees(state.totalBalancePaise),
                    style = MaterialTheme.typography.displaySmall,
                    color = androidx.compose.ui.graphics.Color.White,
                )
                Text(
                    if (state.accounts.isEmpty()) "Balances appear once a bank alert reports one" else "across ${state.accounts.size} account${if (state.accounts.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall, color = Mint.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroStat("Income", state.incomePaise, Modifier.weight(1f))
                    HeroStat("Spent", state.expensePaise, Modifier.weight(1f))
                    HeroStat("Saved", state.savedPaise, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, paise: Long, modifier: Modifier) {
    Column(modifier.background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Mint)
        Text(compactRupees(paise), style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
    }
}

@Composable
private fun AccountsRow(state: HomeState) {
    if (state.accounts.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(state.accounts) { a ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(if (a.kind == "CARD") Icons.Default.CreditCard else Icons.Default.AccountBalance, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("${a.bank} ··${a.accountTail}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(a.balancePaise?.let { formatRupees(it) } ?: "—", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
