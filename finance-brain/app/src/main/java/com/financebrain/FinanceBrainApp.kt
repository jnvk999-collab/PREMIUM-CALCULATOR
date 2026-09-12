package com.financebrain

import android.app.Application
import android.content.Context
import com.financebrain.data.AppDatabase
import com.financebrain.data.TransactionRepository
import com.financebrain.gmail.GmailAccounts
import com.financebrain.gmail.GmailSyncWorker
import com.financebrain.gmail.GmailSyncer
import kotlinx.coroutines.launch

class FinanceBrainApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val prefs by lazy { getSharedPreferences("finance_brain", MODE_PRIVATE) }
    fun ignoredBanks(): Set<String> = prefs.getStringSet("ignored_banks", null) ?: DEFAULT_IGNORED
    fun setIgnoredBanks(v: Set<String>) { prefs.edit().putStringSet("ignored_banks", v).apply() }
    fun ignoredAccounts(): Set<String> = prefs.getStringSet("ignored_accounts", null) ?: DEFAULT_IGNORED_ACCOUNTS
    fun setIgnoredAccounts(v: Set<String>) { prefs.edit().putStringSet("ignored_accounts", v).apply() }
    val repository: TransactionRepository by lazy { TransactionRepository(database, { ignoredBanks() }, { ignoredAccounts() }) }

    var salaryDay: Int
        get() = prefs.getInt("salary_day", 1)
        set(v) { prefs.edit().putInt("salary_day", v.coerceIn(1, 28)).apply(); com.financebrain.ui.Cycle.salaryDay = v.coerceIn(1, 28) }
    var investTargetPct: Int
        get() = prefs.getInt("invest_pct", 20)
        set(v) { prefs.edit().putInt("invest_pct", v.coerceIn(0, 80)).apply() }
    var monthlyBudgetPaise: Long
        get() = prefs.getLong("monthly_budget", 0L)
        set(v) { prefs.edit().putLong("monthly_budget", v).apply() }
    var daughterName: String
        get() = prefs.getString("daughter_name", "") ?: ""
        set(v) { prefs.edit().putString("daughter_name", v.trim()).apply() }
    var dailyAlertEnabled: Boolean
        get() = prefs.getBoolean("daily_alert", false)
        set(v) { prefs.edit().putBoolean("daily_alert", v).apply() }
    var dailyAlertHour: Int
        get() = prefs.getInt("daily_alert_hour", 21)
        set(v) { prefs.edit().putInt("daily_alert_hour", v.coerceIn(0, 23)).apply() }
    val gmailAccounts: GmailAccounts by lazy { GmailAccounts(this) }
    val gmailSyncer: GmailSyncer by lazy { GmailSyncer(this, repository, gmailAccounts) }

    override fun onCreate() {
        super.onCreate()
        com.financebrain.ui.Cycle.salaryDay = salaryDay
        GmailSyncWorker.schedule(this)
        com.financebrain.alerts.DailyAlertWorker.schedule(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            repository.pruneAccounts()
            ignoredBanks().forEach { repository.purgeBank(it) }
            ignoredAccounts().forEach { k -> val b = k.substringBefore('|'); val t = k.substringAfter('|'); if (t.isNotBlank()) repository.purgeAccount(b, t) }
            repository.detectInternalTransfers()
        }
    }

    companion object {
        val DEFAULT_IGNORED = setOf("Union Bank")
        // Accounts the owner asked not to track: an unused HDFC account and accounts on another phone.
        val DEFAULT_IGNORED_ACCOUNTS = setOf("HDFC|7003", "Andhra Bank|2172", "Union Bank|2172", "Federal Bank|3048")
        fun get(context: Context): FinanceBrainApp = context.applicationContext as FinanceBrainApp
    }
}
