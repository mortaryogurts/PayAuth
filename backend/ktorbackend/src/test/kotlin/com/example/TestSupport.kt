package com.example

import com.example.config.Env
import com.example.db.DatabaseFactory
import com.example.db.Payments
import com.example.db.Plans
import com.example.db.RefreshTokens
import com.example.db.Subscriptions
import com.example.db.Users
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction

object TestDb {
    /** Empties every table (children first, because of foreign keys). */
    fun reset() {
        val url = Env.get("DB_URL") ?: ""
        check(url.endsWith("saas_test") || url.startsWith("jdbc:h2:") || url.isEmpty()) {
            "Refusing to wipe a non-test database: $url"
        }
        DatabaseFactory.init()
        transaction {
            RefreshTokens.deleteAll()
            Payments.deleteAll()
            Subscriptions.deleteAll()
            Plans.deleteAll()
            Users.deleteAll()
        }
    }
}

/** Boots the real app modules against the test database. */
fun appTest(block: suspend ApplicationTestBuilder.() -> Unit) {
    TestDb.reset()
    testApplication {
        // don't also auto-load application.yaml, or the modules would be installed twice
        environment { config = MapApplicationConfig() }
        application {
            configureSecurity()
            configureSerialization()
            configureRateLimit()
            configureRouting()
        }
        block()
    }
}

suspend fun HttpClient.postJson(path: String, body: String, token: String? = null): HttpResponse =
    post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
        if (token != null) bearerAuth(token)
    }

suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

fun creds(email: String, password: String = "password123") =
    """{"email":"$email","password":"$password"}"""

fun refreshBody(token: String) = """{"refreshToken":"$token"}"""