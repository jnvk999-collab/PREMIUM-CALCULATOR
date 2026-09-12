package com.financebrain.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financebrain.data.Categories
import com.financebrain.data.Direction
import com.financebrain.data.Transaction
import com.financebrain.parser.BankSmsParser
import com.financebrain.ui.components.CategoryDot
import com.financebrain.ui.formatDay
import com.financebrain.ui.formatRupees
import com.financebrain.ui.formatTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailSheet(
    t: Transaction,
    onDismiss: () -> Unit,
    onCategory: (String, Boolean) -> Unit,
    onNote: (String?) -> Unit,
    onDelete: () -> Unit,
    onSpam: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var remember by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf(t.note ?: "") }
    var showRaw by remember { mutableStateOf(false) }
    val credit = t.direction == Direction.CREDIT
    val options = if (credit) Categories.income else Categories.expense

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryDot(t.category, 48)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.counterparty, style = MaterialTheme.typography.titleLarge)
                    Text("${formatDay(t.timestamp)} · ${formatTime(t.timestamp)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                (if (credit) "+" else "-") + formatRupees(t.amountPaise, showPaise = true),
                style = MaterialTheme.typography.displaySmall,
                color = if (credit) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            val meta = buildList {
                add(t.bank + (t.accountTail?.let { " ··$it" } ?: ""))
                add(t.channel)
                t.reference?.let { add("Ref $it") }
                t.balancePaise?.let { add("Balance after: ${formatRupees(it)}") }
            }
            Text(meta.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(18.dp))
            Text("Category", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { c ->
                    FilterChip(selected = t.category == c, onClick = { onCategory(c, remember) }, label = { Text(c) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Remember for ${t.counterparty.take(22)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = remember, onCheckedChange = { remember = it })
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = note, onValueChange = { note = it; onNote(it.ifBlank { null }) },
                label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )

            if (t.rawText != null) {
                TextButton(onClick = { showRaw = !showRaw }) { Text(if (showRaw) "Hide original SMS" else "Show original SMS") }
                if (showRaw) Text(t.rawText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = onDelete, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("Delete")
                }
                TextButton(onClick = onSpam, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Block, null); Spacer(Modifier.width(6.dp)); Text("Not mine / spam")
                }
            }
            Text("Spam removes every entry from this sender name and blocks it in future scans.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionSheet(
    onDismiss: () -> Unit,
    onSave: (amountPaise: Long, direction: Direction, name: String, category: String, note: String?) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amount by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(Direction.DEBIT) }
    var category by remember { mutableStateOf(Categories.FOOD) }
    val options = if (direction == Direction.CREDIT) Categories.income else Categories.expense
    val paise = BankSmsParser.toPaise(amount) ?: 0L

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text("Add cash transaction", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = direction == Direction.DEBIT, onClick = { direction = Direction.DEBIT; category = Categories.FOOD }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Spent") }
                SegmentedButton(selected = direction == Direction.CREDIT, onClick = { direction = Direction.CREDIT; category = Categories.INCOME }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Received") }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amount, onValueChange = { v -> if (v.matches(Regex("""\d{0,9}(\.\d{0,2})?"""))) amount = v },
                label = { Text("Amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(if (direction == Direction.DEBIT) "Paid to" else "Received from") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Text("Category", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onSave(paise, direction, name.trim(), category, note.ifBlank { null }) }, enabled = paise > 0, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (paise > 0) "Save ${formatRupees(paise)}" else "Save")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetBalanceSheet(
    accounts: List<com.financebrain.data.Account>,
    accountViews: List<com.financebrain.ui.AccountView> = emptyList(),
    anchors: List<com.financebrain.data.BalanceAnchor>,
    onDismiss: () -> Unit,
    onSave: (key: String, amountPaise: Long) -> Unit,
    onClear: (key: String) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var target by remember { mutableStateOf("ALL") }
    var amount by remember { mutableStateOf("") }
    val paise = BankSmsParser.toPaise(amount) ?: 0L
    val existing = anchors.firstOrNull { it.key == target }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text("Set current balance", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Type what your bank shows right now. From here the app keeps it running: every credit adds, every debit subtracts.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Text("Which balance", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = target == "ALL", onClick = { target = "ALL" }, label = { Text("All accounts combined") })
                val options = (accountViews.filter { !it.isCard }.map { it.bank to it.tail } + accounts.map { it.bank to it.accountTail }).distinct()
                options.forEach { (bank, tail) ->
                    val k = "$bank|$tail"
                    FilterChip(selected = target == k, onClick = { target = k }, label = { Text("$bank ··$tail") })
                }
            }
            if (existing != null) {
                Spacer(Modifier.height(6.dp))
                Text("Currently set to ${formatRupees(existing.amountPaise)} on ${formatDay(existing.at)}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amount, onValueChange = { v -> if (v.matches(Regex("""\d{0,10}(\.\d{0,2})?"""))) amount = v },
                label = { Text("Balance today (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onSave(target, paise) }, enabled = paise >= 0 && amount.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (amount.isNotBlank()) "Start from ${formatRupees(paise)}" else "Save")
            }
            if (existing != null) TextButton(onClick = { onClear(target) }) { Text("Remove this balance and go back to bank-reported figures") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
