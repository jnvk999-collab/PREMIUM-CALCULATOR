package com.financebrain.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.financebrain.FinanceBrainApp
import java.util.concurrent.TimeUnit

/** Safety net for phones that kill the SMS receiver: re-read new inbox rows every 30 minutes. */
class SmsScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val app = FinanceBrainApp.get(applicationContext)
        SmsInboxScanner(applicationContext, app.repository).scan { }
        app.repository.syncCardsFromTransactions()
        app.repository.detectInternalTransfers()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SmsScanWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sms-scan", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
