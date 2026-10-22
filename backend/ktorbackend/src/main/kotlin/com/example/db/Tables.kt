package com.example.db

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp

object Users : UUIDTable("users"){
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAt = timestamp("created_at")
}

object Plans : UUIDTable("plans") {
    val name = varchar("name", 100)
    val priceCents = integer("price_cents")
    val billingInterval = varchar("billing_interval", 20)
    val stripePriceId = varchar("stripe_price_id", 255).uniqueIndex()
}

object Subscriptions : UUIDTable("subscriptions") {
    val userId = reference("user_id", Users)
    val planId = reference("plan_id", Plans)
    val status = varchar("status", 20)
    val stripeSubscriptionId = varchar("stripe_subscription_id", 255).uniqueIndex()
    val currentPeriodEnd = timestamp("current_period_end")
    val cancelAtPeriodEnd = bool("cancel_at_period_end").default(false)
}

object Payments : UUIDTable("payments") {
    val userId = reference("user_id", Users)
    val subscriptionId = reference("subscription_id", Subscriptions)
    val amountCents = integer("amount_cents")
    val status = varchar("status", 20)
    val stripePaymentId = varchar("stripe_payment_id", 255).uniqueIndex()
    val createdAt = timestamp("created_at")
}