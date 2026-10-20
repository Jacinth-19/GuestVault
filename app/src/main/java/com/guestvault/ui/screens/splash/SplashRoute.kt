package com.guestvault.ui.screens.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.guestvault.ui.AppStartState
import com.guestvault.ui.AppViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashRoute(
    onReady: (AppStartState) -> Unit,
    viewModel: AppViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        val startState = viewModel.startState()
        // Keep the splash on screen long enough for the animation to breathe.
        delay(1_150)
        onReady(startState)
    }
    SplashScreen()
}
