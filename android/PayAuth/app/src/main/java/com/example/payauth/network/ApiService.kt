package com.example.payauth.network

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @GET("health")
    suspend fun healthCheck(): ResponseBody

    @POST("login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @GET("me")
    suspend fun me(): MeResponse

    @POST("subscribe")
    suspend fun subscribe(@Body request: CreateCheckoutSessionRequest) : CheckoutSessionResponse

    @GET("subscription-status")
    suspend fun subscriptionStatus() : SubscriptionStatusResponse

    @GET("plans")
    suspend fun getPlans(): List<PlanResponse>

    @POST("billing-portal")
    suspend fun billingPortal(): PortalSessionResponse

    @POST("logout")
    suspend fun logout(@Body body: RefreshRequest): retrofit2.Response<Unit>
}