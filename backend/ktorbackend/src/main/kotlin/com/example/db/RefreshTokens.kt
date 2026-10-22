package com.example.db
import com.example.db.Plans
import com.example.db.Users
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp


object RefreshTokens : UUIDTable("refresh_tokens") {
    val userId = reference("user_id", Users)
    val email = varchar("email", 255)
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val expiresAt = timestamp("expires_at")
}
