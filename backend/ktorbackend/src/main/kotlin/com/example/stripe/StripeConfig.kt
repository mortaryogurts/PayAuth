package com.example.stripe

import com.example.config.Env
import com.stripe.Stripe

object StripeConfig {
    fun init() {
        Stripe.apiKey = Env.require("STRIPE_SECRET_KEY")
            ?: error("STRIPE_SECRET_KEY environment variable is not set")
    }
}
