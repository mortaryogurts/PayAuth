package com.example

import com.example.auth.AuthResponse
import com.example.auth.JwtConfig
import com.example.auth.LoginRequest
import com.example.auth.PasswordHasher
import com.example.auth.RefreshRequest
import com.example.auth.RefreshTokenUtil
import com.example.auth.RegisterRequest
import com.example.auth.TokenIssuer
import com.example.db.DatabaseFactory
import com.example.repository.PaymentRepository
import com.example.repository.PlanRepository
import com.example.repository.RefreshTokenRepository
import com.example.repository.SubscriptionRepository
import com.example.repository.UserRepository
import com.example.stripe.stripeRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.example.stripe.StripeConfig
import com.example.stripe.stripeWebhookRoutes
import com.example.subscription.subscriptionRoutes
import io.ktor.server.plugins.ratelimit.rateLimit
import kotlinx.serialization.Serializable
import java.time.Instant

private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
fun Application.configureRouting() {
    DatabaseFactory.init()
    StripeConfig.init()
    val userRepository = UserRepository()
    val planRepository = PlanRepository()
    val subscriptionRepository = SubscriptionRepository()
    val paymentRepository = PaymentRepository()
    val refreshTokenRepository = RefreshTokenRepository()
    val tokenIssuer = TokenIssuer(refreshTokenRepository)

    routing {
        get("/") {
            call.respondText("Hello, World!")
        }
        get("/health") {
            call.respondText("OK")
        }

        get("/plans") {
            call.respond(planRepository.findAll())
        }


        post("/logout"){
            val request = call.receive<RefreshRequest>()
            refreshTokenRepository.deleteByHash(RefreshTokenUtil.hash(request.refreshToken))
            call.respond(HttpStatusCode.NoContent)
        }
        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()
                val email = principal.payload.getClaim("email").asString()
                call.respond(mapOf("userId" to userId, "email" to email))
            }
        }

        stripeRoutes(planRepository, subscriptionRepository)

        stripeWebhookRoutes(planRepository, subscriptionRepository, paymentRepository)

        subscriptionRoutes(subscriptionRepository, planRepository)

        rateLimit(AUTH_LIMIT) {
            post("/register") {
                val request = call.receive<RegisterRequest>()

                val email = request.email.trim()
                if (email.length > 254 || !emailRegex.matches(email)) {
                    call.respondError(HttpStatusCode.BadRequest, "INVALID EMAIL", "Invalid Email")
                    return@post
                }
// BCrypt only uses the first 72 bytes, so longer passwords are rejected, not silently truncated
                if (request.password.length < 8 || request.password.toByteArray().size > 72) {
                    call.respondError(HttpStatusCode.BadRequest, "INVALID PASSWORD", "Password must be 8 to 72 characters")
                    return@post
                }

                val existing = userRepository.findByEmail(email)
                if (existing != null) {
                    call.respondError(HttpStatusCode.Conflict,"EMAIL_TAKEN","Email already registered")
                    return@post
                }

                val passwordHash = PasswordHasher.hash(request.password)
                val user = userRepository.create(email, passwordHash)
                call.respond(HttpStatusCode.Created, tokenIssuer.issue(user.id, user.email))
            }

            post("/login"){
                val request = call.receive<LoginRequest>()

                val user = userRepository.findByEmail(request.email)

                if(user==null){
                    call.respondError(HttpStatusCode.Unauthorized,"INVALID_CREDENTIALS", "Invalid email or password")
                    return@post
                }
                val passwordMatches = PasswordHasher.verify(request.password, user.passwordHash)
                if(!passwordMatches){
                    call.respondError(HttpStatusCode.Unauthorized,"INVALID_CREDENTIALS", "Invalid email or password")
                    return@post
                }

                call.respond(HttpStatusCode.OK, tokenIssuer.issue(user.id, user.email))
            }
        }
        rateLimit(REFRESH_LIMIT) {

            post("/refresh"){
                val request = call.receive<RefreshRequest>()
                val hash = RefreshTokenUtil.hash(request.refreshToken)

                val stored = refreshTokenRepository.findByHash(hash)
                // deleting is the "use it up" step: if it returns false, someone already used this token
                val consumed = stored != null && refreshTokenRepository.deleteByHash(hash)

                if (stored == null || !consumed || stored.expiresAt.isBefore(Instant.now())) {
                    call.respondError(HttpStatusCode.Unauthorized, "INVALID_REFRESH_TOKEN", "Invalid or expired refresh token")
                    return@post
                }
                call.respond(HttpStatusCode.OK, tokenIssuer.issue(stored.userId, stored.email))
            }
        }
    }
}

@Serializable
data class MeResponse(val id: String, val email: String)