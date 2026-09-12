package com.financebrain

import android.app.Application
import android.content.Context
import com.financebrain.data.AppDatabase
import com.financebrain.data.TransactionRepository
import com.financebrain.gmail.GmailAccounts
import com.financebrain.gmail.GmailSyncWorker
import com.financebrain.gmail.GmailSyncer

class FinanceBrainApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: TransactionRepository by lazy { TransactionRepository(database) }
    val gmailAccounts: GmailAccounts by lazy { GmailAccounts(this) }
    val gmailSyncer: GmailSyncer by lazy { GmailSyncer(this, repository, gmailAccounts) }

    override fun onCreate() {
        super.onCreate()
        GmailSyncWorker.schedule(this)
    }

    companion object {
        fun get(context: Context): FinanceBrainApp = context.applicationContext as FinanceBrainApp
    }
}
