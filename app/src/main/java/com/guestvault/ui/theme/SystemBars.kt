package com.guestvault.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Walks a (possibly themed) context back to its Activity. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Paints the status/navigation bar icons white for as long as this composable is
 * on screen, then restores whatever the theme had set.
 *
 * The splash and the guest session both use a deep indigo background, where the
 * theme's default dark icons would be nearly invisible (about 1.7:1). Restoring
 * on dispose matters: the owner dashboard that follows is light and needs the
 * dark icons back.
 */
@Composable
fun LightSystemBarIcons() {
    val view = LocalView.current
    val context = LocalContext.current

    DisposableEffect(view) {
        val activity = context.findActivity()
        if (activity == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(activity.window, view)
            val previousStatus = controller.isAppearanceLightStatusBars
            val previousNavigation = controller.isAppearanceLightNavigationBars

            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false

            onDispose {
                controller.isAppearanceLightStatusBars = previousStatus
                controller.isAppearanceLightNavigationBars = previousNavigation
            }
        }
    }
}
