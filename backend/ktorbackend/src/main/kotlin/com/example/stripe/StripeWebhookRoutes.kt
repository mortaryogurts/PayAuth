package com.example.stripe

import com.example.config.Env
import com.example.repository.PaymentRepository
import com.example.repository.PlanRepository
import com.example.repository.SubscriptionRepository
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Event
import com.stripe.model.Invoice
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.UUID
import com.stripe.model.Subscription as StripeSubscription
/** Seam for tests: production calls Stripe, tests swap in a fake. */
object StripeGateway {
    var fetchSubscription: (String) -> StripeSubscription = { StripeSubscription.retrieve(it) }
}
fun Route.stripeWebhookRoutes(
    planRepository: PlanRepository,
    subscriptionRepository: SubscriptionRepository,
    paymentRepository: PaymentRepository
) {
    post("/webhooks/stripe") {
        val payload = call.receiveText()
        val sigHeader = call.request.header("Stripe-Signature")
        if(sigHeader == null){
            call.respond(HttpStatusCode.BadRequest, "Missing Signature")
            return@post
        }
        val webhookSecret = Env.require("STRIPE_WEBHOOK_SECRET")

        val event: Event = try {
            Webhook.constructEvent(payload, sigHeader, webhookSecret)
        } catch (e: SignatureVerificationException) {
            println("Webhook Signature Failed: ${e.message}")
            call.respond(HttpStatusCode.BadRequest, "Invalid signature")
            return@post
        }

        val handled: Boolean = when (event.type) {
            "checkout.session.completed" -> {
                val session = event.dataObjectDeserializer.getObject().orElse(null) as? Session
                session?.let { handleCheckoutSessionCompleted(it, planRepository, subscriptionRepository) } ?: false
            }

            "customer.subscription.updated", "customer.subscription.deleted" -> {
                val stripeSub = event.dataObjectDeserializer.getObject().orElse(null) as? StripeSubscription
                stripeSub?.let { handleSubscriptionUpdated(it,planRepository, subscriptionRepository) } ?: false
            }

            "invoice.payment_succeeded" -> {
                val invoice = event.dataObjectDeserializer.getObject().orElse(null) as? Invoice
                invoice?.let { handleInvoicePaymentSucceeded(it, subscriptionRepository, paymentRepository) } ?: false
            }

            else -> true
        }

        if (handled) {
            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(HttpStatusCode.Conflict, "Dependency not yet available, please retry")
        }
    }
}

private suspend fun handleCheckoutSessionCompleted(
    session: Session,
    planRepository: PlanRepository,
    subscriptionRepository: SubscriptionRepository
): Boolean {
    val userId = session.metadata?.get("userId") ?: return false
    val stripeSubscriptionId = session.subscription ?: return false

    val stripeSub = StripeGateway.fetchSubscription(stripeSubscriptionId)
    val firstItem = stripeSub.items.data.firstOrNull() ?: return false
    val priceId = firstItem.price?.id ?: return false
    val plan = planRepository.findByStripePriceId(priceId) ?: return false

    val currentPeriodEnd = Instant.ofEpochSecond(firstItem.currentPeriodEnd)

    val existing = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
    if (existing == null) {
        subscriptionRepository.create(
            userId = UUID.fromString(userId),
            planId = plan.id,
            status = stripeSub.status,
            stripeSubscriptionId = stripeSubscriptionId,
            currentPeriodEnd = currentPeriodEnd
        )
    }
    return true
}

private suspend fun handleSubscriptionUpdated(
    stripeSub: StripeSubscription,
    planRepository: PlanRepository,
    subscriptionRepository: SubscriptionRepository
): Boolean {
    val firstItem = stripeSub.items.data.firstOrNull() ?: return false
    val currentPeriodEnd = Instant.ofEpochSecond(firstItem.currentPeriodEnd)

    val cancelling = stripeSub.cancelAtPeriodEnd == true || stripeSub.cancelAt != null
    val priceId = firstItem.price?.id
    val plan = priceId?.let { planRepository.findByStripePriceId(it) }
    if(plan==null){
        // Unknown price: still sync status, but don't touch plan_id (and say so)
        println("Webhook: no plan found for price $priceId on subscription ${stripeSub.id}")
    }
        val updated = subscriptionRepository.updateStatus(
        stripeSubscriptionId = stripeSub.id,
        newStatus = stripeSub.status,
        newCurrentPeriodEnd = currentPeriodEnd,
        newCancelAtPeriodEnd = cancelling,
            newPlanId = plan?.id
    )
    println("sub.updated id=${stripeSub.id} price=$priceId plan=${plan?.name} rowsUpdated=$updated")
    return true
}

private suspend fun handleInvoicePaymentSucceeded(
    invoice: Invoice,
    subscriptionRepository: SubscriptionRepository,
    paymentRepository: PaymentRepository
): Boolean {
    val stripeSubscriptionId = invoice.parent?.subscriptionDetails?.subscription ?: return false

    // checkout.session.completed writes the subscription row, but Ktor processes
    // webhook requests concurrently, so that write might not have landed yet when
    // this handler runs. Rather than rely on Stripe's own retry schedule (which
    // can take minutes to hours — too slow to matter here), retry the lookup
    // ourselves a few times with a short pause. This resolves in well under a
    // second in practice, since it's just waiting on our own database.
    var subscription = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
    var attempts = 0
    while (subscription == null && attempts < 5) {
        delay(300)
        subscription = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
        attempts++
    }
    if (subscription == null) return false

    val existing = paymentRepository.findByStripePaymentId(invoice.id)
    if (existing == null) {
        paymentRepository.create(
            userId = subscription.userId,
            subscriptionId = subscription.id,
            amountCents = invoice.amountPaid.toInt(),
            status = "succeeded",
            stripePaymentId = invoice.id
        )
    }
    return true
}