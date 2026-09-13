package com.financebrain.gmail

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.financebrain.BuildConfig
import net.openid.appauth.AuthState
import org.json.JSONObject

data class GmailAccount(
    val email: String,
    val lastSyncAt: Long,
    val historyComplete: Boolean,
    val imported: Int,
    val lastError: String?,
)

/** Encrypted on-device store of connected Gmail accounts and their OAuth tokens. */
class GmailAccounts(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "gmail_accounts",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var clientId: String
        get() = BuildConfig.GMAIL_CLIENT_ID.ifBlank { prefs.getString("client_id", null) ?: "" }
        set(v) { prefs.edit().putString("client_id", v.trim()).apply() }

    fun list(): List<GmailAccount> = emails().map { e ->
        val j = JSONObject(prefs.getString("meta:$e", "{}") ?: "{}")
        GmailAccount(e, j.optLong("lastSyncAt"), j.optBoolean("historyComplete"), j.optInt("imported"), j.optString("lastError").ifBlank { null })
    }

    fun emails(): List<String> = prefs.getStringSet("emails", emptySet())!!.toList().sorted()

    fun authState(email: String): AuthState? = prefs.getString("auth:$email", null)?.let { AuthState.jsonDeserialize(it) }

    fun save(email: String, state: AuthState) {
        prefs.edit()
            .putStringSet("emails", emails().toMutableSet().apply { add(email) })
            .putString("auth:$email", state.jsonSerializeString())
            .apply()
    }

    fun updateMeta(email: String, lastSyncAt: Long? = null, historyComplete: Boolean? = null, importedDelta: Int = 0, lastError: String? = null, clearError: Boolean = false) {
        val j = JSONObject(prefs.getString("meta:$email", "{}") ?: "{}")
        lastSyncAt?.let { j.put("lastSyncAt", it) }
        historyComplete?.let { j.put("historyComplete", it) }
        if (importedDelta != 0) j.put("imported", j.optInt("imported") + importedDelta)
        if (clearError) j.remove("lastError") else lastError?.let { j.put("lastError", it) }
        prefs.edit().putString("meta:$email", j.toString()).apply()
    }

    fun remove(email: String) {
        prefs.edit()
            .putStringSet("emails", emails().toMutableSet().apply { remove(email) })
            .remove("auth:$email").remove("meta:$email").remove("cursor:$email")
            .apply()
    }

    /** Gmail page token for an in-progress history import, so a long first sync can resume. */
    fun cursor(email: String): String? = prefs.getString("cursor:$email", null)
    fun setCursor(email: String, token: String?) { prefs.edit().putString("cursor:$email", token).apply() }
}
