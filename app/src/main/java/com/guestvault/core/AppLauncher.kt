package com.guestvault.core

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Launches apps on behalf of the guest.
 *
 * The guest UI only ever calls this for packages that are part of the active
 * session's allow-list; [SessionController][com.guestvault.data.session.SessionController]
 * is the only writer of that list.
 */
@Singleton
class AppLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun launch(packageName: String): Result<Unit> = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalStateException("No launcher activity for $packageName")
        // NEW_TASK is required because we are starting from an application context.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure { Log.w(TAG, "Could not launch $packageName", it) }

    fun isLaunchable(packageName: String): Boolean =
        runCatching { context.packageManager.getLaunchIntentForPackage(packageName) != null }
            .getOrDefault(false)

    private companion object {
        const val TAG = "AppLauncher"
    }
}
