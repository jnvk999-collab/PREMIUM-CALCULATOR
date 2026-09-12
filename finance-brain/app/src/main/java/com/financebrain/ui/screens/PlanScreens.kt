package com.financebrain.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financebrain.data.Categories
import com.financebrain.data.ControlledCategory
import com.financebrain.data.CreditCard
import com.financebrain.data.Goal
import com.financebrain.data.InformalLoan
import com.financebrain.data.Receivable
import com.financebrain.data.ZeroTolerance
import com.financebrain.parser.BankSmsParser
import com.financebrain.ui.HomeState
import com.financebrain.ui.MainViewModel
import com.financebrain.ui.PlanState
import com.financebrain.ui.components.CategoryDot
import com.financebrain.ui.components.EmptyHint
import com.financebrain.ui.components.SectionCard
import com.financebrain.ui.components.SectionTitle
import com.financebrain.ui.components.StatPill
import com.financebrain.ui.compactRupees
import com.financebrain.ui.formatDay
import com.financebrain.ui.formatRupees
import com.financebrain.ui.theme.Amber
import com.financebrain.ui.theme.Coral
import com.financebrain.ui.theme.Leaf
import com.financebrain.ui.theme.Teal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun parseDate(s: String): Long? = try { SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).apply { isLenient = false }.parse(s.trim())?.time } catch (_: Exception) { null }
private fun today(): String = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(java.util.Date())

@Composable
private fun AmountField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = { v -> if (v.matches(Regex("""\d{0,10}(\.\d{0,2})?"""))) onChange(v) },
        label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DateField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text("$label (dd/mm/yyyy)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = value.isNotBlank() && parseDate(value) == null)
}

