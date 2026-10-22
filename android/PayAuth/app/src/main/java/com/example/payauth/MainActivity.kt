package com.example.payauth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.payauth.auth.AuthViewModel
import com.example.payauth.auth.TokenManager
import com.example.payauth.userinterface.HomeScreen
import com.example.payauth.userinterface.LoginScreen
import com.example.payauth.userinterface.RegisterScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    // decided once: a saved token means the user is already logged in
                    val startDestination = remember {
                        if (tokenManager.getToken() != null) Routes.HOME else Routes.LOGIN
                    }

                    NavHost(navController = navController, startDestination = startDestination) {

                        composable(Routes.LOGIN) {
                            LoginScreen(
                                onLoginSuccess = {
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(Routes.LOGIN) { inclusive = true }
                                    }
                                },
                                onNavigateToRegister = { navController.navigate(Routes.REGISTER) }
                            )
                        }

                        composable(Routes.REGISTER) {
                            RegisterScreen(
                                onRegisterSuccess = {
                                    // removes Register and Login, so Back can't return to them
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(Routes.LOGIN) { inclusive = true }
                                    }
                                },
                                onNavigateToLogin = { navController.popBackStack() }
                            )
                        }

                        composable(Routes.HOME) {
                            val authViewModel: AuthViewModel = hiltViewModel()
                            HomeScreen(
                                onLogout = {
                                    authViewModel.logout()
                                    navController.navigate(Routes.LOGIN) {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}