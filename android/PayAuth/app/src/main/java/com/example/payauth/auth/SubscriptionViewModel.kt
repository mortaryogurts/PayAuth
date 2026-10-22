package com.example.payauth.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.payauth.network.ApiService
import com.example.payauth.network.CreateCheckoutSessionRequest
import com.example.payauth.network.PlanResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionUiState(
    val isLoading: Boolean = false,
    val confirming: Boolean = false,
    val active: Boolean = false,
    val planName: String? = null,
    val status: String? = null,
    val checkoutUrl: String? = null,
    val error: String? = null,
    val plans: List<PlanResponse> = emptyList(),
    val cancelAtPeriodEnd: Boolean? = false,
    val currentPeriodEnd: String? = null
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val api: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(SubscriptionUiState())
    val state: StateFlow<SubscriptionUiState> = _state

    // true after the user starts checkout, so coming back waits for the webhook
    private var awaitingPayment = false

    fun loadStatus() {
        viewModelScope.launch { fetchStatus() }
    }

    private suspend fun fetchStatus(): Boolean {
        return try {
            val response = api.subscriptionStatus()
            _state.update {
                it.copy(
                    active = response.active,
                    planName = response.planName,
                    status = response.status,
                    cancelAtPeriodEnd = response.cancelAtPeriodEnd,
                    currentPeriodEnd = response.currentPeriodEnd,
                    error = null
                )
            }
            response.active
        } catch (e: Exception) {
            android.util.Log.e("SubVM", "status failed", e)
            _state.update { it.copy(error = "Couldn't load subscription status") }
            false
        }
    }

    fun loadPlans() {
        viewModelScope.launch {
            try {
                val plans = api.getPlans()
                _state.update { it.copy(plans = plans) }
            } catch (e: Exception) {
                android.util.Log.e("SubVM", "plans failed", e)
                _state.update { it.copy(error = "Couldn't load plans") }
            }
        }
    }

    fun subscribe(stripePriceId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val r = api.subscribe(CreateCheckoutSessionRequest(stripePriceId))
                awaitingPayment = true
                _state.update { it.copy(isLoading = false, checkoutUrl = r.checkoutUrl) }
            } catch (e: retrofit2.HttpException) {
                android.util.Log.e("SubVM", "subscribe failed: ${e.code()} ${e.response()?.errorBody()?.string()}")
                if (e.code() == 409) {
                    fetchStatus()
                    _state.update { it.copy(isLoading = false, error = "You already have an active subscription") }
                } else {
                    _state.update { it.copy(isLoading = false, error = "Couldn't start checkout (${e.code()})") }
                }
            } catch (e: Exception) {
                android.util.Log.e("SubVM", "subscribe failed", e)
                _state.update { it.copy(isLoading = false, error = "Couldn't start checkout") }
            }
        }
    }

    // opens Stripe's Customer Portal; reuses checkoutUrl because HomeScreen already opens that URL
    fun manageSubscription() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val r = api.billingPortal()
                _state.update { it.copy(isLoading = false, checkoutUrl = r.portalUrl) }
            } catch (e: retrofit2.HttpException) {
                android.util.Log.e("SubVM", "portal failed: ${e.code()} ${e.response()?.errorBody()?.string()}")
                _state.update { it.copy(isLoading = false, error = "Couldn't open billing (${e.code()})") }
            } catch (e: Exception) {
                android.util.Log.e("SubVM", "portal failed", e)
                _state.update { it.copy(isLoading = false, error = "Couldn't open billing") }
            }
        }
    }

    fun checkoutLaunched() {
        _state.update { it.copy(checkoutUrl = null) }
    }

    // called whenever Home comes back to the front
    fun onReturnToApp() {
        if (!awaitingPayment) {
            loadStatus()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(confirming = true) }
            repeat(10) {
                if (fetchStatus()) {
                    awaitingPayment = false
                    _state.update { it.copy(confirming = false) }
                    return@launch
                }
                delay(1500)
            }
            awaitingPayment = false
            _state.update { it.copy(confirming = false) }
        }
    }
}