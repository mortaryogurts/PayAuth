package com.example.repository

import com.example.db.DatabaseFactory.dbQuery
import com.example.db.RefreshTokens
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.util.UUID

data class StoredRefreshToken(val userId: UUID, val email: String, val expiresAt: Instant)

class RefreshTokenRepository {

    suspend fun create(userId: UUID, email: String, tokenHash: String, expiresAt: Instant) = dbQuery {
        RefreshTokens.insert {
            it[RefreshTokens.userId] = userId
            it[RefreshTokens.email] = email
            it[RefreshTokens.tokenHash] = tokenHash
            it[RefreshTokens.expiresAt] = expiresAt
        }
        Unit
    }

    suspend fun findByHash(tokenHash: String): StoredRefreshToken? = dbQuery {
        RefreshTokens.selectAll()
            .where { RefreshTokens.tokenHash eq tokenHash }
            .map {
                StoredRefreshToken(
                    userId = it[RefreshTokens.userId].value,
                    email = it[RefreshTokens.email],
                    expiresAt = it[RefreshTokens.expiresAt]
                )
            }
            .singleOrNull()
    }

    /** Returns true only if this call actually removed the row. */
    suspend fun deleteByHash(tokenHash: String): Boolean = dbQuery {
        RefreshTokens.deleteWhere { RefreshTokens.tokenHash eq tokenHash } > 0
    }
    /** Removes tokens past their expiry; returns how many were deleted. */
    suspend fun deleteExpired(now: Instant): Int = dbQuery {
        RefreshTokens.deleteWhere { expiresAt less now }
    }
}