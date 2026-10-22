package com.example.payauth.auth

import com.example.payauth.FakeApiService
import com.example.payauth.MainDispatcherRule
import com.example.payauth.httpError
import com.example.payauth.network.CheckoutSessionResponse
import com.example.payauth.network.PlanResponse
import com.example.payauth.network.PortalSessionResponse
import com.example.payauth.network.SubscriptionStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SubscriptionViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val api = FakeApiService()
    private fun viewModel() = SubscriptionViewModel(api)

    @Test
    fun `loadStatus updates state on success`() {
        api.onSubscriptionStatus = {
            SubscriptionStatusResponse(
                active = true,
                status = "active",
                planName = "Pro",
                currentPeriodEnd = "2025-12-31T00:00:00Z",
                cancelAtPeriodEnd = false
            )
        }
        val vm = viewModel()
        vm.loadStatus()

        val state = vm.state.value
        assertTrue(state.active)
        assertEquals("Pro", state.planName)
        assertEquals("active", state.status)
        assertNull(state.error)
    }

    @Test
    fun `loadPlans updates state on success`() {
        val plansList = listOf(
            PlanResponse("p1", "Pro", 1000, "month", "price_123")
        )
        api.onGetPlans = { plansList }
        val vm = viewModel()
        vm.loadPlans()

        assertEquals(plansList, vm.state.value.plans)
    }

    @Test
    fun `subscribe sets checkoutUrl on success`() {
        api.onSubscribe = { CheckoutSessionResponse("https://checkout.stripe.com/pay/123") }
        val vm = viewModel()
        vm.subscribe("price_123")

        assertEquals("https://checkout.stripe.com/pay/123", vm.state.value.checkoutUrl)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `subscribe handles 409 conflict`() {
        api.onSubscribe = { throw httpError(409) }
        api.onSubscriptionStatus = { SubscriptionStatusResponse(active = true) }
        val vm = viewModel()
        vm.subscribe("price_123")

        assertEquals("You already have an active subscription", vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `manageSubscription sets portal url`() {
        api.onBillingPortal = { PortalSessionResponse("https://billing.stripe.com/123") }
        val vm = viewModel()
        vm.manageSubscription()

        assertEquals("https://billing.stripe.com/123", vm.state.value.checkoutUrl)
    }

    @Test
    fun `checkoutLaunched clears checkoutUrl`() {
        api.onSubscribe = { CheckoutSessionResponse("https://checkout.stripe.com/pay/123") }
        val vm = viewModel()
        vm.subscribe("price_123")

        vm.checkoutLaunched()

        assertNull(vm.state.value.checkoutUrl)
    }
}
