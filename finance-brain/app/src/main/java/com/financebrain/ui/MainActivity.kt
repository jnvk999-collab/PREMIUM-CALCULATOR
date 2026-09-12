package com.financebrain.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.financebrain.data.Transaction
import com.financebrain.ui.screens.AddTransactionSheet
import com.financebrain.ui.screens.BrainScreen
import com.financebrain.ui.screens.HomeScreen
import com.financebrain.ui.screens.InsightsScreen
import com.financebrain.ui.screens.SetBalanceSheet
import com.financebrain.ui.screens.SettingsScreen
import com.financebrain.ui.screens.TransactionDetailSheet
import com.financebrain.ui.screens.TransactionsScreen
import com.financebrain.ui.theme.FinanceBrainTheme
import com.financebrain.ui.theme.P

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FinanceBrainTheme { App(vm) } }
    }
}

/** Five tabs. Each is one kind of thing, in lists. Settings lives behind the gear on Home. */
private data class Tab(val key: String, val emoji: String, val label: String)
private val TABS = listOf(Tab("home", "🏠", "Home"), Tab("spend", "💸", "Spend"), Tab("money", "💼", "Money"), Tab("plan", "📅", "Plan"), Tab("brain", "🧠", "Brain"))

private val SMS_PERMISSIONS = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

