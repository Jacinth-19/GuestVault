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
import com.guestvault.domain.policy.DailyBudgetPolicy
import com.guestvault.domain.policy.SchedulePolicy
import com.guestvault.domain.repository.SettingsRepository
import com.guestvault.guest.GuestHomeActivity
import com.guestvault.guest.GuestSessionService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.time.ZonedDateTime
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
    private val wiper: GuestDataWiper,
    private val usageTracker: AppUsageTracker
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

        // The schedule is enforced here, not in the UI, so the Quick Settings
        // tile and any future caller cannot start a session the owner's own
        // rules forbid.
        val current = settingsRepository.settings.first()
        val blocked = SchedulePolicy.closedMessage(current.schedule, ZonedDateTime.now())
        if (blocked != null) throw IllegalStateException(blocked)

        // The daily budget is checked in the same place and for the same
        // reason: the Quick Settings tile starts sessions too, and it must hit
        // exactly the same wall as the dashboard button.
        if (DailyBudgetPolicy.hasBudget(current.dailyBudgetMinutes)) {
            val used = DailyBudgetPolicy.usedToday(
                base = settingsRepository.readDailyUsage(),
                sessionStartedAt = null,
                now = System.currentTimeMillis()
            )
            if (DailyBudgetPolicy.isExhausted(current.dailyBudgetMinutes, used)) {
                throw IllegalStateException(DailyBudgetPolicy.END_MESSAGE)
            }
        }

        val session = GuestSession(
            startedAt = System.currentTimeMillis(),
            packages = packages,
            durationMinutes = durationMinutes.coerceAtLeast(0),
            autoWipe = autoWipe,
            lockTaskEnabled = lockTaskEnabled
        )

        wiper.prepareGuestWorkspace()
        // A new session starts with a clean clock: budgets are per hand-over.
        usageTracker.startSession()
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
        recordDailyUsage(current.startedAt, record.endedAt, settings.dailyBudgetMinutes)
        // Nothing about the guest's usage should outlive the session.
        usageTracker.startSession()
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

    /**
     * Charges a finished session to today's budget.
     *
     * Nothing is written when no daily budget is set, so a build that never
     * uses the feature does not grow a usage record it will never read.
     */
    private suspend fun recordDailyUsage(startedAt: Long, endedAt: Long, budgetMinutes: Int) {
        if (!DailyBudgetPolicy.hasBudget(budgetMinutes)) return
        val today = DailyBudgetPolicy.epochDay(endedAt)
        val todayMillis = DailyBudgetPolicy.split(startedAt, endedAt)
            .filter { (day, _) -> day == today }
            .sumOf { (_, millis) -> millis }
        if (todayMillis > 0L) settingsRepository.addDailyUsage(today, todayMillis)
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
