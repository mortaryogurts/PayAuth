package com.example.repository

import com.example.db.DatabaseFactory.dbQuery
import com.example.db.Users
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val createdAt: Instant
)

private fun rowToUser(row: ResultRow) = User(
    id = row[Users.id].value,
    email = row[Users.email],
    passwordHash = row[Users.passwordHash],
    createdAt = row[Users.createdAt]
)

class UserRepository {

    suspend fun create(email: String, passwordHash: String): User = dbQuery {
        val now = Instant.now()
        val id = Users.insertAndGetId {
            it[Users.email] = email
            it[Users.passwordHash] = passwordHash
            it[Users.createdAt] = now
        }
        User(id = id.value, email = email, passwordHash = passwordHash, createdAt = now)
    }

    suspend fun findByEmail(email: String): User? = dbQuery {
        Users.selectAll()
            .where { Users.email eq email }
            .map(::rowToUser)
            .singleOrNull()
    }
}