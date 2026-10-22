package com.example

import com.example.auth.RefreshTokenUtil
import com.example.repository.RefreshTokenRepository
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthTest {

    @Test
    fun `register returns access and refresh token`() = appTest {
        val res = client.postJson("/register", creds("a@test.com"))
        assertEquals(HttpStatusCode.Created, res.status)
        val body = res.json()
        assertTrue(body["token"]!!.jsonPrimitive.content.isNotBlank())
        assertTrue(body["refreshToken"]!!.jsonPrimitive.content.isNotBlank())
        assertEquals("a@test.com", body["email"]!!.jsonPrimitive.content)
    }

    @Test
    fun `register rejects duplicate email`() = appTest {
        client.postJson("/register", creds("a@test.com"))
        val res = client.postJson("/register", creds("a@test.com"))
        assertEquals("EMAIL_TAKEN", res.json()["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `register rejects short password and bad email`() = appTest {
        assertEquals(HttpStatusCode.BadRequest, client.postJson("/register", creds("a@test.com", "short")).status)
        assertEquals(HttpStatusCode.BadRequest, client.postJson("/register", creds("not-an-email")).status)
    }

    @Test
    fun `login succeeds with right password`() = appTest {
        client.postJson("/register", creds("a@test.com"))
        val res = client.postJson("/login", creds("a@test.com"))
        assertEquals(HttpStatusCode.OK, res.status)
    }

    @Test
    fun `login fails the same way for wrong password and unknown email`() = appTest {
        client.postJson("/register", creds("a@test.com"))
        val wrongPassword = client.postJson("/login", creds("a@test.com", "wrong-password"))
        val unknownEmail = client.postJson("/login", creds("nobody@test.com"))
        assertEquals("INVALID_CREDENTIALS", wrongPassword.json()["code"]!!.jsonPrimitive.content)
        assertEquals("INVALID_CREDENTIALS", unknownEmail.json()["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `me returns the logged in user and rejects missing or bad tokens`() = appTest {
        val login = client.postJson("/register", creds("a@test.com")).json()
        val token = login["token"]!!.jsonPrimitive.content

        val ok = client.get("/me") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("a@test.com", ok.json()["email"]!!.jsonPrimitive.content)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/me").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth("garbage") }.status)
    }

    @Test
    fun `refresh rotates the token and a used token cannot be reused`() = appTest {
        val first = client.postJson("/register", creds("a@test.com")).json()
        val oldRefresh = first["refreshToken"]!!.jsonPrimitive.content

        val rotated = client.postJson("/refresh", refreshBody(oldRefresh))
        assertEquals(HttpStatusCode.OK, rotated.status)
        val newRefresh = rotated.json()["refreshToken"]!!.jsonPrimitive.content
        assertNotEquals(oldRefresh, newRefresh)

        // the old one is used up
        assertEquals(HttpStatusCode.Unauthorized, client.postJson("/refresh", refreshBody(oldRefresh)).status)
        // the new one still works
        assertEquals(HttpStatusCode.OK, client.postJson("/refresh", refreshBody(newRefresh)).status)
    }

    @Test
    fun `refresh rejects an unknown token`() = appTest {
        val res = client.postJson("/refresh", refreshBody("not-a-real-token"))
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `logout revokes the refresh token`() = appTest {
        val login = client.postJson("/register", creds("a@test.com")).json()
        val refresh = login["refreshToken"]!!.jsonPrimitive.content

        assertEquals(HttpStatusCode.NoContent, client.postJson("/logout", refreshBody(refresh)).status)
        assertEquals(HttpStatusCode.Unauthorized, client.postJson("/refresh", refreshBody(refresh)).status)
    }

    @Test
    fun `register rejects malformed emails and over-long passwords`() = appTest {
        assertEquals(HttpStatusCode.BadRequest, client.postJson("/register", creds("a@b")).status)
        assertEquals(HttpStatusCode.BadRequest, client.postJson("/register", creds("two words@test.com")).status)
        assertEquals(HttpStatusCode.BadRequest, client.postJson("/register", creds("a@test.com", "x".repeat(73))).status)
    }

    @Test
    fun `login is rate limited after 10 attempts`() = appTest {
        val statuses = (1..11).map {
            client.postJson("/login", creds("a@test.com", "wrong-password")).status
        }
        assertEquals(List(10) { HttpStatusCode.Unauthorized }, statuses.take(10))
        assertEquals(HttpStatusCode.TooManyRequests, statuses.last())
    }
    @Test
    fun `expired refresh tokens are purged and live ones survive`() = appTest {
        val reg = client.postJson("/register", creds("a@test.com")).json()
        val userId = UUID.fromString(reg["userId"]!!.jsonPrimitive.content)
        val repo = RefreshTokenRepository()

        repo.create(userId, "a@test.com", "expired-hash", Instant.now().minusSeconds(60))
        repo.deleteExpired(Instant.now())

        assertNull(repo.findByHash("expired-hash"))
        assertNotNull(repo.findByHash(RefreshTokenUtil.hash(reg["refreshToken"]!!.jsonPrimitive.content)))
    }
}