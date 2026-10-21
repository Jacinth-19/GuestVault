package com.guestvault.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.guestvault.MainActivity
import com.guestvault.R
import com.guestvault.core.notifications.GuestChannel
import com.guestvault.domain.model.EndReason
import com.guestvault.domain.model.SessionRecord
import com.guestvault.domain.model.WipeResult
import com.guestvault.guest.GuestHomeActivity

/**
 * Everything GuestVault is allowed to put in the notification shade.
 *
 * Four channels, each with one job (see [GuestChannel]):
 *
 *  - [GuestChannel.SESSION]  — the ongoing "Guest Mode is on" notice. Silent,
 *    non-dismissable, and the only one a guest will notice.
 *  - [GuestChannel.ALERTS]   — "5 minutes left" / "1 minute left".
 *  - [GuestChannel.SUMMARY]  — a recap once the session has ended.
 *  - [GuestChannel.SECURITY] — repeated wrong owner PINs.
 *
 * Privacy rules that apply to every notification here:
 *  - no package names, no app list, no guest identity;
 *  - `VISIBILITY_PRIVATE`, so a locked screen shows the app name only;
 *  - no content is ever deleted from the device because of a notification.
 *
 * Posting is best-effort: a revoked permission or a disabled channel must never
 * break a session, so every call is wrapped by [post].
 */
object GuestNotifications {

    const val ID_SESSION = 4711
    const val ID_SECURITY = 4712
    const val ID_SUMMARY = 4713

    /** Timer alerts get one id per threshold so two can coexist briefly. */
    private fun alertId(minutes: Int) = 4720 + minutes

    fun ensureChannels(context: Context) = GuestChannel.createAll(context)

    // ------------------------------------------------------------------ session

    fun buildSessionNotification(
        context: Context,
        remainingLabel: String?
    ): Notification {
        val text = if (remainingLabel != null) {
            context.getString(R.string.notif_session_text_timed, remainingLabel)
        } else {
            context.getString(R.string.notif_session_text_open)
        }

        return NotificationCompat.Builder(context, GuestChannel.SESSION.id)
            .setSmallIcon(R.drawable.ic_status_shield)
            .setContentTitle(context.getString(R.string.notif_session_title))
            .setContentText(text)
            .setContentIntent(openGuestHome(context))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setShowWhen(false)
            // Deliberately no progress bar: in the collapsed shade layout it
            // replaces the text line, and "Ends in 14:31" is the only reason a
            // guest or owner looks at this notification.
            .build()
    }

    fun updateSessionNotification(
        context: Context,
        remainingLabel: String?
    ) {
        post(
            context,
            GuestChannel.SESSION,
            ID_SESSION,
            buildSessionNotification(context, remainingLabel)
        )
    }

    // ------------------------------------------------------------------- alerts

