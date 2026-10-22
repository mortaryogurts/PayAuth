package com.example

import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes

val AUTH_LIMIT = RateLimitName("auth")
val REFRESH_LIMIT = RateLimitName("refresh")

fun Application.configureRateLimit() {
    install(RateLimit) {
        // login + register: slows down password guessing and mass sign-ups
        register(AUTH_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
        // refresh is called silently by the app, so the limit is looser
        register(REFRESH_LIMIT) {
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
}