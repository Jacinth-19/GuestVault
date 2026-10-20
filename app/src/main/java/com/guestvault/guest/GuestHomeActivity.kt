package com.guestvault.guest

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guestvault.MainActivity
import com.guestvault.ui.theme.GuestVaultTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The restricted "guest home". It is a normal activity, but it only composes
 * the guest UI: no app drawer, no settings, no access to the owner dashboard.
 *
 * It lives in its own task (`com.guestvault.guest`) so the owner's navigation
 * state survives a session.
 */
@AndroidEntryPoint
class GuestHomeActivity : FragmentActivity() {

    private var returningToOwner = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: GuestViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            GuestVaultTheme(themeMode = state.settings.themeMode) {
                GuestHomeRoute(
                    state = state,
                    activity = this@GuestHomeActivity,
                    viewModel = viewModel,
                    onLaunchApp = viewModel::launchApp,
                    onEndSession = viewModel::endSession,
                    onReturnToOwner = ::returnToOwner
                )
            }
        }
    }

    fun isInLockTaskMode(): Boolean {
        val activityManager = getSystemService(ActivityManager::class.java) ?: return false
        return activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    /**
     * Requests Android's screen pinning. The first time, the system shows a
     * one-time confirmation ("Pin GuestVault to this screen?"). If the user
     * declines, the soft lock (our own UI) is still in place.
     */
    fun startPinnedMode() {
        if (isInLockTaskMode()) return
        runCatching { startLockTask() }
            .onFailure { Log.w(TAG, "Screen pinning unavailable", it) }
    }

    fun returnToOwner() {
        if (returningToOwner) return
        returningToOwner = true

        if (isInLockTaskMode()) {
            runCatching { stopLockTask() }
                .onFailure { Log.w(TAG, "Could not stop lock task", it) }
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finishAndRemoveTask()
    }

    private companion object {
        const val TAG = "GuestHomeActivity"
    }
}
