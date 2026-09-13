package com.financebrain.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financebrain.data.Direction
import com.financebrain.data.Transaction
import com.financebrain.ui.components.EmptyHint
import com.financebrain.ui.components.TransactionRow
import com.financebrain.ui.formatDay
import com.financebrain.ui.formatRupees

@Composable
fun TransactionsScreen(all: List<Transaction>, padding: PaddingValues, onOpen: (Transaction) -> Unit, showHeader: Boolean = true, trackingStart: Long = 0) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    val banks = remember(all) { all.map { it.bank }.distinct().sorted() }
    val filters = listOf("All", "Spent", "Received") + banks

    val shown = remember(all, query, filter) {
        all.filter { t ->
            val f = when (filter) {
                "All" -> true
                "Spent" -> t.direction == Direction.DEBIT
                "Received" -> t.direction == Direction.CREDIT
                else -> t.bank == filter
            }
            val q = query.trim().lowercase()
            f && (q.isEmpty() || t.counterparty.lowercase().contains(q) || t.category.lowercase().contains(q) ||
                (t.note?.lowercase()?.contains(q) == true) || formatRupees(t.amountPaise).contains(q) || (t.amountPaise / 100).toString().contains(q))
        }
    }
    val grouped = remember(shown) { shown.groupBy { formatDay(it.timestamp) } }

    Column(Modifier.padding(top = padding.calculateTopPadding())) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (showHeader) { Spacer(Modifier.height(8.dp)); Text("Transactions", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(10.dp)) }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search merchant, category, amount") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Close, "Clear") } },
                singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filters) { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) }) }
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 96.dp)) {
            if (shown.isEmpty()) item { EmptyHint("No transactions match.") }
            var dividerShown = false
            grouped.forEach { (day, list) ->
                if (!dividerShown && trackingStart > 0 && list.first().timestamp < trackingStart) {
                    dividerShown = true
                    item(key = "divider") {
                        Text("— before tracking started (not counted) —", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                item(key = "h-$day") {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        val spent = list.filter { it.direction == Direction.DEBIT }.sumOf { it.amountPaise }
                        if (spent > 0) Text("-${formatRupees(spent)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    }
                }
                items(list, key = { it.id }) { t -> TransactionRow(t) { onOpen(t) } }
            }
        }
    }
}
