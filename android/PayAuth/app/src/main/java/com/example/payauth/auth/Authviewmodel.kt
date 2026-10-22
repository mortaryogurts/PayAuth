package com.example.payauth.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.payauth.network.ApiService
import com.example.payauth.network.LoginRequest
import com.example.payauth.network.RefreshRequest
import com.example.payauth.network.RegisterRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject


// Represents every state the login screen can be in — the UI reacts to this,
// rather than tracking loading/error booleans separately by hand.
sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenStore
) : ViewModel() {

    private val _hasSession = MutableStateFlow(tokenManager.getToken() != null)
    val hasSession : StateFlow<Boolean> = _hasSession
    var email by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    var uiState: AuthUiState by mutableStateOf(AuthUiState.Idle)
        private set

    fun onEmailChange(value: String) {
        email = value
    }

    fun onPasswordChange(value: String) {
        password = value
    }

    fun login() {
        viewModelScope.launch {
            uiState = AuthUiState.Loading
            uiState = try {
                val response = apiService.login(LoginRequest(email, password))
                tokenManager.saveTokens(response.token, response.refreshToken)
                _hasSession.value = true
                AuthUiState.Success
            } catch (e: HttpException) {
                val message = when (e.code()) {
                    401 -> "Invalid email or password"
                    429 -> "Too many attempts. Please wait a minute and try again"
                    else -> "Server error (${e.code()})"
                }
                AuthUiState.Error(message)
            } catch (e: Exception) {
                AuthUiState.Error("Couldn't reach the server: ${e.message}")
            }
        }
    }

    fun register() {
        viewModelScope.launch {
            uiState = AuthUiState.Loading
            uiState = try {
                val response = apiService.register(RegisterRequest(email, password))
                tokenManager.saveTokens(response.token, response.refreshToken)
                _hasSession.value = true
                AuthUiState.Success
            } catch (e: HttpException) {
                // Mirrors the backend's specific validation responses:
                // 409 = email already taken, 400 = failed validation (bad email/short password).
                val message = when (e.code()) {
                    409 -> "An account with that email already exists"
                    400 -> "Please check your email and use a password of at least 8 characters"
                    429 -> "Too many attempts. Please wait a minute and try again"
                    else -> "Server error (${e.code()})"
                }
                AuthUiState.Error(message)
            } catch (e: Exception) {
                AuthUiState.Error("Couldn't reach the server: ${e.message}")
            }
        }
    }

    // Called when switching between Login/Register screens, so a leftover
    // error or Loading state from one screen doesn't bleed into the other.
    fun resetState() {
        uiState = AuthUiState.Idle
    }

    fun logout(){
        val refresh = tokenManager.getRefreshToken()
        tokenManager.clearToken()
        if(refresh!=null){
            viewModelScope.launch {
                try{
                    apiService.logout(RefreshRequest(refresh))
                }catch (e: Exception){
                    // ignore
                }
            }
        }
    }
}