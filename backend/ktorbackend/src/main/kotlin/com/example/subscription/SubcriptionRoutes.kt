package com.example.subscription

import com.example.repository.PlanRepository
import com.example.repository.SubscriptionRepository
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SubscriptionStatusResponse(
    val active: Boolean,
    val status: String? = null,
    val planName: String? = null,
    val currentPeriodEnd: String? = null,
    val cancelAtPeriodEnd: Boolean = false
)

fun Route.subscriptionRoutes(
    subscriptionRepository: SubscriptionRepository,
    planRepository: PlanRepository
) {
    authenticate("auth-jwt") {
        get("/subscription-status") {
            val principal = call.principal<JWTPrincipal>()
            val userId = UUID.fromString(principal!!.payload.getClaim("userId").asString())

            val subscription = subscriptionRepository.findLatestByUserId(userId)

            if (subscription == null) {
                // No subscription row at all — user has never subscribed.
                call.respond(HttpStatusCode.OK, SubscriptionStatusResponse(active = false))
                return@get
            }

            val plan = planRepository.findById(subscription.planId)

            // "active" here mirrors Stripe's own status values. Depending on how
            // strict you want gating to be, you might also treat "trialing" as
            // active — that's a product decision, not a technical one.
            val isActive = subscription.status == "active"

            call.respond(
                HttpStatusCode.OK,
                SubscriptionStatusResponse(
                    active = isActive,
                    status = subscription.status,
                    planName = plan?.name,
                    currentPeriodEnd = subscription.currentPeriodEnd.toString(),
                    cancelAtPeriodEnd = isActive && subscription.cancelAtPeriodEnd
                )
            )
        }
    }
}