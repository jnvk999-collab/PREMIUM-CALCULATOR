package com.financebrain.gmail

import android.content.Context
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService
import org.json.JSONObject

data class GmailMessage(val id: String, val from: String, val subject: String, val date: Long, val text: String)

/** Thin Gmail REST client. Tokens are refreshed through AppAuth before every call. */
class GmailClient(context: Context, private val state: AuthState, private val onStateChanged: (AuthState) -> Unit) {
    private val service = AuthorizationService(context)

    private suspend fun token(): String = suspendCoroutine { cont ->
        state.performActionWithFreshTokens(service) { access, _, ex ->
            onStateChanged(state)
            if (ex != null || access == null) cont.resumeWithException(ex ?: IllegalStateException("No access token"))
            else cont.resume(access)
        }
    }

    private suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        val t = token()
        val conn = (URL("https://gmail.googleapis.com/gmail/v1/$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $t")
            setRequestProperty("Accept", "application/json")
        }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) throw IllegalStateException("Gmail $code: ${body.take(200)}")
        JSONObject(body)
    }

    suspend fun profileEmail(): String = get("users/me/profile").getString("emailAddress")

    /** Returns message ids on one page plus the next page token. */
    suspend fun listIds(query: String, pageToken: String?, pageSize: Int = 100): Pair<List<String>, String?> {
        val q = URLEncoder.encode(query, "UTF-8")
        val pt = pageToken?.let { "&pageToken=$it" } ?: ""
        val j = get("users/me/messages?q=$q&maxResults=$pageSize$pt")
        val arr = j.optJSONArray("messages")
        val ids = ArrayList<String>()
        if (arr != null) for (i in 0 until arr.length()) ids += arr.getJSONObject(i).getString("id")
        return ids to j.optString("nextPageToken").ifBlank { null }
    }

    suspend fun message(id: String): GmailMessage {
        val j = get("users/me/messages/$id?format=full")
        val payload = j.getJSONObject("payload")
        val headers = payload.optJSONArray("headers")
        var from = ""; var subject = ""
        if (headers != null) for (i in 0 until headers.length()) {
            val h = headers.getJSONObject(i)
            when (h.optString("name").lowercase()) {
                "from" -> from = h.optString("value")
                "subject" -> subject = h.optString("value")
            }
        }
        val date = j.optLong("internalDate")
        val plain = StringBuilder(); val html = StringBuilder()
        collect(payload, plain, html)
        val text = if (plain.isNotBlank()) plain.toString() else stripHtml(html.toString())
        return GmailMessage(id, from, subject, date, text.take(20_000))
    }

    private fun collect(part: JSONObject, plain: StringBuilder, html: StringBuilder) {
        val mime = part.optString("mimeType")
        val data = part.optJSONObject("body")?.optString("data")?.takeIf { it.isNotBlank() }
        if (data != null) {
            val decoded = try { String(Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8) } catch (_: Exception) { "" }
            if (mime.startsWith("text/plain")) plain.append(decoded).append('\n')
            else if (mime.startsWith("text/html")) html.append(decoded).append('\n')
        }
        val parts = part.optJSONArray("parts") ?: return
        for (i in 0 until parts.length()) collect(parts.getJSONObject(i), plain, html)
    }

    companion object {
        fun stripHtml(h: String): String = h
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</tr>|</li>|</h\\d>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&#8377;", "₹").replace("&#x20B9;", "₹")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .replace(Regex("\\n\\s*\\n+"), "\n")
            .trim()
    }
}
