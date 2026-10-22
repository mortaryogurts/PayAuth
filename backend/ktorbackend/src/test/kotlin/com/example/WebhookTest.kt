    package com.example

import com.example.db.Payments
import com.example.repository.PlanRepository
import com.example.repository.SubscriptionRepository
import com.example.stripe.StripeGateway
import com.stripe.Stripe
import com.stripe.net.ApiResource
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WEBHOOK_SECRET = "whsec_test_secret"   // must match build.gradle.kts
private const val SUB_ID = "sub_test_1"
private const val PRO_PRICE = "price_pro_test"
private const val SLAVE_PRICE = "price_slave_test"

/** Stripe's signature scheme: HMAC-SHA256 of "<timestamp>.<raw body>". */
private fun sign(payload: String, secret: String = WEBHOOK_SECRET): String {
    val t = System.currentTimeMillis() / 1000
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
    val sig = mac.doFinal("$t.$payload".toByteArray()).joinToString("") { "%02x".format(it) }
    return "t=$t,v1=$sig"
}

private fun envelope(type: String, obj: String) = """
{"id":"evt_${UUID.randomUUID()}","object":"event","api_version":"${Stripe.API_VERSION}",
 "created":1700000000,"livemode":false,"pending_webhooks":1,
 "request":{"id":null,"idempotency_key":null},
 "type":"$type","data":{"object":$obj}}
""".trimIndent()

private fun subscriptionObject(
    priceId: String,
    status: String = "active",
    cancelAtPeriodEnd: Boolean = false,
) = """{"id":"$SUB_ID","object":"subscription","status":"$status",
       "cancel_at_period_end":$cancelAtPeriodEnd,"cancel_at":null,
       "items":{"object":"list","has_more":false,"url":"/v1/subscription_items","data":[
         {"id":"si_1","object":"subscription_item","current_period_start":1700000000,
          "current_period_end":1900000000,"price":{"id":"$priceId","object":"price"}}]}}"""

private fun subscriptionEvent(
    priceId: String,
    status: String = "active",
    cancelAtPeriodEnd: Boolean = false,
) = envelope("customer.subscription.updated", subscriptionObject(priceId, status, cancelAtPeriodEnd))

private fun invoiceEvent(invoiceId: String, subscriptionId: String = SUB_ID) = envelope(
    "invoice.payment_succeeded",
    """{"id":"$invoiceId","object":"invoice","amount_paid":999,
       "parent":{"type":"subscription_details","subscription_details":{"subscription":"$subscriptionId"}}}"""
)

private suspend fun ApplicationTestBuilder.sendWebhook(payload: String, signature: String? = sign(payload)) =
    client.post("/webhooks/stripe") {
        contentType(ContentType.Application.Json)
        if (signature != null) {
            header("Stripe-Signature", signature)
        }
        setBody(payload)
    }

private data class Seed(val userId: UUID, val proPlanId: UUID, val slavePlanId: UUID)

/** Two plans, one registered user, and an active Pro subscription row (as checkout would have created). */
private suspend fun ApplicationTestBuilder.seedPlansAndUser(): Seed {
    val plans = PlanRepository()
    val pro = plans.create("Pro", 999, "month", PRO_PRICE)
    val slave = plans.create("slave", 12000, "month", SLAVE_PRICE)
    val userId = UUID.fromString(client.postJson("/register", creds("w@test.com")).json()["userId"]!!.jsonPrimitive.content)
    return Seed(userId, pro.id, slave.id)
}

private suspend fun ApplicationTestBuilder.seedSubscribedUser(): Seed {
    val seed = seedPlansAndUser()
    SubscriptionRepository().create(
        userId = seed.userId,
        planId = seed.proPlanId,
        status = "active",
        stripeSubscriptionId = SUB_ID,
        currentPeriodEnd = Instant.ofEpochSecond(1_800_000_000)
    )
    return seed
}

private fun checkoutCompletedEvent(userId: UUID?) = envelope(
    "checkout.session.completed",
    """{"id":"cs_test_1","object":"checkout.session","mode":"subscription","subscription":"$SUB_ID",
       "metadata":{${if (userId != null) "\"userId\":\"$userId\"" else ""}}}"""
)

private fun subscriptionCount(): Long = transaction { com.example.db.Subscriptions.selectAll().count() }
private fun paymentCount(): Long = transaction { Payments.selectAll().count() }

class WebhookTest {

