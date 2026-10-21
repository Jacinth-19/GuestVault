package com.guestvault.ui.screens.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.guestvault.ui.AppStartState
import com.guestvault.ui.AppViewModel
import kotlinx.coroutines.delay

/**
 * How long the brand splash stays up.
 *
 * The old value (1.15 s) was shorter than the CI screenshot window, which is
 * exactly why `docs/media/00-splash.png` used to capture onboarding instead of
 * the splash. The media workflow now waits for this screen explicitly and
 * `scripts/ci/pick_splash.py` verifies the frame, so the two cannot drift apart
 * silently again.
 */
private const val SPLASH_DURATION_MS = 1_400L

@Composable
fun SplashRoute(
    onReady: (AppStartState) -> Unit,
    viewModel: AppViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        val startState = viewModel.startState()
        // Read the destination while the brand animation plays, then leave.
        delay(SPLASH_DURATION_MS)
        onReady(startState)
    }
    SplashScreen()
}
