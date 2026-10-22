package com.example.auth

import com.example.repository.RefreshTokenRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

object RefreshTokenUtil {
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

class TokenIssuer(private val refreshTokens: RefreshTokenRepository) {

    suspend fun issue(userId: UUID, email: String): AuthResponse {
        val accessToken = JwtConfig.generateToken(userId, email)
        val rawRefresh = RefreshTokenUtil.generate()
        refreshTokens.create(
            userId = userId,
            email = email,
            tokenHash = RefreshTokenUtil.hash(rawRefresh),
            expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
        )
        return AuthResponse(
            token = accessToken,
            refreshToken = rawRefresh,
            userId = userId.toString(),
            email = email
        )
    }
}