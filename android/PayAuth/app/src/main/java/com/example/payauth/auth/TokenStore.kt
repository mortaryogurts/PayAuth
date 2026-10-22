package com.example.payauth.auth

interface TokenStore {
    fun saveToken(token: String)
    fun saveTokens(accessToken: String, refreshToken: String)
    fun getToken(): String?
    fun getRefreshToken(): String?
    fun clearToken()
}