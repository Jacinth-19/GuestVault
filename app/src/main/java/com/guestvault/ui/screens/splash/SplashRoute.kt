package com.guestvault.ui.screens.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
    /**
     * Debug-only override used by the screenshot pipeline. The splash is drawn
     * identically; only the timer changes, so the capture can photograph the
     * screen instead of racing it. Release builds pass 0.
     */
    holdMs: Int = 0,
    viewModel: AppViewModel = hiltViewModel()
) {
    val duration = remember(holdMs) {
        if (holdMs.toLong() > SPLASH_DURATION_MS) holdMs.toLong() else SPLASH_DURATION_MS
    }

    LaunchedEffect(duration) {
        val startState = viewModel.startState()
        // Read the destination while the brand animation plays, then leave.
        delay(duration)
        onReady(startState)
    }
    SplashScreen()
}
