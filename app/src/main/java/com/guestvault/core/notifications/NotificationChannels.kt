package com.guestvault.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import com.guestvault.R

/**
 * The four notification channels GuestVault owns.
 *
 * They are deliberately separate so the owner can silence the chatty ones
 * (summaries) without losing the ones that matter (security, the ongoing
 * session notice). Android treats channels as user property: once created, only
 * the user can change importance, so these values are the *defaults*.
 */
enum class GuestChannel(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val importance: Int,
    val showBadge: Boolean = false
) {
    /** Ongoing, silent, non-dismissable while a session runs. */
    SESSION(
        id = "guest_session",
        nameRes = R.string.channel_session_name,
        descriptionRes = R.string.channel_session_description,
        importance = NotificationManager.IMPORTANCE_LOW
    ),

    /** "5 minutes left" / "1 minute left". Informational, no vibration. */
    ALERTS(
        id = "session_alerts",
        nameRes = R.string.channel_alerts_name,
        descriptionRes = R.string.channel_alerts_description,
        importance = NotificationManager.IMPORTANCE_DEFAULT
    ),

    /** Post-session recap. Off by default in Settings, because some owners find it noise. */
    SUMMARY(
        id = "session_summary",
        nameRes = R.string.channel_summary_name,
        descriptionRes = R.string.channel_summary_description,
        importance = NotificationManager.IMPORTANCE_DEFAULT
    ),

    /** Failed unlock attempts. Heads-up, only ever about the owner lock. */
    SECURITY(
        id = "security_alerts",
        nameRes = R.string.channel_security_name,
        descriptionRes = R.string.channel_security_description,
        importance = NotificationManager.IMPORTANCE_HIGH
    );

    companion object {
        fun createAll(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            entries.forEach { channel ->
                // Re-creating an existing channel is a no-op for importance; it
                // only refreshes name/description, which is what we want after
                // a translation change.
                manager.createNotificationChannel(channel.toChannel(context))
            }
        }

        /** True when the user has not switched this channel off in Android settings. */
        fun isEnabled(context: Context, channel: GuestChannel): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            return manager.getNotificationChannel(channel.id)?.importance != NotificationManager.IMPORTANCE_NONE
        }
    }

    private fun toChannel(context: Context): NotificationChannel =
        NotificationChannel(id, context.getString(nameRes), importance).apply {
            description = context.getString(descriptionRes)
            setShowBadge(showBadge)
            enableVibration(importance >= NotificationManager.IMPORTANCE_DEFAULT)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
}
