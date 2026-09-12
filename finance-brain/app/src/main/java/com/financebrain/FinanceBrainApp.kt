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
    val repository: TransactionRepository by lazy { TransactionRepository(database) { ignoredBanks() } }
    val gmailAccounts: GmailAccounts by lazy { GmailAccounts(this) }
    val gmailSyncer: GmailSyncer by lazy { GmailSyncer(this, repository, gmailAccounts) }

    override fun onCreate() {
        super.onCreate()
        GmailSyncWorker.schedule(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            repository.pruneAccounts()
            ignoredBanks().forEach { repository.purgeBank(it) }
            repository.detectInternalTransfers()
        }
    }

    companion object {
        val DEFAULT_IGNORED = setOf("Union Bank")
        fun get(context: Context): FinanceBrainApp = context.applicationContext as FinanceBrainApp
    }
}
