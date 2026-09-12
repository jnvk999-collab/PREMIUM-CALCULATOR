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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financebrain.sms.ScanProgress
import com.financebrain.ui.HomeState
import com.financebrain.ui.components.SectionCard
import com.financebrain.ui.components.SectionTitle

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: HomeState,
    scan: ScanProgress?,
    smsGranted: Boolean,
    padding: PaddingValues,
    onRequestSms: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRescan: (full: Boolean) -> Unit,
    update: com.financebrain.update.UpdateState,
    onCheckUpdate: () -> Unit,
    onUpdateDownload: () -> Unit,
    onUpdateInstall: () -> Unit,
    gmail: List<com.financebrain.gmail.GmailAccount>,
    gmailProgress: com.financebrain.gmail.GmailSyncProgress?,
    gmailClientId: String,
    gmailError: String?,
    onGmailClientId: (String) -> Unit,
    onGmailAdd: () -> Unit,
    onGmailSync: () -> Unit,
    onGmailRemove: (String) -> Unit,
    hasApiKey: Boolean = false,
    onApiKey: (String) -> Unit = {},
    knownBanks: List<String> = emptyList(),
    ignoredBanks: Set<String> = emptySet(),
    onBankIgnored: (String, Boolean) -> Unit = { _, _ -> },
    plan: com.financebrain.ui.PlanState = com.financebrain.ui.PlanState(),
    onSalaryDay: (Int) -> Unit = {},
    onInvestPct: (Int) -> Unit = {},
    onBudget: (Long) -> Unit = {},
    onDaughter: (String) -> Unit = {},
    alertEnabled: Boolean = false, alertHour: Int = 21,
    onDailyAlert: (Boolean, Int) -> Unit = { _, _ -> },
    onBackup: () -> Unit = {}, onRestore: () -> Unit = {}, onImportCsv: () -> Unit = {},
    accountRefs: List<com.financebrain.data.AccountRef> = emptyList(),
    ignoredAccounts: Set<String> = emptySet(),
    onAccountIgnored: (String, Boolean) -> Unit = { _, _ -> },
    trackingStart: Long = 0,
    onTrackingStart: (Long) -> Unit = {},
    onExpectedIncome: (Long) -> Unit = {},
    uncounted: List<com.financebrain.sms.UncountedSms> = emptyList(),
    onLoadUncounted: () -> Unit = {},
    onCountUncounted: (com.financebrain.sms.UncountedSms, com.financebrain.data.Direction) -> Unit = { _, _ -> },
    onBatteryExemption: () -> Unit = {},
    batteryExempt: Boolean = false,
) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onLoadUncounted() }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall) }
        item {
            SectionCard {
                SectionTitle("Count from")
                val fmt = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ENGLISH)
                var d by androidx.compose.runtime.remember(trackingStart) { androidx.compose.runtime.mutableStateOf(if (trackingStart > 0) fmt.format(java.util.Date(trackingStart)) else "") }
                Text("Income, spending and the plan count only from this date. Older messages stay in Activity for reference. The balance you enter is the starting point.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(d, { d = it }, label = { Text("dd/mm/yyyy") }, singleLine = true, modifier = Modifier.weight(1f))
                    Button(onClick = { try { fmt.parse(d)?.let { onTrackingStart(it.time) } } catch (_: Exception) {} }) { Text("Save") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onTrackingStart(java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0) }.timeInMillis) }) { Text("From today") }
                    OutlinedButton(onClick = { onTrackingStart(com.financebrain.ui.monthStart(System.currentTimeMillis())) }) { Text("From this month") }
                    OutlinedButton(onClick = { onTrackingStart(0L) }) { Text("All history") }
                }
            }
        }
        item {
            SectionCard {
                SectionTitle("Salary cycle & plan")
                var sd by androidx.compose.runtime.remember(plan.salaryDay) { androidx.compose.runtime.mutableStateOf(plan.salaryDay.toString()) }
                var pct by androidx.compose.runtime.remember(plan.investPct) { androidx.compose.runtime.mutableStateOf(plan.investPct.toString()) }
                var budget by androidx.compose.runtime.remember(plan.budgetPaise) { androidx.compose.runtime.mutableStateOf(if (plan.budgetPaise > 0) (plan.budgetPaise / 100).toString() else "") }
                var name by androidx.compose.runtime.remember(plan.daughterName) { androidx.compose.runtime.mutableStateOf(plan.daughterName) }
                var inc by androidx.compose.runtime.remember(plan.expectedIncomePaise) { androidx.compose.runtime.mutableStateOf(if (plan.expectedIncomePaise > 0) (plan.expectedIncomePaise / 100).toString() else "") }
                Text("The app's month starts on your salary day. With day 1 it is the calendar month.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.OutlinedTextField(sd, { sd = it.filter(Char::isDigit).take(2) }, label = { Text("Salary day (1-28)") }, singleLine = true, modifier = Modifier.weight(1f))
                    androidx.compose.material3.OutlinedTextField(pct, { pct = it.filter(Char::isDigit).take(2) }, label = { Text("Invest target %") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(inc, { v -> if (v.all(Char::isDigit)) inc = v }, label = { Text("Monthly income (₹), used until a salary credit is seen") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(budget, { v -> if (v.all(Char::isDigit)) budget = v }, label = { Text("Spending budget per month (₹), optional") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(name, { name = it }, label = { Text("Name for the 'what it could have been' view (e.g. your daughter)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    sd.toIntOrNull()?.let { if (it in 1..28) onSalaryDay(it) }
                    pct.toIntOrNull()?.let { if (it in 0..80) onInvestPct(it) }
                    onBudget((budget.toLongOrNull() ?: 0L) * 100)
                    onExpectedIncome((inc.toLongOrNull() ?: 0L) * 100)
                    onDaughter(name)
                }) { Text("Save") }
            }
        }
        item {
            SectionCard {
                SectionTitle("Evening spend alert")
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily summary at ${"%02d:00".format(alertHour)}", style = MaterialTheme.typography.bodyLarge)
                        Text("What went out today and what is left for the cycle.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(checked = alertEnabled, onCheckedChange = { onDailyAlert(it, alertHour) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(20, 21, 22).forEach { h -> androidx.compose.material3.FilterChip(selected = alertHour == h, onClick = { onDailyAlert(alertEnabled, h) }, label = { Text("%02d:00".format(h)) }) }
                }
            }
        }
        item {
            SectionCard {
                SectionTitle("Backup & import")
                Text("Backup writes everything to a file you choose (transactions, cards, goals, rules, settings). Restore merges it back. CSV import reads a bank statement export.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onBackup) { Text("Backup") }
                    OutlinedButton(onClick = onRestore) { Text("Restore") }
                    OutlinedButton(onClick = onImportCsv) { Text("Import CSV") }
                }
            }
        }
        item {
            SectionCard {
                SectionTitle("Automatic tracking")
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Bank SMS alerts", style = MaterialTheme.typography.bodyLarge)
                        Text(if (smsGranted) "On · new alerts are added instantly" else "Permission needed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!smsGranted) Button(onClick = onRequestSms) { Text("Allow") }
                }
                if (!smsGranted) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "If Allow is greyed out or does nothing: open App info → tap the ⋮ menu (top right) → Allow restricted settings → then Permissions → SMS → Allow. Android requires this once for apps installed outside the Play Store.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenAppSettings) { Text("Open app settings") }
                }
            }
        }
        item {
            SectionCard {
                SectionTitle("Gmail sync")
                val baked = com.financebrain.BuildConfig.GMAIL_CLIENT_ID.isNotBlank()
                if (!baked) {
                    Text(
                        "This build has no Google client configured. Add the FINANCE_BRAIN_GMAIL_CLIENT_ID secret in GitHub and the next update will enable Gmail sync.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (gmailClientId.isBlank()) {
                    var draft by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                    Text(
                        "Google requires a one-time OAuth client for apps that read Gmail. Create it under your Google account (see README), then paste the Android Client ID here.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = draft, onValueChange = { draft = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Client ID (…apps.googleusercontent.com)") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onGmailClientId(draft) }, enabled = draft.contains("apps.googleusercontent.com")) { Text("Save") }
                } else {
                    if (gmail.isEmpty()) Text("No accounts connected yet. Bank, card, UPI and order emails will be read and matched against your SMS transactions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    gmail.forEach { a ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.email, style = MaterialTheme.typography.bodyLarge)
                                val p = gmailProgress
                                val status = when {
                                    p != null && p.email == a.email && !p.done -> "Syncing… ${p.fetched} read, ${p.imported} new"
                                    a.lastError != null -> "Error: ${a.lastError}"
                                    a.lastSyncAt == 0L -> "Not synced yet"
                                    else -> "${a.imported} imported · last sync ${com.financebrain.ui.formatDay(a.lastSyncAt)} ${com.financebrain.ui.formatTime(a.lastSyncAt)}" + if (a.historyComplete) "" else " · history in progress"
                                }
                                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            androidx.compose.material3.TextButton(onClick = { onGmailRemove(a.email) }) { Text("Remove") }
                        }
                    }
                    if (gmailError != null) Text(gmailError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onGmailAdd) { Text(if (gmail.isEmpty()) "Connect Gmail" else "Add account") }
                        if (gmail.isNotEmpty()) OutlinedButton(onClick = onGmailSync, enabled = gmailProgress?.done != false) { Text("Sync now") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Syncs automatically every 4 hours on Wi-Fi or data. Read-only access; emails stay on this phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                }
            }
        }
        item {
            SectionCard {
                SectionTitle("Keep running in background")
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (batteryExempt) "Allowed" else "Ask Android to allow it", style = MaterialTheme.typography.bodyLarge)
                        Text("Some phones stop apps from reading new messages in the background. Allowing this keeps alerts flowing. The app also re-checks the inbox every 30 minutes and each time it opens.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!batteryExempt) Button(onClick = onBatteryExemption) { Text("Allow") }
                }
            }
        }
        item {
            SectionCard {
                SectionTitle("Bank messages not counted · last 7 days", "Refresh", onLoadUncounted)
                if (uncounted.isEmpty()) Text("Every bank message from the last week was understood.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                uncounted.forEach { u ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text("${u.sender} · ${com.financebrain.ui.formatDay(u.at)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(u.body, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                        if (u.amountPaise != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            androidx.compose.material3.TextButton(onClick = { onCountUncounted(u, com.financebrain.data.Direction.DEBIT) }) { Text("Count as spend ${com.financebrain.ui.formatRupees(u.amountPaise)}") }
                            androidx.compose.material3.TextButton(onClick = { onCountUncounted(u, com.financebrain.data.Direction.CREDIT) }) { Text("as income") }
                        }
                    }
                }
            }
        }
        item {
            SectionCard {
                SectionTitle("SMS history")
                Text("${state.totalCount} transactions · ${state.accounts.size} accounts", style = MaterialTheme.typography.bodyMedium)
                if (scan != null && !scan.done) Text("Scanning ${scan.scanned}/${scan.total}…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { onRescan(false) }, enabled = smsGranted && scan?.done != false) { Text("Scan new messages") }
                    OutlinedButton(onClick = { onRescan(true) }, enabled = smsGranted && scan?.done != false) { Text("Full rescan") }
                }
                Spacer(Modifier.height(6.dp))
                Text("Full rescan rebuilds everything from SMS with the latest parser. Cash entries and your category corrections are kept.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard {
                SectionTitle("Ask Finance Brain")
                var key by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                Text(
                    if (hasApiKey) "Connected. Ask questions in the Brain tab." else "Questions in plain English are answered by Claude using your data. Get an API key at console.anthropic.com → API keys, paste it here. It is stored encrypted on this phone and used only for your questions.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = key, onValueChange = { key = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (hasApiKey) "Replace API key" else "Anthropic API key (sk-ant-…)") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { onApiKey(key); key = "" }, enabled = key.startsWith("sk-ant-")) { Text("Save key") }
                    if (hasApiKey) OutlinedButton(onClick = { onApiKey("") }) { Text("Remove") }
                }
            }
        }
        item {
            SectionCard {
                SectionTitle("App updates")
                Text("Installed version ${com.financebrain.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                val status = when (update) {
                    is com.financebrain.update.UpdateState.Checking -> "Checking…"
                    is com.financebrain.update.UpdateState.UpToDate -> "You have the latest build."
                    is com.financebrain.update.UpdateState.Available -> "Version ${update.update.versionName} is available."
                    is com.financebrain.update.UpdateState.Downloading -> "Downloading ${(update.progress * 100).toInt()}%"
                    is com.financebrain.update.UpdateState.ReadyToInstall -> "Downloaded. Tap Install."
                    is com.financebrain.update.UpdateState.Failed -> "Check failed: ${update.message}"
                    else -> "New builds are published automatically with every code change."
                }
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (update) {
                        is com.financebrain.update.UpdateState.Available -> Button(onClick = onUpdateDownload) { Text("Download update") }
                        is com.financebrain.update.UpdateState.ReadyToInstall -> Button(onClick = onUpdateInstall) { Text("Install") }
                        else -> OutlinedButton(onClick = onCheckUpdate, enabled = update !is com.financebrain.update.UpdateState.Checking && update !is com.financebrain.update.UpdateState.Downloading) { Text("Check for updates") }
                    }
                }
            }
        }
        item {
            SectionCard {
                SectionTitle("Accounts")
                Text("Every account and card seen in your messages. Switch off any that are not yours or that you do not want tracked; its entries are removed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                val seeded = ignoredAccounts.filter { k -> accountRefs.none { it.key == k } }.map { k -> com.financebrain.data.AccountRef(k.substringBefore('|'), k.substringAfter('|'), "BANK") }
                val all = (accountRefs + seeded).distinctBy { it.key }.sortedBy { it.label }
                if (all.isEmpty()) Text("Nothing yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    all.forEach { a ->
                        val off = a.key in ignoredAccounts
                        androidx.compose.material3.FilterChip(selected = off, onClick = { onAccountIgnored(a.key, !off) }, label = { Text(if (off) "✕ ${a.label}" else a.label) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Selected = ignored. Re-enable and run Full rescan to bring one back.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard {
                SectionTitle("Banks to ignore")
                Text("Messages from these are dropped and their entries removed. Useful for accounts that are not yours or that you track elsewhere.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                val all = (knownBanks + ignoredBanks + listOf("Union Bank")).distinct().sorted()
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    all.forEach { b ->
                        androidx.compose.material3.FilterChip(
                            selected = b in ignoredBanks, onClick = { onBankIgnored(b, b !in ignoredBanks) },
                            label = { Text(if (b in ignoredBanks) "✕ $b" else b) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Selected = ignored. Tap a bank to toggle. Re-enable and run Full rescan to bring it back.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard {
                SectionTitle("Supported banks")
                Text("SBI · HDFC · ICICI · Federal Bank · PhonePe · Axis · Kotak · Paytm", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text("Raw messages never leave this phone. Everything is stored in a local database.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
