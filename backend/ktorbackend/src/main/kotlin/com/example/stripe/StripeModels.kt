package com.example.stripe

import kotlinx.serialization.Serializable

@Serializable
data class CreateCheckoutSessionRequest(val stripePriceId: String)

@Serializable
data class CheckoutSessionResponse(val checkoutUrl: String)