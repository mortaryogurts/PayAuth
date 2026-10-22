package com.example.config

import io.github.cdimascio.dotenv.dotenv

object Env {
    private val dotenv = dotenv {
        ignoreIfMissing = true // don't crash if .env isn't present (e.g. in production)
    }

    fun get(key: String): String? =
        System.getenv(key) ?: dotenv[key]

    fun require(key: String): String =

        get(key) ?: error("Missing required environment variable: $key")
}