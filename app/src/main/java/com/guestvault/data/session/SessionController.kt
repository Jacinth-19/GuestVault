package com.guestvault.data.session

import android.content.Context
import android.content.Intent
import android.util.Log
import com.guestvault.MainActivity
import com.guestvault.core.GuestNotifications
import com.guestvault.domain.model.EndReason
import com.guestvault.domain.model.GuestSession
import com.guestvault.domain.model.SessionRecord
import com.guestvault.domain.model.WipeResult
import com.guestvault.domain.repository.SettingsRepository
import com.guestvault.guest.GuestHomeActivity
import com.guestvault.guest.GuestSessionService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the guest session lifecycle.
 *
 * A session is written to disk *before* the guest UI opens, so a crash or
 * process death can never make the phone look like it is in owner mode while a
 * stranger is still holding it.
 */
@Singleton
class SessionController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val wiper: GuestDataWiper
) {

    /** Serialises start/end so double taps or timer races cannot end twice. */
    private val mutex = Mutex()

    suspend fun start(
        packages: Set<String>,
        durationMinutes: Int,
        autoWipe: Boolean,
        lockTaskEnabled: Boolean
    ): GuestSession = mutex.withLock {
        require(packages.isNotEmpty()) { "Select at least one app first" }

        val session = GuestSession(
            startedAt = System.currentTimeMillis(),
            packages = packages,
            durationMinutes = durationMinutes.coerceAtLeast(0),
            autoWipe = autoWipe,
            lockTaskEnabled = lockTaskEnabled
        )

        wiper.prepareGuestWorkspace()
        // Persist first: if anything below fails, recovery still knows a guest
        // session is supposed to be active.
        settingsRepository.setActiveSession(session)
        GuestSessionService.start(context)
        openGuestUi()
        session
    }

    /**
     * Ends the active session, records it in history and (optionally) wipes
     * guest artifacts. Safe to call multiple times.
     */
    suspend fun end(reason: EndReason): WipeResult? = mutex.withLock {
        val settings = settingsRepository.settings.first()
        val current = settings.activeSession ?: return@withLock null

        val shouldWipe = current.autoWipe || reason == EndReason.DATA_WIPED
        val wipeResult = if (shouldWipe) {
            runCatching { wiper.wipe(current.packages) }
                .onFailure { Log.w(TAG, "Wipe failed", it) }
                .getOrNull()
        } else {
            null
        }

        val record = SessionRecord(
            startedAt = current.startedAt,
            endedAt = System.currentTimeMillis(),
            appCount = current.packages.size,
            autoWiped = wipeResult != null,
            endReason = reason
        )
        settingsRepository.addSessionRecord(record)
        settingsRepository.setActiveSession(null)
        GuestSessionService.stop(context)

        // The session is over, so nothing about it should still be in the shade:
        // the ongoing notice disappears with the service, the timer reminders and
        // the lockout warning are stale. The summary replaces them.
        GuestNotifications.clearTimerReminders(context)
        GuestNotifications.clearSecurity(context)
        GuestNotifications.notifySessionSummary(
            context = context,
            record = record,
            wipeResult = wipeResult,
            enabled = settings.notifySessionSummary
        )

        wipeResult
    }

    /** Brings an already running session back to the foreground. */
    fun openGuestUi() {
        val intent = Intent(context, GuestHomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Could not open guest UI", it) }
    }

    /** Returns to the owner dashboard and drops the guest task. */
    fun returnToOwner(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
    }

    private companion object {
        const val TAG = "SessionController"
    }
}
