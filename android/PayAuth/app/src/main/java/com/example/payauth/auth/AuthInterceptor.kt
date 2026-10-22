package com.example.payauth.auth

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenManager.getToken()
        val request = chain.request().newBuilder().apply {
            if (token != null) header("Authorization", "Bearer $token")
        }.build()
        return chain.proceed(request)
    }
}