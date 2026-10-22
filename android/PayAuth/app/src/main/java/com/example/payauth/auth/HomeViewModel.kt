package com.example.payauth.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.payauth.network.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class HomeState(
    val email: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val sessionExpired: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    fun load() {
        viewModelScope.launch {
            _state.value = HomeState(isLoading = true)
            try {
                val me = api.me()
                _state.value = HomeState(email = me.email, isLoading = false)
            } catch (e: HttpException) {

                _state.value = if (e.code() == 401)
                    HomeState(isLoading = false, sessionExpired = true)
                else
                    HomeState(isLoading = false, error = "Server error (${e.code()})")
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "me() failed", e)
                _state.value = HomeState(isLoading = false, error = "Can't reach the server")
            }
        }
    }
}