package com.example

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerTest {

    @Test
    fun `test root endpoint`() = appTest {
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }
}
