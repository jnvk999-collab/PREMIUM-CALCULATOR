package com.financebrain.gmail

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.financebrain.FinanceBrainApp
import java.util.concurrent.TimeUnit

/** Runs Gmail sync in the background every few hours, only on a network connection. */
class GmailSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = FinanceBrainApp.get(applicationContext)
        if (app.gmailAccounts.emails().isEmpty()) return Result.success()
        app.gmailSyncer.syncAll(maxMessagesPerAccount = 500)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<GmailSyncWorker>(4, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("gmail-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
