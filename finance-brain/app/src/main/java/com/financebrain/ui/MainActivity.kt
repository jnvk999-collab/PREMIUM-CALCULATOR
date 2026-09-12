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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.financebrain.data.Transaction
import com.financebrain.ui.screens.AddTransactionSheet
import com.financebrain.ui.screens.BrainScreen
import com.financebrain.ui.screens.HomeScreen
import com.financebrain.ui.screens.InsightsScreen
import com.financebrain.ui.screens.SettingsScreen
import com.financebrain.ui.screens.SetBalanceSheet
import com.financebrain.ui.screens.TransactionDetailSheet
import com.financebrain.ui.screens.TransactionsScreen
import com.financebrain.ui.theme.FinanceBrainTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FinanceBrainTheme { App(vm) } }
    }
}

private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("Home", Icons.Default.Home),
    Transactions("Activity", Icons.Default.ReceiptLong),
    Brain("Brain", Icons.Default.Psychology),
    Insights("Money", Icons.Default.Insights),
    Settings("Settings", Icons.Default.Settings),
}

private val SMS_PERMISSIONS = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

@Composable
private fun App(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun granted() = SMS_PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    val activity = context as ComponentActivity
    var smsGranted by remember { mutableStateOf(granted()) }
    var onboarded by rememberSaveable { mutableStateOf(granted()) }
    // Android shows the SMS dialog at most twice. After that every request is denied silently,
    // so we send the user to the app's permission page instead.
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

    // Re-check when returning from system settings.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val g = granted()
                if (g != smsGranted) smsGranted = g
                if (g) onboarded = true
            }
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
    val plan by vm.plan.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    var moneySection by rememberSaveable { mutableStateOf(0) }
    val app = context.applicationContext as com.financebrain.FinanceBrainApp
    var alertEnabled by remember { mutableStateOf(app.dailyAlertEnabled) }
    var alertHour by remember { mutableStateOf(app.dailyAlertHour) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) { vm.setDailyAlert(true, alertHour); alertEnabled = true } }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.exportBackup(uri) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.restoreBackup(uri) }
    var csvBank by remember { mutableStateOf<String?>(null) }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importCsv(uri, csvBank ?: "Statement") }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(toast) { toast?.let { snackbar.showSnackbar(it); vm.clearToast() } }
    val ignoredBanks by vm.ignoredBanks.collectAsStateWithLifecycle()
    val gmailLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.finishGmailSignIn(it.data) }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var selected by remember { mutableStateOf<Transaction?>(null) }
    var adding by remember { mutableStateOf(false) }
    var settingBalance by remember { mutableStateOf(false) }

    // Keep the open sheet in sync with edits.
    val live = selected?.let { s -> state.allTransactions.firstOrNull { it.id == s.id } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, t.label) }, label = { Text(t.label) })
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, "Add transaction", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        when (tab) {
            Tab.Home -> HomeScreen(state, scan, padding, update, vm::downloadUpdate, vm::installUpdate, vm::dismissUpdate, vm::shiftMonth, { selected = it }, { tab = Tab.Transactions }, { tab = Tab.Brain }, { settingBalance = true }, plan.allocation, { moneySection = 2; tab = Tab.Insights })
            Tab.Brain -> BrainScreen(state.report, state.month, chat, hasApiKey, padding, vm::ask) { tab = Tab.Settings }
            Tab.Transactions -> TransactionsScreen(state.allTransactions, padding) { selected = it }
            Tab.Insights -> InsightsScreen(state, plan, vm, padding, moneySection) { tab = Tab.Settings }
            Tab.Settings -> SettingsScreen(state, scan, smsGranted, padding, { requestSms() }, { openAppSettings() }, { full -> vm.scanInbox(full) }, update, vm::checkForUpdate, vm::downloadUpdate, vm::installUpdate,
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
                { csvBank = "Statement"; csvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) })
        }
    }

    if (live != null) TransactionDetailSheet(
        t = live,
        onDismiss = { selected = null },
        onCategory = { c, remember -> vm.setCategory(live, c, remember) },
        onNote = { vm.setNote(live, it) },
        onDelete = { vm.delete(live); selected = null },
        onSpam = { vm.markSpam(live); selected = null },
    )

    if (settingBalance) SetBalanceSheet(
        accounts = state.accounts, anchors = state.anchors,
        onDismiss = { settingBalance = false },
        onSave = { key, paise -> vm.setBalance(key, paise, System.currentTimeMillis()); settingBalance = false },
        onClear = { key -> vm.clearBalance(key); settingBalance = false },
    )

    if (adding) AddTransactionSheet(
        onDismiss = { adding = false },
        onSave = { amount, dir, name, cat, note ->
            vm.addManual(amount, dir, name, cat, System.currentTimeMillis(), note); adding = false
        }
    )
}

@Composable
private fun Onboarding(onAllow: () -> Unit, onOpenSettings: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Sms, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(24.dp))
        Text("Let Finance Brain read your bank alerts", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "It reads only bank and UPI alert messages from SBI, HDFC, ICICI, Federal Bank and PhonePe, including your past history, and builds your ledger automatically. Nothing is uploaded.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Allow SMS access") }
        TextButton(onClick = onOpenSettings) { Text("No dialog? Open app settings") }
        Text(
            "Installed from a file? In App info tap the ⋮ menu → Allow restricted settings, then Permissions → SMS → Allow.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        TextButton(onClick = onSkip) { Text("Skip for now") }
    }
}
