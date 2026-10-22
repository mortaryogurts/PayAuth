package com.example

import com.example.db.DatabaseFactory
import com.example.repository.RefreshTokenRepository
import io.ktor.server.application.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration.Companion.hours

fun Application.configureCleanup() {
    DatabaseFactory.init()   // safe to call twice, it only runs once
    val repo = RefreshTokenRepository()
    launch {
        while (isActive) {
            runCatching { repo.deleteExpired(Instant.now()) }
                .onSuccess { if (it > 0) log.info("Purged $it expired refresh tokens") }
                .onFailure { log.warn("Refresh token cleanup failed: ${it.message}") }
            delay(6.hours)
        }
    }
}