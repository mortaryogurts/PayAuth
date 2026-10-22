package com.example.payauth.network

import kotlinx.serialization.Serializable

@Serializable
data class CreateCheckoutSessionRequest(val stripePriceId: String)
@Serializable
data class CheckoutSessionResponse(val checkoutUrl: String)
@Serializable
data class SubscriptionStatusResponse(
    val active: Boolean,
    val status: String? = null,
    val planName: String? = null,
    val currentPeriodEnd: String? = null,
    val cancelAtPeriodEnd: Boolean? = false
)

data class PortalSessionResponse(val portalUrl: String)