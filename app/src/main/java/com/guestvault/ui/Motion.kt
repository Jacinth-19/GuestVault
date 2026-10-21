package com.guestvault.ui

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Whether the user has asked the system to remove animations.
 *
 * This is the same signal Compose and Lottie use internally, exposed as a
 * boolean an app can *branch* on. That matters for two reasons:
 *
 *  - Compose applies the duration scale by making `tween(...)` instantaneous. For
 *    a one-shot animation that is exactly right. For an **infinite** one it is
 *    ambiguous: the animation may sample its start, its end, or thrash between
 *    iterations depending on when the frame lands. Anything that must look
 *    correct with animators off has to stop relying on the animated value and
 *    pick a fixed one.
 *  - Lottie multiplies its playback speed by the same scale, so an "animate once"
 *    composition can sit on frame 0 (a blank first frame) instead of holding its
 *    final frame. The onboarding pages explicitly ask for the settled frame.
 *
 * `ValueAnimator.areAnimatorsEnabled()` is the documented check (API 26+, and
 * this app's minSdk is 29), and it covers both the developer "animator duration
 * scale = 0" setting and accessibility "Remove animations".
 *
 * The value is re-read on every `ON_RESUME`, because the setting can change
 * while the app is backgrounded — reading it once into `remember` would keep a
 * stale answer for the rest of the process.
 */
@Composable
fun rememberAnimationsDisabled(): Boolean {
    var disabled by remember { mutableStateOf(!animatorsEnabled()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                disabled = !animatorsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return disabled
}

/**
 * Never let a device quirk turn "reduced motion" into a crash: if the platform
 * refuses to answer, assume animations are on.
 */
private fun animatorsEnabled(): Boolean =
    runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
