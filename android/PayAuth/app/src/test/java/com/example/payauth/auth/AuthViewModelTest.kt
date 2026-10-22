package com.example.payauth.auth

import com.example.payauth.FakeApiService
import com.example.payauth.FakeTokenStore
import com.example.payauth.MainDispatcherRule
import com.example.payauth.httpError
import com.example.payauth.network.AuthResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class AuthViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val api = FakeApiService()
    private val tokens = FakeTokenStore()
    private fun viewModel() = AuthViewModel(api, tokens).apply {
        onEmailChange("a@test.com")
        onPasswordChange("password123")
    }

    private val authResponse = AuthResponse(
        token = "access-1", refreshToken = "refresh-1", userId = "u1", email = "a@test.com"
    )

    @Test
    fun `login success saves both tokens and starts a session`() {
        api.onLogin = { authResponse }
        val vm = viewModel()
        assertFalse(vm.hasSession.value)

        vm.login()

        assertEquals(AuthUiState.Success, vm.uiState)
        assertEquals("access-1", tokens.access)
        assertEquals("refresh-1", tokens.refresh)
        assertTrue(vm.hasSession.value)
    }

    @Test
    fun `login maps 401 and 429 to readable messages`() {
        val vm = viewModel()

        api.onLogin = { throw httpError(401) }
        vm.login()
        assertEquals(AuthUiState.Error("Invalid email or password"), vm.uiState)

        api.onLogin = { throw httpError(429) }
        vm.login()
        assertEquals(AuthUiState.Error("Too many attempts. Please wait a minute and try again"), vm.uiState)
        assertNull(tokens.access)
    }

    @Test
    fun `login reports an unreachable server`() {
        api.onLogin = { throw IOException("timeout") }
        val vm = viewModel()
        vm.login()
        val state = vm.uiState
        assertTrue(state is AuthUiState.Error && state.message.startsWith("Couldn't reach the server"))
    }

    @Test
    fun `register maps 409 and 400`() {
        val vm = viewModel()

        api.onRegister = { throw httpError(409) }
        vm.register()
        assertEquals(AuthUiState.Error("An account with that email already exists"), vm.uiState)

        api.onRegister = { throw httpError(400) }
        vm.register()
        assertEquals(
            AuthUiState.Error("Please check your email and use a password of at least 8 characters"),
            vm.uiState
        )
    }

    @Test
    fun `logout clears tokens locally and revokes the refresh token on the server`() {
        tokens.saveTokens("access-1", "refresh-1")
        val vm = viewModel()

        vm.logout()

        assertNull(tokens.access)
        assertNull(tokens.refresh)
        assertEquals("refresh-1", api.logoutRequests.single().refreshToken)
    }

    @Test
    fun `logout still clears tokens when the server call fails`() {
        tokens.saveTokens("access-1", "refresh-1")
        api.onLogout = { throw IOException("offline") }
        val vm = viewModel()

        vm.logout()

        assertNull(tokens.access)
        assertNull(tokens.refresh)
    }
}
