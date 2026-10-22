package com.example.payauth

import com.example.payauth.auth.TokenStore
import com.example.payauth.network.ApiService
import com.example.payauth.network.AuthResponse
import com.example.payauth.network.CheckoutSessionResponse
import com.example.payauth.network.CreateCheckoutSessionRequest
import com.example.payauth.network.LoginRequest
import com.example.payauth.network.MeResponse
import com.example.payauth.network.PlanResponse
import com.example.payauth.network.PortalSessionResponse
import com.example.payauth.network.RefreshRequest
import com.example.payauth.network.RegisterRequest
import com.example.payauth.network.SubscriptionStatusResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

fun httpError(code: Int): HttpException {
    return HttpException(Response.error<Any>(code, "".toResponseBody(null)))
}

class FakeTokenStore : TokenStore {
    var access: String? = null
    var refresh: String? = null

    override fun saveToken(token: String) {
        access = token
    }

    override fun saveTokens(accessToken: String, refreshToken: String) {
        access = accessToken
        refresh = refreshToken
    }

    override fun getToken(): String? = access

    override fun getRefreshToken(): String? = refresh

    override fun clearToken() {
        access = null
        refresh = null
    }
}

class FakeApiService : ApiService {
    var onHealthCheck: () -> ResponseBody = { throw NotImplementedError() }
    var onLogin: (LoginRequest) -> AuthResponse = { throw NotImplementedError() }
    var onRegister: (RegisterRequest) -> AuthResponse = { throw NotImplementedError() }
    var onMe: () -> MeResponse = { throw NotImplementedError() }
    var onSubscribe: (CreateCheckoutSessionRequest) -> CheckoutSessionResponse = { throw NotImplementedError() }
    var onSubscriptionStatus: () -> SubscriptionStatusResponse = { throw NotImplementedError() }
    var onGetPlans: () -> List<PlanResponse> = { throw NotImplementedError() }
    var onBillingPortal: () -> PortalSessionResponse = { throw NotImplementedError() }
    var onLogout: (RefreshRequest) -> Response<Unit> = { Response.success(Unit) }

    val logoutRequests = mutableListOf<RefreshRequest>()

    override suspend fun healthCheck(): ResponseBody = onHealthCheck()

    override suspend fun login(request: LoginRequest): AuthResponse = onLogin(request)

    override suspend fun register(request: RegisterRequest): AuthResponse = onRegister(request)

    override suspend fun me(): MeResponse = onMe()

    override suspend fun subscribe(request: CreateCheckoutSessionRequest): CheckoutSessionResponse = onSubscribe(request)

    override suspend fun subscriptionStatus(): SubscriptionStatusResponse = onSubscriptionStatus()

    override suspend fun getPlans(): List<PlanResponse> = onGetPlans()

    override suspend fun billingPortal(): PortalSessionResponse = onBillingPortal()

    override suspend fun logout(body: RefreshRequest): Response<Unit> {
        logoutRequests.add(body)
        return onLogout(body)
    }
}