// ======================================================================= Cards
fun LazyListScope.cardsSection(plan: PlanState, vm: MainViewModel) {
    item {
        var editing by remember { mutableStateOf<CreditCard?>(null) }
        var adding by remember { mutableStateOf(false) }
        val total = plan.cards.sumOf { it.outstandingPaise }
        val unbilled = plan.cards.sumOf { it.currentSpendPaise }
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("Bills due", compactRupees(total), if (total > 0) Coral else Leaf, Modifier.weight(1f))
                StatPill("Unbilled spend", compactRupees(unbilled), Teal, Modifier.weight(1f))
                StatPill("Cards", "${plan.cards.size}", Teal, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            if (plan.cards.isEmpty()) SectionCard { EmptyHint("Cards appear automatically from card spend alerts. You can also add one.") }
            plan.cards.forEach { st ->
                SectionCard(Modifier.padding(bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(st.card.name, style = MaterialTheme.typography.titleMedium)
                            Text("Bills on day ${st.card.billingDay} · due ${formatDay(st.dueAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { editing = st.card }) { Text("Edit") }
                    }
                    Spacer(Modifier.height(8.dp))
                    val u = st.utilisation
                    if (u != null) {
                        LinearProgressIndicator(progress = { u.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = if (u > 0.5f) Coral else if (u > 0.3f) Amber else Leaf)
                        Spacer(Modifier.height(4.dp))
                        Text("${(u * 100).toInt()}% of ${formatRupees(st.card.limitPaise!!)} limit used · ${formatRupees(st.availablePaise!!)} available" + if (u > 0.3f) " · keep under 30% for a healthy score" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else Text("Set the limit to see utilisation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatPill("This cycle", compactRupees(st.currentSpendPaise), Teal, Modifier.weight(1f))
                        StatPill("Last statement", compactRupees(st.lastStatementPaise), Amber, Modifier.weight(1f))
                        StatPill("Outstanding", compactRupees(st.outstandingPaise), if (st.outstandingPaise > 0) Coral else Leaf, Modifier.weight(1f))
                    }
                    if (st.paidSincePaise > 0) { Spacer(Modifier.height(6.dp)); Text("Paid ${formatRupees(st.paidSincePaise)} since the statement.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            OutlinedButton(onClick = { adding = true }) { Text("Add card manually") }
        }
        val target = editing
        if (target != null || adding) CardDialog(target, onDismiss = { editing = null; adding = false },
            onSave = { vm.saveCard(it); editing = null; adding = false }, onDelete = { vm.deleteCard(it); editing = null })
    }
}

@Composable
private fun CardDialog(card: CreditCard?, onDismiss: () -> Unit, onSave: (CreditCard) -> Unit, onDelete: (String) -> Unit) {
    var name by remember { mutableStateOf(card?.name ?: "") }
    var bank by remember { mutableStateOf(card?.bank ?: "") }
    var tail by remember { mutableStateOf(card?.tail ?: "") }
    var limit by remember { mutableStateOf(card?.limitPaise?.let { (it / 100).toString() } ?: "") }
    var billing by remember { mutableStateOf(card?.billingDay?.toString() ?: "1") }
    var dueAfter by remember { mutableStateOf(card?.dueDaysAfter?.toString() ?: "20") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (card == null) "Add card" else "Edit card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                if (card == null) {
                    OutlinedTextField(bank, { bank = it }, label = { Text("Bank (as in alerts, e.g. HDFC)") }, singleLine = true)
                    OutlinedTextField(tail, { tail = it.filter(Char::isDigit).take(4) }, label = { Text("Last 4 digits") }, singleLine = true)
                }
                AmountField(limit, { limit = it }, "Credit limit (₹)")
                OutlinedTextField(billing, { billing = it.filter(Char::isDigit).take(2) }, label = { Text("Statement day (1-28)") }, singleLine = true)
                OutlinedTextField(dueAfter, { dueAfter = it.filter(Char::isDigit).take(2) }, label = { Text("Due days after statement") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank() && (card != null || (bank.isNotBlank() && tail.length == 4)), onClick = {
                val b = card?.bank ?: bank.trim(); val t = card?.tail ?: tail
                onSave(CreditCard("$b|$t", name.trim(), b, t, BankSmsParser.toPaise(limit)?.takeIf { it > 0 }, billing.toIntOrNull()?.coerceIn(1, 28) ?: 1, dueAfter.toIntOrNull()?.coerceIn(1, 60) ?: 20))
            }) { Text("Save") }
        },
        dismissButton = { Row { if (card != null) TextButton({ onDelete(card.key) }) { Text("Remove") }; TextButton(onDismiss) { Text("Cancel") } } }
    )
}

// ======================================================================= Calendar
fun LazyListScope.calendarSection(state: HomeState, plan: PlanState) {
    item {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR); val month = cal.get(Calendar.MONTH); val todayDay = cal.get(Calendar.DAY_OF_MONTH)
        val dim = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDow = Calendar.getInstance().apply { set(year, month, 1) }.get(Calendar.DAY_OF_WEEK) - 1
        val byDay = plan.calendar.groupBy { it.day }
        SectionCard {
            SectionTitle(SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(cal.time), "salary day ${plan.salaryDay}")
            Row(Modifier.fillMaxWidth()) { listOf("S", "M", "T", "W", "T", "F", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            var day = 1 - firstDow
            while (day <= dim) {
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) {
                        val d = day++
                        Box(Modifier.weight(1f).padding(2.dp).height(44.dp), contentAlignment = Alignment.Center) {
                            if (d in 1..dim) {
                                val evs = byDay[d].orEmpty()
                                val bg = when {
                                    evs.any { it.type == "income" } -> Leaf.copy(alpha = 0.22f)
                                    evs.any { it.type == "emi" || it.type == "card" } -> Coral.copy(alpha = 0.22f)
                                    evs.isNotEmpty() -> Amber.copy(alpha = 0.22f)
                                    else -> Color.Transparent
                                }
                                Column(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(8.dp)).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$d", style = MaterialTheme.typography.labelMedium, fontWeight = if (d == todayDay) FontWeight.Bold else FontWeight.Normal, color = if (d == todayDay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    if (evs.isNotEmpty()) Text(compactRupees(evs.sumOf { it.amountPaise }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Dot(Leaf, "Income"); Dot(Coral, "EMI / card bill"); Dot(Amber, "SIP / bill") }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            SectionTitle("Coming up")
            val upcoming = plan.calendar.filter { it.day >= todayDay }.ifEmpty { plan.calendar }
            if (upcoming.isEmpty()) EmptyHint("Nothing scheduled yet. Salary, EMIs, SIPs and card bills appear here once detected.")
            upcoming.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.day}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(34.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(when (e.type) { "income" -> "Income"; "emi" -> "EMI"; "card" -> "Card statement"; "sip" -> "SIP"; else -> "Bill" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text((if (e.type == "income") "+" else "-") + formatRupees(e.amountPaise), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (e.type == "income") Leaf else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun Dot(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(c, CircleShape)); Spacer(Modifier.width(5.dp)); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

// ======================================================================= Goals, receivables, informal loans
fun LazyListScope.goalsSection(plan: PlanState, vm: MainViewModel) {
    item {
        var addGoal by remember { mutableStateOf(false) }
        var editGoal by remember { mutableStateOf<Goal?>(null) }
        SectionCard {
            SectionTitle("Savings goals", "New goal") { addGoal = true }
            if (plan.goals.isEmpty()) EmptyHint("Set a goal with a target and date. Add money to it as you save.")
            plan.goals.forEach { g ->
                Column(Modifier.fillMaxWidth().clickable { editGoal = g }.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.icon, style = MaterialTheme.typography.titleLarge); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            val left = (g.targetPaise - g.savedPaise).coerceAtLeast(0)
                            val monthsLeft = g.deadline?.let { ((it - System.currentTimeMillis()) / (30.4 * 86_400_000L)).toInt().coerceAtLeast(1) }
                            Text("${formatRupees(g.savedPaise)} of ${formatRupees(g.targetPaise)}" + (g.deadline?.let { " · by ${formatDay(it)}" } ?: "") + (monthsLeft?.let { " · ${formatRupees(left / it)}/month needed" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${(g.savedPaise * 100 / g.targetPaise.coerceAtLeast(1))}%", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { (g.savedPaise.toFloat() / g.targetPaise.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (addGoal || editGoal != null) GoalDialog(editGoal, { addGoal = false; editGoal = null }, { vm.saveGoal(it); addGoal = false; editGoal = null }, { vm.deleteGoal(it); editGoal = null })
    }
    item {
        var add by remember { mutableStateOf(false) }
        val pending = plan.receivables.filter { !it.received }
        SectionCard {
            SectionTitle("Money owed to you", "Add") { add = true }
            if (pending.isNotEmpty()) Text("${formatRupees(pending.sumOf { it.amountPaise })} pending from ${pending.size} ${if (pending.size == 1) "person" else "people"}", style = MaterialTheme.typography.bodyMedium)
            if (plan.receivables.isEmpty()) EmptyHint("Track money you lent. Mark it received when it comes back.")
            plan.receivables.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.from, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        val overdue = !r.received && r.dueDate != null && r.dueDate < System.currentTimeMillis()
                        Text(when { r.received -> "Received"; overdue -> "Overdue · was due ${formatDay(r.dueDate!!)}"; r.dueDate != null -> "Due ${formatDay(r.dueDate)}"; else -> "No due date" } + (r.notes?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = if (overdue) Coral else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatRupees(r.amountPaise), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (!r.received) TextButton({ vm.saveReceivable(r.copy(received = true)) }) { Text("Got it") } else TextButton({ vm.deleteReceivable(r.id) }) { Text("Clear") }
                }
            }
        }
        if (add) SimpleMoneyDialog("Who owes you", "Name", { add = false }) { name, amt, due, notes -> vm.saveReceivable(Receivable(from = name, amountPaise = amt, dueDate = due, notes = notes)); add = false }
    }
    item {
        var add by remember { mutableStateOf(false) }
        val open = plan.informalLoans.filter { !it.repaid }
        SectionCard {
            SectionTitle("Informal loans you took", "Add") { add = true }
            if (open.isNotEmpty()) Text("${formatRupees(open.sumOf { it.amountPaise })} still to repay", style = MaterialTheme.typography.bodyMedium)
            if (plan.informalLoans.isEmpty()) EmptyHint("Borrowed from a friend, family or an advance from work? Keep it here so it is not forgotten.")
            plan.informalLoans.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.lender, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text((if (l.repaid) "Repaid" else "Taken ${formatDay(l.takenDate)}" + (l.dueDate?.let { " · due ${formatDay(it)}" } ?: "")) + (l.notes?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatRupees(l.amountPaise), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (!l.repaid) TextButton({ vm.saveInformalLoan(l.copy(repaid = true)) }) { Text("Repaid") } else TextButton({ vm.deleteInformalLoan(l.id) }) { Text("Clear") }
                }
            }
        }
        if (add) SimpleMoneyDialog("Loan taken", "Lender", { add = false }) { name, amt, due, notes -> vm.saveInformalLoan(InformalLoan(lender = name, amountPaise = amt, takenDate = System.currentTimeMillis(), dueDate = due, notes = notes)); add = false }
    }
}

@Composable
private fun SimpleMoneyDialog(title: String, nameLabel: String, onDismiss: () -> Unit, onSave: (String, Long, Long?, String?) -> Unit) {
    var name by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var due by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(nameLabel) }, singleLine = true)
            AmountField(amount, { amount = it }, "Amount (₹)")
            DateField(due, { due = it }, "Due date, optional")
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, singleLine = true)
        } },
        confirmButton = { Button(enabled = name.isNotBlank() && (BankSmsParser.toPaise(amount) ?: 0) > 0 && (due.isBlank() || parseDate(due) != null), onClick = { onSave(name.trim(), BankSmsParser.toPaise(amount)!!, parseDate(due), notes.ifBlank { null }) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
private fun GoalDialog(goal: Goal?, onDismiss: () -> Unit, onSave: (Goal) -> Unit, onDelete: (Long) -> Unit) {
    var name by remember { mutableStateOf(goal?.name ?: "") }
    var icon by remember { mutableStateOf(goal?.icon ?: "🎯") }
    var target by remember { mutableStateOf(goal?.targetPaise?.let { (it / 100).toString() } ?: "") }
    var saved by remember { mutableStateOf(goal?.savedPaise?.let { (it / 100).toString() } ?: "0") }
    var deadline by remember { mutableStateOf(goal?.deadline?.let { SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(java.util.Date(it)) } ?: "") }
    var addAmt by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (goal == null) "New goal" else "Goal") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("🎯", "🏠", "🚗", "✈️", "🎓", "💍", "🛡️", "📱").forEach { e -> FilterChip(selected = icon == e, onClick = { icon = e }, label = { Text(e) }) } }
            OutlinedTextField(name, { name = it }, label = { Text("Goal") }, singleLine = true)
            AmountField(target, { target = it }, "Target (₹)")
            AmountField(saved, { saved = it }, "Saved so far (₹)")
            if (goal != null) AmountField(addAmt, { addAmt = it }, "Add to this goal now (₹)")
            DateField(deadline, { deadline = it }, "Target date, optional")
        } },
        confirmButton = { Button(enabled = name.isNotBlank() && (BankSmsParser.toPaise(target) ?: 0) > 0 && (deadline.isBlank() || parseDate(deadline) != null), onClick = {
            val s = (BankSmsParser.toPaise(saved) ?: 0) + (BankSmsParser.toPaise(addAmt) ?: 0)
            onSave(Goal(goal?.id ?: 0, name.trim(), icon, BankSmsParser.toPaise(target)!!, s, parseDate(deadline), null, goal?.createdAt ?: System.currentTimeMillis()))
        }) { Text("Save") } },
        dismissButton = { Row { if (goal != null) TextButton({ onDelete(goal.id) }) { Text("Delete") }; TextButton(onDismiss) { Text("Cancel") } } })
}

// ======================================================================= Discipline
@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.disciplineSection(plan: PlanState, vm: MainViewModel, onOpenSettings: () -> Unit) {
    val d = plan.discipline ?: return
    item {
        val tone = if (d.breaches.isEmpty()) Leaf else Coral
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).background(tone.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${d.cleanDays}", style = MaterialTheme.typography.headlineSmall, color = tone); Text("days", style = MaterialTheme.typography.labelSmall, color = tone) }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(if (d.breaches.isEmpty()) "Clean streak" else "${d.breaches.size} rule${if (d.breaches.size > 1) "s" else ""} broken this cycle", style = MaterialTheme.typography.titleMedium)
                    Text(if (plan.zero.isEmpty()) "Add zero-tolerance categories below to start a streak." else "Since the last spend in a zero-tolerance category.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    item {
        var pick by remember { mutableStateOf(false) }
        SectionCard {
            SectionTitle("Zero tolerance", "Add") { pick = true }
            Text("Categories you have decided to spend nothing on. Any payment breaks the streak.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (plan.zero.isEmpty()) EmptyHint("Nothing yet.")
            plan.zero.forEach { z ->
                val br = d.breaches.firstOrNull { it.first.category == z.category }
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryDot(z.category, 32); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(z.category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(if (br == null) "Clean this cycle" else "Broken ${br.second.size}× · ${formatRupees(br.second.sumOf { it.amountPaise })}", style = MaterialTheme.typography.bodySmall, color = if (br == null) Leaf else Coral)
                    }
                    TextButton({ vm.deleteZero(z.category) }) { Text("Remove") }
                }
            }
        }
        if (pick) CategoryPicker(Categories.expense.filter { c -> plan.zero.none { it.category == c } }, { pick = false }) { vm.saveZero(ZeroTolerance(it)); pick = false }
    }
    item {
        var pick by remember { mutableStateOf(false) }
        var limitFor by remember { mutableStateOf<String?>(null) }
        SectionCard {
            SectionTitle("Controlled categories", "Add") { pick = true }
            Text("A monthly ceiling per category. The bar fills as you spend.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (d.controlled.isEmpty()) EmptyHint("Nothing yet.")
            d.controlled.forEach { (rule, spent, share) ->
                Column(Modifier.fillMaxWidth().clickable { limitFor = rule.category }.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryDot(rule.category, 32); Spacer(Modifier.width(10.dp))
                        Text(rule.category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("${formatRupees(spent)} / ${formatRupees(rule.monthlyLimitPaise)}", style = MaterialTheme.typography.bodySmall, color = if (share > 1f) Coral else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { share.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = if (share > 1f) Coral else if (share > 0.8f) Amber else Leaf)
                }
            }
        }
        if (pick) CategoryPicker(Categories.expense.filter { c -> plan.controlled.none { it.category == c } }, { pick = false }) { limitFor = it; pick = false }
        val lf = limitFor
        if (lf != null) {
            var limit by remember(lf) { mutableStateOf(plan.controlled.firstOrNull { it.category == lf }?.monthlyLimitPaise?.let { (it / 100).toString() } ?: "") }
            AlertDialog(onDismissRequest = { limitFor = null }, title = { Text(lf) }, text = { AmountField(limit, { limit = it }, "Monthly limit (₹)") },
                confirmButton = { Button(enabled = (BankSmsParser.toPaise(limit) ?: 0) > 0, onClick = { vm.saveControlled(ControlledCategory(lf, BankSmsParser.toPaise(limit)!!)); limitFor = null }) { Text("Save") } },
                dismissButton = { Row { TextButton({ vm.deleteControlled(lf); limitFor = null }) { Text("Remove") }; TextButton({ limitFor = null }) { Text("Cancel") } } })
        }
    }
    item {
        // Opportunity cost: what the wasted money could have been.
        val name = plan.daughterName.ifBlank { "your family" }
        val wasted = d.wastedPaise + d.regretPaise
        SectionCard {
            SectionTitle("What it could have been")
            if (wasted <= 0) Text("No wasted spend logged this cycle. Keep it that way.", style = MaterialTheme.typography.bodyMedium)
            else {
                Text("${formatRupees(wasted)} went to things you said you would not buy, or regretted.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatPill("Meals for $name", "${wasted / 15_000}", Amber, Modifier.weight(1f))
                    StatPill("School books", "${wasted / 40_000}", Amber, Modifier.weight(1f))
                    StatPill("Months of SIP", "%.1f".format(wasted / 500_000.0), Teal, Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Text("Invested at 12% for 15 years, that same ${formatRupees(wasted)} would be about ${formatRupees((wasted * 5.47).toLong())}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (plan.daughterName.isBlank()) TextButton(onClick = onOpenSettings) { Text("Set a name to make this personal") }
        }
    }
    item {
        var fine by remember { mutableStateOf(false) }; var regret by remember { mutableStateOf(false) }
        SectionCard {
            SectionTitle("Self-fine jar & regret log")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("Fine jar", compactRupees(d.fineJarPaise), Coral, Modifier.weight(1f))
                StatPill("Regretted", compactRupees(d.regretPaise), Amber, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("Broke a rule? Fine yourself and move that money to a goal. Bought something you regret? Log it so the pattern shows.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button({ fine = true }) { Text("Add fine") }; OutlinedButton({ regret = true }) { Text("Log regret") } }
            plan.entries.take(6).forEach { e ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(e.reason.ifBlank { if (e.kind == "FINE") "Fine" else "Regret" }, style = MaterialTheme.typography.bodyMedium); Text(formatDay(e.at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(formatRupees(e.amountPaise), style = MaterialTheme.typography.bodyMedium)
                    TextButton({ vm.deleteDisciplineEntry(e.id) }) { Text("×") }
                }
            }
        }
        if (fine || regret) {
            var amt by remember { mutableStateOf("") }; var why by remember { mutableStateOf("") }
            val kind = if (fine) "FINE" else "REGRET"
            AlertDialog(onDismissRequest = { fine = false; regret = false }, title = { Text(if (fine) "Self-fine" else "Regret") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { AmountField(amt, { amt = it }, "Amount (₹)"); OutlinedTextField(why, { why = it }, label = { Text("What happened") }, singleLine = true) } },
                confirmButton = { Button(enabled = (BankSmsParser.toPaise(amt) ?: 0) > 0, onClick = { vm.addDisciplineEntry(kind, BankSmsParser.toPaise(amt)!!, why.trim()); fine = false; regret = false }) { Text("Save") } },
                dismissButton = { TextButton({ fine = false; regret = false }) { Text("Cancel") } })
        }
    }
    item {
        var urge by remember { mutableStateOf(false) }
        SectionCard {
            SectionTitle("About to buy something?")
            Text("Start a 10-minute pause. Most impulse buys do not survive it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (!urge) Button({ urge = true }) { Text("Start the 10-minute pause") } else UrgeTimer { urge = false }
        }
    }
}

@Composable
private fun UrgeTimer(onDone: () -> Unit) {
    var secs by remember { mutableStateOf(600) }
    androidx.compose.runtime.LaunchedEffect(Unit) { while (secs > 0) { kotlinx.coroutines.delay(1000); secs-- } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("%02d:%02d".format(secs / 60, secs % 60), style = MaterialTheme.typography.displaySmall, color = if (secs == 0) Leaf else Coral)
        LinearProgressIndicator(progress = { 1f - secs / 600f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Text(if (secs == 0) "Still want it? Then it was not an impulse. Decide with a clear head." else "Ask: will I still want this next week? Can it wait for the next cycle?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onDone) { Text("I will skip it") }; if (secs == 0) Button(onDone) { Text("Buying anyway") } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(options: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose a category") },
        text = { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { c -> FilterChip(selected = false, onClick = { onPick(c) }, label = { Text(c) }) } } },
        confirmButton = {}, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
