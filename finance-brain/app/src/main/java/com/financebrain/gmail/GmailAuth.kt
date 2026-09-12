package com.financebrain.gmail

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

/** Google sign-in for Gmail read-only access, one browser round-trip per account. */
class GmailAuth(private val context: Context) {
    private val config = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token"),
    )
    private val service = AuthorizationService(context)

    fun signInIntent(clientId: String): Intent {
        val req = AuthorizationRequest.Builder(config, clientId, ResponseTypeValues.CODE, Uri.parse("com.financebrain:/oauth2redirect"))
            .setScope("https://www.googleapis.com/auth/gmail.readonly")
            .setPrompt("select_account consent")
            .setAdditionalParameters(mapOf("access_type" to "offline"))
            .build()
        return service.getAuthorizationRequestIntent(req)
    }

    /** Exchanges the code and returns the authorized account state, or an error message. */
    suspend fun complete(data: Intent?): Result<AuthState> {
        if (data == null) return Result.failure(IllegalStateException("Sign-in cancelled"))
        val resp = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)
        if (resp == null) return Result.failure(IllegalStateException(ex?.errorDescription ?: ex?.error ?: "Sign-in cancelled"))
        val state = AuthState(resp, ex)
        return suspendCoroutine { cont ->
            service.performTokenRequest(resp.createTokenExchangeRequest()) { tokenResp, tokenEx ->
                state.update(tokenResp, tokenEx)
                if (tokenResp == null) cont.resume(Result.failure(IllegalStateException(tokenEx?.errorDescription ?: "Token exchange failed")))
                else cont.resume(Result.success(state))
            }
        }
    }
}
