package com.financebrain.sms

import android.content.Context
import android.provider.Telephony
import com.financebrain.data.ProcessedSms
import com.financebrain.data.Source
import com.financebrain.data.TransactionRepository
import com.financebrain.parser.BankSmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanProgress(val scanned: Int, val total: Int, val found: Int, val done: Boolean)

/** Walks the entire SMS inbox so history from before the app was installed is captured. */
class SmsInboxScanner(private val context: Context, private val repo: TransactionRepository) {

    suspend fun scan(onProgress: (ScanProgress) -> Unit) = withContext(Dispatchers.IO) {
        val already = repo.processedSmsIds()
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, projection, null, null, "${Telephony.Sms.DATE} DESC"
        ) ?: run { onProgress(ScanProgress(0, 0, 0, true)); return@withContext }

        cursor.use {
            val total = it.count
            var scanned = 0
            var found = 0
            val batch = mutableListOf<ProcessedSms>()
            val idIdx = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addrIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (it.moveToNext()) {
                scanned++
                val id = it.getLong(idIdx)
                if (id in already) continue
                val sender = it.getString(addrIdx)
                val body = it.getString(bodyIdx) ?: ""
                val date = it.getLong(dateIdx)
                val parsed = BankSmsParser.parse(sender, body, date)
                var stored = false
                if (parsed != null) stored = repo.ingest(parsed, body, Source.SMS)
                if (stored) found++
                batch += ProcessedSms(id, parsed != null)
                if (batch.size >= 200) { repo.markProcessed(batch.toList()); batch.clear() }
                if (scanned % 50 == 0) onProgress(ScanProgress(scanned, total, found, false))
            }
            if (batch.isNotEmpty()) repo.markProcessed(batch)
            onProgress(ScanProgress(scanned, total, found, true))
        }
    }
}
