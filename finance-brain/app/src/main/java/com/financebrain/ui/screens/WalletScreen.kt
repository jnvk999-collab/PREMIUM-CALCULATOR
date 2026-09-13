package com.financebrain.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financebrain.data.Direction
import com.financebrain.data.Transaction
import com.financebrain.ui.HomeState
import com.financebrain.ui.components.SectionCard
import com.financebrain.ui.components.SectionTitle
import com.financebrain.ui.formatDay
import com.financebrain.ui.formatRupees
import com.financebrain.ui.formatTime
import com.financebrain.ui.theme.P

/**
 * The figure on Home, shown as a sum you can check line by line: the balance it started from,
 * every movement counted since, and the total. Tap any line to correct it.
 */
@Composable
fun WalletScreen(
    state: HomeState,
    padding: PaddingValues,
    onOpenTransaction: (Transaction) -> Unit,
    onSetBalance: (String?) -> Unit,
) {
    val p = P
    val w = state.wallet

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("How this number is worked out", style = MaterialTheme.typography.headlineSmall, color = p.t1) }

        if (w == null) {
            item {
                SectionCard {
                    Text("No starting balance yet.", style = MaterialTheme.typography.bodyMedium)
                    Text("Enter what your bank shows and every payment after it is counted here.", style = MaterialTheme.typography.bodySmall, color = p.t2)
                    Spacer(Modifier.height(8.dp))
                    Text("Enter balance", style = MaterialTheme.typography.labelMedium, color = p.gold, modifier = Modifier.clickable { onSetBalance(null) })
                }
            }
            return@LazyColumn
        }

        item {
            SectionCard(tint = p.gold) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (w.fromUser) "You entered this" else "Your bank reported this", style = MaterialTheme.typography.bodyMedium)
                        Text("${formatDay(w.baseAt)} · everything after it counts", style = MaterialTheme.typography.labelSmall, color = p.t2)
                    }
                    Text(formatRupees(w.basePaise), style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, color = p.t1)
                }
                Spacer(Modifier.height(8.dp))
                Text("Change this", style = MaterialTheme.typography.labelMedium, color = p.gold, modifier = Modifier.clickable { onSetBalance(null) })
            }
        }

        item {
            SectionCard {
                SectionTitle("Counted since then · ${w.rows.size}")
                if (w.rows.isEmpty()) Text("Nothing has moved since that balance.", style = MaterialTheme.typography.bodySmall, color = p.t2)
            }
        }

        items(w.rows) { t ->
            val out = t.direction == Direction.DEBIT
            SectionCard {
                Row(Modifier.fillMaxWidth().clickable { onOpenTransaction(t) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.counterparty, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatDay(t.timestamp)} ${formatTime(t.timestamp)} · ${t.bank}" +
                                (t.accountTail?.let { " ··$it" } ?: "") +
                                (if (t.accountKind == "CARD") " card" else "") + " · ${t.category}",
                            style = MaterialTheme.typography.labelSmall, color = p.t2
                        )
                    }
                    Text(
                        (if (out) "−" else "+") + formatRupees(t.amountPaise),
                        style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                        color = if (out) p.red else p.green
                    )
                }
            }
        }

        item {
            SectionCard(tint = p.green) {
                Line("Started with", w.basePaise, p.t1)
                if (w.creditsPaise > 0) Line("Money in", w.creditsPaise, p.green, sign = "+")
                if (w.bankDebitsPaise > 0) Line("Paid from your accounts", w.bankDebitsPaise, p.red, sign = "−")
                if (w.cardDebitsPaise > 0) Line("Spent on cards", w.cardDebitsPaise, p.red, sign = "−")
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = p.bd)
                Spacer(Modifier.height(6.dp))
                Line("Money you have now", w.paise, p.t1, bold = true)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Card spending is counted here because the money is gone the moment you pay. " +
                        "When the card bill is paid from your bank it is not counted again.",
                    style = MaterialTheme.typography.labelSmall, color = p.t2
                )
            }
        }

        item {
            SectionCard {
                Text("Something here that is not yours, or in the wrong place? Tap it and pick the right category. Your answer wins over anything the app worked out.", style = MaterialTheme.typography.bodySmall, color = p.t2)
            }
        }
    }
}

@Composable
private fun Line(label: String, paise: Long, color: androidx.compose.ui.graphics.Color, sign: String = "", bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            sign + formatRupees(paise), style = MaterialTheme.typography.bodyMedium, color = color,
            fontFamily = FontFamily.Monospace, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}
