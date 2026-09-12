package com.financebrain.gmail

import android.content.Context
import com.financebrain.data.Source
import com.financebrain.data.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class GmailSyncProgress(val email: String, val fetched: Int, val imported: Int, val done: Boolean, val error: String? = null)

/**
 * Pulls money-related mail for every connected account. The first run walks the whole
 * mailbox page by page (resumable); later runs only look at the last few days.
 */
class GmailSyncer(private val context: Context, private val repo: TransactionRepository, private val accounts: GmailAccounts) {

    private val _progress = MutableStateFlow<GmailSyncProgress?>(null)
    val progress: StateFlow<GmailSyncProgress?> = _progress

    @Volatile var running = false; private set

    suspend fun syncAll(maxMessagesPerAccount: Int = 3000) {
        if (running) return
        running = true
        try { for (email in accounts.emails()) syncOne(email, maxMessagesPerAccount) } finally { running = false }
    }

    suspend fun syncOne(email: String, maxMessages: Int) {
        val state = accounts.authState(email) ?: return
        val client = GmailClient(context, state) { accounts.save(email, it) }
        val meta = accounts.list().first { it.email == email }
        var fetched = 0; var imported = 0
        _progress.value = GmailSyncProgress(email, 0, 0, false)
        try {
            val query = if (meta.historyComplete) EmailParser.QUERY + " newer_than:7d" else EmailParser.QUERY
            var page: String? = if (meta.historyComplete) null else accounts.cursor(email)
            var more = true
            while (more && fetched < maxMessages) {
                val (ids, next) = client.listIds(query, page)
                for (id in ids) {
                    if (repo.isEmailProcessed(id)) continue
                    val msg = try { client.message(id) } catch (e: Exception) { continue }
                    fetched++
                    val parsed = EmailParser.parse(msg)
                    var stored = false
                    if (parsed != null) stored = repo.ingest(parsed, "${msg.subject}\n${msg.text.take(1500)}", Source.EMAIL)
                    if (stored) imported++
                    repo.markEmailProcessed(id, parsed != null)
                    if (fetched % 10 == 0) _progress.value = GmailSyncProgress(email, fetched, imported, false)
                }
                page = next
                if (!meta.historyComplete) accounts.setCursor(email, page)
                more = next != null
            }
            if (!more) { accounts.setCursor(email, null); accounts.updateMeta(email, historyComplete = true) }
            accounts.updateMeta(email, lastSyncAt = System.currentTimeMillis(), importedDelta = imported, clearError = true)
            _progress.value = GmailSyncProgress(email, fetched, imported, true)
        } catch (e: Exception) {
            accounts.updateMeta(email, lastError = e.message ?: "Sync failed")
            _progress.value = GmailSyncProgress(email, fetched, imported, true, e.message)
        }
    }
}
