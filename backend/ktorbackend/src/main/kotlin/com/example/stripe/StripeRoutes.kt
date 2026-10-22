package com.example.stripe

import com.example.repository.PlanRepository
import com.example.repository.SubscriptionRepository
import com.stripe.model.Subscription as StripeSubscription
import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionCreateParams
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID
import com.example.config.Env
import kotlinx.serialization.Serializable

fun Route.stripeRoutes(
    planRepository: PlanRepository,
    subscriptionRepository: SubscriptionRepository
    ) {

    authenticate("auth-jwt") {
        post("/subscribe") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal!!.payload.getClaim("userId").asString()

            val existing = subscriptionRepository.findLatestByUserId(UUID.fromString(userId))
            if (existing != null && existing.status in setOf("active", "trialing")) {
                call.respond(HttpStatusCode.Conflict, "Already Subscribed")
                return@post
            }
            val request = call.receive<CreateCheckoutSessionRequest>()

            val plan = planRepository.findByStripePriceId(request.stripePriceId)
            if (plan == null) {
                call.respond(HttpStatusCode.BadRequest, "Unknown plan")
                return@post
            }

            val baseUrl = Env.get("PUBLIC_BASE_URL") ?: "http://localhost:8080"
            val params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl("$baseUrl/subscribe/success")
                .setCancelUrl("$baseUrl/subscribe/cancel")
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPrice(request.stripePriceId)
                        .setQuantity(1L)
                        .build()
                )
                // Ties this Checkout Session back to your user, so the webhook
                // handler knows which of YOUR users this payment belongs to.
                .putMetadata("userId", userId)
                .build()

            val session = Session.create(params)

            call.respond(HttpStatusCode.OK, CheckoutSessionResponse(checkoutUrl = session.url))
        }

        post("/billing-portal") {

            val userId = UUID.fromString(
                call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
            )

            val sub = subscriptionRepository.findLatestByUserId(userId)
            if (sub == null) {
                call.respond(HttpStatusCode.NotFound, "No subscription")
                return@post
            }

            // the subscriptions table doesn't store the Stripe customer id, so ask Stripe for it
            val customerId = StripeSubscription.retrieve(sub.stripeSubscriptionId).customer
            val baseUrl = Env.get("PUBLIC_BASE_URL") ?: "http://localhost:8080"

            val params = com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl("$baseUrl/billing/return")
                .build()
            val portal = com.stripe.model.billingportal.Session.create(params)
            call.respond(HttpStatusCode.OK, PortalSessionResponse(portalUrl = portal.url))
        }
    }

    get("/subscribe/success") {
        call.respondText(
            returnToAppPage("Payment received", "payauth://subscribe/success"),
            ContentType.Text.Html
        )
    }
    get("/subscribe/cancel") {
        call.respondText(
            returnToAppPage("Checkout canceled", "payauth://subscribe/cancel"),
            ContentType.Text.Html
        )
    }
    get("/billing/return") {
        call.respondText(
            returnToAppPage("Back to the app", "payauth://subscribe/portal"),
            ContentType.Text.Html
        )
    }
}


private fun returnToAppPage(title: String, deepLink: String) = """
<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>$title</title>
  <meta http-equiv="refresh" content="0;url=$deepLink">
</head>
<body style="font-family:sans-serif;text-align:center;padding:48px">
  <h2>$title</h2>
  <p><a href="$deepLink">Return to the app</a></p>
  <script>window.location.href = "$deepLink";</script>
</body>
</html>
""".trimIndent()


@Serializable
data class PortalSessionResponse(val portalUrl: String)