@Composable
private fun App(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val p = P
    fun granted() = SMS_PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    val activity = context as ComponentActivity
    var smsGranted by remember { mutableStateOf(granted()) }
    var onboarded by rememberSaveable { mutableStateOf(granted()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    fun openAppSettings() {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        smsGranted = granted()
        if (smsGranted) { onboarded = true; vm.scanInbox() }
        else {
            val canAskAgain = SMS_PERMISSIONS.any { activity.shouldShowRequestPermissionRationale(it) }
            if (askedOnce && !canAskAgain) openAppSettings()
            askedOnce = true
        }
    }
    fun requestSms() {
        val blocked = askedOnce && SMS_PERMISSIONS.none { activity.shouldShowRequestPermissionRationale(it) }
        if (blocked) openAppSettings() else launcher.launch(SMS_PERMISSIONS)
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { val g = granted(); if (g != smsGranted) smsGranted = g; if (g) onboarded = true }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(smsGranted) { if (smsGranted) vm.scanInbox() }

    if (!onboarded) {
        Onboarding(onAllow = { requestSms() }, onOpenSettings = { openAppSettings() }, onSkip = { onboarded = true })
        return
    }

    val state by vm.state.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val gmail by vm.gmail.collectAsStateWithLifecycle()
    val gmailProgress by vm.gmailProgress.collectAsStateWithLifecycle()
    val gmailClientId by vm.gmailClientId.collectAsStateWithLifecycle()
    val gmailError by vm.gmailError.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val hasApiKey by vm.hasApiKey.collectAsStateWithLifecycle()
    val knownBanks by vm.knownBanks.collectAsStateWithLifecycle()
    val ignoredBanks by vm.ignoredBanks.collectAsStateWithLifecycle()
    val accountRefs by vm.accountRefs.collectAsStateWithLifecycle()
    val ignoredAccounts by vm.ignoredAccounts.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val gmailLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.finishGmailSignIn(it.data) }

    var tab by rememberSaveable { mutableStateOf("home") }
    var spendSection by rememberSaveable { mutableStateOf("activity") }
    var moneySection by rememberSaveable { mutableStateOf("accounts") }
    var planSection by rememberSaveable { mutableStateOf("budget") }
    var selected by remember { mutableStateOf<Transaction?>(null) }
    var adding by remember { mutableStateOf(false) }
    var settingBalance by remember { mutableStateOf(false) }
    var balanceTarget by remember { mutableStateOf<String?>(null) }
    val app = context.applicationContext as com.financebrain.FinanceBrainApp
    var alertEnabled by remember { mutableStateOf(app.dailyAlertEnabled) }
    var alertHour by remember { mutableStateOf(app.dailyAlertHour) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g -> if (g) { vm.setDailyAlert(true, alertHour); alertEnabled = true } }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.exportBackup(uri) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.restoreBackup(uri) }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importCsv(uri, "Statement") }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(toast) { toast?.let { snackbar.showSnackbar(it); vm.clearToast() } }

    val live = selected?.let { s -> state.allTransactions.firstOrNull { it.id == s.id } }
    fun open(key: String) {
        val (t, sec) = key.split(":").let { it[0] to it.getOrNull(1) }
        when (t) { "spend" -> sec?.let { spendSection = it }; "money" -> sec?.let { moneySection = it }; "plan" -> sec?.let { planSection = it } }
        tab = t
    }
    fun setBalanceFor(target: String?) { balanceTarget = target; settingBalance = true }
    fun review(r: com.financebrain.data.ReviewItem, answer: String) {
        when (r) {
            is com.financebrain.data.ReviewItem.BigUnknown -> when (answer) {
                "spend" -> selected = r.t
                "transfer" -> vm.markTransfer(r.t)
                "investment" -> vm.markInvestment(r.t)
                "spam" -> vm.markSpam(r.t)
            }
            is com.financebrain.data.ReviewItem.BigCredit -> when (answer) {
                "income" -> vm.setCategory(r.t, com.financebrain.data.Categories.INCOME, false)
                "salary" -> vm.confirmSalary(r.t)
                "transfer" -> vm.markTransfer(r.t)
                "spam" -> vm.markSpam(r.t)
            }
            is com.financebrain.data.ReviewItem.ConfirmSalary -> if (answer == "yes") vm.confirmSalary(r.t) else vm.dismissReview(r.id)
            else -> vm.dismissReview(r.id)
        }
    }

    Scaffold(
        containerColor = p.bg,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.background(p.s1)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.bd))
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TABS.forEach { t ->
                        val sel = tab == t.key || (tab == "settings" && t.key == "home")
                        Column(
                            Modifier.clickable { tab = t.key }.background(if (sel) p.gold.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(t.emoji, style = MaterialTheme.typography.bodyLarge)
                            Text(t.label, style = MaterialTheme.typography.labelSmall, color = if (sel) p.gold else p.t2)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }, containerColor = p.gold, contentColor = androidx.compose.ui.graphics.Color(0xFF1A1200)) {
                Icon(Icons.Default.Add, "Add transaction")
            }
        }
    ) { padding ->
        when (tab) {
            "home" -> HomeScreen(state, plan, scan, padding, update, vm::downloadUpdate, vm::installUpdate, vm::dismissUpdate, vm::shiftMonth, { selected = it }, ::open, ::setBalanceFor, ::review)
            "spend" -> com.financebrain.ui.screens.SpendScreen(state, padding, spendSection, { spendSection = it }) { selected = it }
            "money" -> com.financebrain.ui.screens.MoneyScreen(state, plan, vm, padding, moneySection, { moneySection = it }, ::setBalanceFor)
            "plan" -> com.financebrain.ui.screens.PlanScreen(state, plan, vm, padding, planSection, { planSection = it }) { tab = "settings" }
            "brain" -> BrainScreen(state.report, state.month, chat, hasApiKey, padding, plan.wealthSections, plan.wealthActions, vm::ask) { tab = "settings" }
            "settings" -> SettingsScreen(state, scan, smsGranted, padding, { requestSms() }, { openAppSettings() }, { full -> vm.scanInbox(full) }, update, vm::checkForUpdate, vm::downloadUpdate, vm::installUpdate,
                gmail, gmailProgress, gmailClientId, gmailError, vm::setGmailClientId,
                { vm.clearGmailError(); gmailLauncher.launch(vm.gmailAuth.signInIntent(gmailClientId)) }, vm::syncGmail, vm::removeGmail,
                hasApiKey, vm::setApiKey, knownBanks, ignoredBanks, vm::setBankIgnored,
                plan, vm::setSalaryDay, vm::setInvestPct, vm::setBudget, vm::setDaughterName,
                alertEnabled, alertHour,
                { on, h ->
                    alertHour = h
                    if (on && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else { vm.setDailyAlert(on, h); alertEnabled = on }
                },
                { backupLauncher.launch("finance-brain-backup-${java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ENGLISH).format(java.util.Date())}.json") },
                { restoreLauncher.launch(arrayOf("application/json", "*/*")) },
                { csvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) },
                accountRefs, ignoredAccounts, vm::setAccountIgnored, state.trackingStart, vm::setTrackingStart)
            else -> HomeScreen(state, plan, scan, padding, update, vm::downloadUpdate, vm::installUpdate, vm::dismissUpdate, vm::shiftMonth, { selected = it }, ::open, ::setBalanceFor, ::review)
        }
    }

    if (live != null) TransactionDetailSheet(
        t = live, onDismiss = { selected = null },
        onCategory = { c, remember -> vm.setCategory(live, c, remember) },
        onNote = { vm.setNote(live, it) },
        onDelete = { vm.delete(live); selected = null },
        onSpam = { vm.markSpam(live); selected = null },
    )
    if (settingBalance) SetBalanceSheet(
        accounts = state.accounts, accountViews = state.accountList, anchors = state.anchors,
        onDismiss = { settingBalance = false },
        onSave = { key, paise -> vm.setBalance(key, paise, System.currentTimeMillis()); settingBalance = false },
        onClear = { key -> vm.clearBalance(key); settingBalance = false },
        initialTarget = balanceTarget,
    )
    if (adding) AddTransactionSheet(
        onDismiss = { adding = false },
        onSave = { amount, dir, name, cat, note -> vm.addManual(amount, dir, name, cat, System.currentTimeMillis(), note); adding = false }
    )
}

@Composable
private fun Onboarding(onAllow: () -> Unit, onOpenSettings: () -> Unit, onSkip: () -> Unit) {
    val p = P
    Column(Modifier.fillMaxSize().background(p.bg).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Sms, null, tint = p.gold, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(24.dp))
        Text("Let Finance Brain read your bank alerts", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = p.t1)
        Spacer(Modifier.height(12.dp))
        Text("It reads only bank, card, UPI and investment alerts, including your past history, and builds your ledger automatically. Nothing is uploaded.",
            style = MaterialTheme.typography.bodyLarge, color = p.t2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Allow SMS access") }
        TextButton(onClick = onOpenSettings) { Text("No dialog? Open app settings") }
        Text("Installed from a file? In App info tap the ⋮ menu → Allow restricted settings, then Permissions → SMS → Allow.", style = MaterialTheme.typography.bodySmall, color = p.t2, textAlign = TextAlign.Center)
        TextButton(onClick = onSkip) { Text("Skip for now") }
    }
}
