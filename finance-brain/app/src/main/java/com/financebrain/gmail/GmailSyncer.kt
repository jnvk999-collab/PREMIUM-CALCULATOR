package com.financebrain.gmail

import android.content.Context
import com.financebrain.data.Source
import com.financebrain.data.TransactionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

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

    /** Network hiccups are common on phones; retry a few times before giving up on a page. */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var last: Exception? = null
        repeat(4) { attempt ->
            try { return block() } catch (e: IOException) { last = e; delay(1500L * (attempt + 1)) }
        }
        throw last!!
    }

    private fun friendly(e: Exception): String {
        val m = e.message ?: ""
        return when {
            e is java.net.UnknownHostException || m.contains("Unable to resolve host") -> "No internet connection. Will retry on the next sync."
            m.contains("timed out", true) -> "Connection timed out. Will retry on the next sync."
            m.contains("401") || m.contains("invalid_grant") -> "Google sign-in expired. Remove and add the account again."
            m.contains("403") -> "Gmail access was refused. Check the account is a test user in Google Cloud."
            else -> m.ifBlank { "Sync failed" }
        }
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
                val (ids, next) = withRetry { client.listIds(query, page) }
                for (id in ids) {
                    if (repo.isEmailProcessed(id)) continue
                    val msg = try { withRetry { client.message(id) } } catch (e: IOException) { throw e } catch (e: Exception) { continue }
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
            val msg = friendly(e)
            // Keep what was imported so far; the saved page cursor lets the next run resume.
            accounts.updateMeta(email, importedDelta = imported, lastError = msg)
            _progress.value = GmailSyncProgress(email, fetched, imported, true, msg)
        }
    }
}
