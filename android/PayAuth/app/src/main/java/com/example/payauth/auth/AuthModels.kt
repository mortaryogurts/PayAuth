package com.example.payauth.network

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val refreshToken: String, val userId: String, val email: String)

data class RefreshRequest(val refreshToken: String)