    @AfterTest
    fun restoreStripeGateway() {
        StripeGateway.fetchSubscription = { com.stripe.model.Subscription.retrieve(it) }
    }

    @Test
    fun `checkout completed creates the subscription once, even when replayed`() = appTest {
        val seed = seedPlansAndUser()
        StripeGateway.fetchSubscription = {
            ApiResource.GSON.fromJson(subscriptionObject(PRO_PRICE), com.stripe.model.Subscription::class.java)
        }
        val payload = checkoutCompletedEvent(seed.userId)

        assertEquals(HttpStatusCode.OK, sendWebhook(payload).status)
        assertEquals(1, subscriptionCount())
        val sub = SubscriptionRepository().findByStripeSubscriptionId(SUB_ID)!!
        assertEquals(seed.userId, sub.userId)
        assertEquals(seed.proPlanId, sub.planId)
        assertEquals("active", sub.status)

        assertEquals(HttpStatusCode.OK, sendWebhook(payload).status)   // replay
        assertEquals(1, subscriptionCount())
    }

    @Test
    fun `checkout completed without a userId in metadata returns 409 and creates nothing`() = appTest {
        seedPlansAndUser()
        StripeGateway.fetchSubscription = {
            ApiResource.GSON.fromJson(subscriptionObject(PRO_PRICE), com.stripe.model.Subscription::class.java)
        }
        assertEquals(HttpStatusCode.Conflict, sendWebhook(checkoutCompletedEvent(null)).status)
        assertEquals(0, subscriptionCount())
    }

    @Test
    fun `missing or wrong signature is rejected with 400`() = appTest {
        val payload = envelope("customer.created", """{"id":"cus_1","object":"customer"}""")
        assertEquals(HttpStatusCode.BadRequest, sendWebhook(payload, signature = null).status)
        assertEquals(HttpStatusCode.BadRequest, sendWebhook(payload, signature = sign(payload, "whsec_wrong")).status)
    }

    @Test
    fun `validly signed event of an unhandled type is acknowledged`() = appTest {
        val payload = envelope("customer.created", """{"id":"cus_1","object":"customer"}""")
        assertEquals(HttpStatusCode.OK, sendWebhook(payload).status)
    }

    @Test
    fun `subscription updated to another price switches the plan`() = appTest {
        val seed = seedSubscribedUser()

        assertEquals(HttpStatusCode.OK, sendWebhook(subscriptionEvent(SLAVE_PRICE)).status)

        val sub = SubscriptionRepository().findByStripeSubscriptionId(SUB_ID)!!
        assertEquals(seed.slavePlanId, sub.planId)
        assertEquals("active", sub.status)
    }

    @Test
    fun `cancel at period end is stored and the plan is left alone`() = appTest {
        val seed = seedSubscribedUser()

        sendWebhook(subscriptionEvent(PRO_PRICE, cancelAtPeriodEnd = true))

        val sub = SubscriptionRepository().findByStripeSubscriptionId(SUB_ID)!!
        assertTrue(sub.cancelAtPeriodEnd)
        assertEquals(seed.proPlanId, sub.planId)
    }

    @Test
    fun `unknown price still syncs status but keeps the plan`() = appTest {
        val seed = seedSubscribedUser()

        assertEquals(HttpStatusCode.OK, sendWebhook(subscriptionEvent("price_nobody_knows", status = "past_due")).status)

        val sub = SubscriptionRepository().findByStripeSubscriptionId(SUB_ID)!!
        assertEquals("past_due", sub.status)
        assertEquals(seed.proPlanId, sub.planId)
    }

    @Test
    fun `payment succeeded records one payment and a replay does not duplicate it`() = appTest {
        seedSubscribedUser()
        val payload = invoiceEvent("in_test_1")

        assertEquals(HttpStatusCode.OK, sendWebhook(payload).status)
        assertEquals(1, paymentCount())

        // Stripe delivers events more than once; same invoice id must not create a second row
        assertEquals(HttpStatusCode.OK, sendWebhook(payload).status)
        assertEquals(1, paymentCount())
    }

    @Test
    fun `invoice for a subscription we do not have yet returns 409 so Stripe retries`() = appTest {
        seedSubscribedUser()
        val res = sendWebhook(invoiceEvent("in_test_2", subscriptionId = "sub_not_in_db"))
        assertEquals(HttpStatusCode.Conflict, res.status)
        assertEquals(0, paymentCount())
    }
}