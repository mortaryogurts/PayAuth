package com.example.payauth.userinterface

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.payauth.auth.HomeViewModel
import com.example.payauth.auth.SubscriptionViewModel

@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    subscriptionViewModel: SubscriptionViewModel = hiltViewModel()
){
    val state by viewModel.state.collectAsStateWithLifecycle()

    val sub by subscriptionViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { subscriptionViewModel.onReturnToApp() }
    LaunchedEffect(sub.checkoutUrl) {
        sub.checkoutUrl?.let {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(it))
            subscriptionViewModel.checkoutLaunched()
        }
    }
    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onLogout()
    }
    LaunchedEffect(Unit) { subscriptionViewModel.loadPlans() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            state.isLoading -> CircularProgressIndicator()
            state.error != null -> {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = viewModel::load) { Text("Retry") }
            }
            else -> {
                Text("Welcome!", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(state.email ?: "", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                if (sub.active) {
                    Text("Plan: ${sub.planName ?: "Active"}", style = MaterialTheme.typography.titleMedium)
                    if (sub.cancelAtPeriodEnd == true) {
                        Text(
                            "Cancels on ${sub.currentPeriodEnd?.substringBefore("T")}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { subscriptionViewModel.manageSubscription() },
                        enabled = !sub.isLoading
                    ) { Text("Manage subscription") }
                } else if(sub.confirming){
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Confirming your payment...")
                }   else {
                    if(sub.plans.isEmpty()){
                        Text("No plans available", style = MaterialTheme.typography.titleMedium)
                    } else {
                        sub.plans.forEach {plan ->
                            Button(
                                onClick = {subscriptionViewModel.subscribe(plan.stripePriceId)},
                                enabled = !sub.isLoading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${plan.name} · ${"%.2f".format(plan.priceCents / 100.0)} / ${plan.billingInterval}")
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                sub.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Log out")
        }
    }
}