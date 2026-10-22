package com.example.auth

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val refreshToken: String, val userId: String, val email: String)

@Serializable
data class LoginRequest(val email : String, val password : String)

@Serializable
data class RefreshRequest(val refreshToken: String)