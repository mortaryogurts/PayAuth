package com.example

import com.example.auth.JwtConfig
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.respond

fun Application.configureSecurity() {
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(JwtConfig.verifier)
            validate { credential ->
                // If the token has a userId claim, treat it as valid and
                // expose that claim to route handlers via the principal.
                val userId = credential.payload.getClaim("userId").asString()
                if (userId != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respondError(io.ktor.http.HttpStatusCode.Unauthorized,"UNAUTHORIZED","Token is missing, invalid, or expired")
            }
        }
    }
}