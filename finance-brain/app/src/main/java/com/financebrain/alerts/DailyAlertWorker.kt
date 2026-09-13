package com.financebrain.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.financebrain.FinanceBrainApp
import com.financebrain.data.Insights
import com.financebrain.ui.formatRupees
import com.financebrain.ui.monthEnd
import com.financebrain.ui.monthStart
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** Evening summary: what went out today and what is left for the cycle. */
class DailyAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = FinanceBrainApp.get(applicationContext)
        if (!app.dailyAlertEnabled) return Result.success()
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val all = app.repository.transactions.first()
        val now = System.currentTimeMillis()
        val dayStart = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val today = all.filter { it.timestamp >= dayStart && Insights.isSpend(it) }.sumOf { it.amountPaise }
        val cs = monthStart(now); val ce = monthEnd(now)
        val cycleSpend = all.filter { it.timestamp in cs until ce && Insights.isSpend(it) }.sumOf { it.amountPaise }
        val budget = app.monthlyBudgetPaise
        val daysLeft = ((ce - now) / 86_400_000L).toInt().coerceAtLeast(1)
        val text = buildString {
            append("Today: ").append(formatRupees(today)).append(" out. ")
            if (budget > 0) {
                val left = budget - cycleSpend
                if (left >= 0) append("₹").append(formatRupees(left).drop(1)).append(" left for ").append(daysLeft).append(" days (").append(formatRupees(left / daysLeft)).append("/day).")
                else append("Over budget by ").append(formatRupees(-left)).append(".")
            } else append("Cycle so far: ").append(formatRupees(cycleSpend)).append(".")
        }
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily spend summary", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, com.financebrain.ui.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Finance Brain · evening check")
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).build()
        nm.notify(1001, n)
        return Result.success()
    }

    companion object {
        const val CHANNEL = "daily_summary"
        fun schedule(context: Context) {
            val app = FinanceBrainApp.get(context)
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, app.dailyAlertHour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
            if (next.before(now)) next.add(Calendar.DAY_OF_YEAR, 1)
            val delay = next.timeInMillis - now.timeInMillis
            val req = PeriodicWorkRequestBuilder<DailyAlertWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("daily-alert", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
