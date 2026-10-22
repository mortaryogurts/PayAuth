package com.example.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.config.Env
import java.util.Date
import java.util.UUID

object JwtConfig {

    private val secret = Env.require("JWT_SECRET")
    private const val issuer = "ktorbackend"
    private val validityMs = (Env.get("ACCESS_TOKEN_MINUTES")?.toLongOrNull() ?: 15L) * 60_000L

    private val algorithm = Algorithm.HMAC256(secret)

    fun generateToken(userId: UUID, email: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withClaim("userId", userId.toString())
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + validityMs))
            .sign(algorithm)

    val verifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .build()
}