    /** "5 minutes left" / "1 minute left", posted once per threshold. */
    fun notifyTimerReminder(context: Context, minutesLeft: Int) {
        val title = if (minutesLeft <= 1) {
            context.getString(R.string.notif_alert_title_minute)
        } else {
            context.getString(R.string.notif_alert_title_minutes, minutesLeft)
        }

        // "End now" cannot end anything by itself — the owner PIN is the whole
        // point of the lock. It opens the same PIN pad the guest sees.
        val endNow = PendingIntent.getActivity(
            context,
            2,
            lockScreenIntent(context),
            immutableFlags()
        )

        val notification = NotificationCompat.Builder(context, GuestChannel.ALERTS.id)
            .setSmallIcon(R.drawable.ic_status_timer)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_alert_text))
            .setContentIntent(openLockScreen(context))
            .addAction(0, context.getString(R.string.notif_action_end_now), endNow)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        post(context, GuestChannel.ALERTS, alertId(minutesLeft), notification)
    }

    /** Removes any reminder that is still in the shade once the session ends. */
    fun clearTimerReminders(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        com.guestvault.core.notifications.TimerReminders.THRESHOLDS.forEach { minutes ->
            manager.cancel(alertId(minutes))
        }
    }

    // ------------------------------------------------------------------ summary

    /** A recap for the owner, posted after the session has already ended. */
    fun notifySessionSummary(
        context: Context,
        record: SessionRecord,
        wipeResult: WipeResult?,
        enabled: Boolean
    ) {
        if (!enabled) return

        val wiped = if (wipeResult != null) {
            context.getString(R.string.notif_summary_wiped, wipeResult.readableSize())
        } else {
            context.getString(R.string.notif_summary_not_wiped)
        }
        // "18 min · 4 apps · 12 MB cleared · returned by owner"
        val text = context.getString(
            R.string.notif_summary_text,
            Formatters.duration(record.durationMillis),
            record.appCount,
            wiped,
            context.getString(record.endReason.summaryRes())
        )

        val notification = NotificationCompat.Builder(context, GuestChannel.SUMMARY.id)
            .setSmallIcon(R.drawable.ic_status_summary)
            .setContentTitle(context.getString(R.string.notif_summary_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openHistory(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        post(context, GuestChannel.SUMMARY, ID_SUMMARY, notification)
    }

    // ----------------------------------------------------------------- security

    /** Heads-up after repeated wrong PINs. Never contains a hint about the PIN. */
    fun notifyLockout(context: Context, failedAttempts: Int, lockSeconds: Int) {
        val notification = NotificationCompat.Builder(context, GuestChannel.SECURITY.id)
            .setSmallIcon(R.drawable.ic_status_alert)
            .setContentTitle(context.getString(R.string.notif_security_title))
            .setContentText(
                context.getString(R.string.notif_security_text, failedAttempts, lockSeconds)
            )
            .setContentIntent(openLockScreen(context))
            // Dismissable on purpose: the owner may already have seen it, and a
            // notification that cannot be swiped away is the sort of thing that
            // gets the whole app muted. The cool-down itself is enforced in the
            // lock screen, not by the notification.
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        post(context, GuestChannel.SECURITY, ID_SECURITY, notification)
    }

    /** Replaces the lockout warning once the cool-down expires. */
    fun notifyLockoutCleared(context: Context, attemptsLeft: Int) {
        val notification = NotificationCompat.Builder(context, GuestChannel.SECURITY.id)
            .setSmallIcon(R.drawable.ic_status_alert)
            .setContentTitle(context.getString(R.string.notif_security_cleared_title))
            .setContentText(
                context.getString(R.string.notif_security_cleared_text, attemptsLeft)
            )
            .setContentIntent(openLockScreen(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        post(context, GuestChannel.SECURITY, ID_SECURITY, notification)
    }

    fun clearSecurity(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_SECURITY)
    }

    // ---------------------------------------------------------------- plumbing

    /**
     * Posts only when everything is in place: permission granted, channel not
     * muted by the owner, and the app allowed to post at all. Swallowing the
     * failure is the correct behaviour — a guest session must not crash because
     * the owner revoked notifications.
     */
    @SuppressLint("MissingPermission") // canPost() performs the real check; lint cannot see through it.
    private fun post(
        context: Context,
        channel: GuestChannel,
        id: Int,
        notification: Notification
    ) {
        if (!canPost(context, channel)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(id, notification) }
            .onFailure { Log.w(TAG, "Could not post ${channel.id} notification", it) }
    }

    private fun canPost(context: Context, channel: GuestChannel): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        return GuestChannel.isEnabled(context, channel)
    }

    /** Opens the restricted guest home, where "Return to Owner" lives. */
    private fun openGuestHome(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, immutableFlags())
    }

    /** Opens the guest home on the owner PIN pad, and nothing else. */
    private fun openLockScreen(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        2,
        lockScreenIntent(context),
        immutableFlags()
    )

    private fun lockScreenIntent(context: Context): Intent =
        Intent(context, GuestHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(GuestHomeActivity.EXTRA_OPEN_LOCK, true)

    /** Opens the dashboard, which forwards straight to session history. */
    private fun openHistory(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_HISTORY, true)
        return PendingIntent.getActivity(context, 1, intent, immutableFlags())
    }

    private fun immutableFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    /** How the session ended, in the owner's language (never a guest's). */
    private fun EndReason.summaryRes(): Int = when (this) {
        EndReason.OWNER_UNLOCK -> R.string.notif_summary_reason_owner
        EndReason.TIMER_EXPIRED -> R.string.notif_summary_reason_timer
        EndReason.DATA_WIPED -> R.string.notif_summary_reason_wiped
        EndReason.RECOVERED -> R.string.notif_summary_reason_recovered
    }

    private const val TAG = "GuestNotifications"
}
