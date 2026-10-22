package com.example.payauth.auth

import com.example.payauth.network.AuthResponse   // adjust to wherever your AuthResponse lives
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager
) : Authenticator {

    private val plainClient = OkHttpClient()   // no authenticator, so no loops
    private val gson = Gson()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Requests sent without a token (login/register) have nothing to refresh
        val sentToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            ?: return null

        // Already retried once with a fresh token and still 401, so give up
        if (response.priorResponse != null) return null

        // One refresh at a time: parallel 401s queue here
        synchronized(this) {
            val currentToken = tokenManager.getToken()

            // Another request already refreshed while we waited, so just retry with the new token
            if (currentToken != null && currentToken != sentToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = tokenManager.getRefreshToken() ?: return null

            val refreshUrl = response.request.url.newBuilder()
                .encodedPath("/refresh")
                .query(null)
                .build()
            val body = gson.toJson(mapOf("refreshToken" to refreshToken))
                .toRequestBody("application/json".toMediaType())
            val refreshRequest = Request.Builder().url(refreshUrl).post(body).build()

            try {
                plainClient.newCall(refreshRequest).execute().use { r ->
                    if (r.code == 401) {            // refresh token dead: session is over
                        tokenManager.clearToken()
                        return null
                    }
                    if (!r.isSuccessful) return null // server trouble, so keep tokens, fail this call

                    val auth = gson.fromJson(r.body?.string(), AuthResponse::class.java)
                    tokenManager.saveTokens(auth.token, auth.refreshToken)
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer ${auth.token}")
                        .build()
                }
            } catch (e: IOException) {
                return null                          // offline, so keep tokens and try again later
            }
        }
    }
}