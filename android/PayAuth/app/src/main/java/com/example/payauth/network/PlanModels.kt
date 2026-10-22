package com.example.payauth.network

data class PlanResponse(
    val id: String,
    val name: String,
    val priceCents: Int,
    val billingInterval: String,
    val stripePriceId: String
)