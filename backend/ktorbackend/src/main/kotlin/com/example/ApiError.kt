package com.example

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

@Serializable
data class ApiError(val error: String, val code: String)

suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String, message: String) =
    respond(status, ApiError(error = message, code = code))