package com.guestvault.core.notifications

/**
 * Decides when a timed guest session deserves a reminder.
 *
 * Pure Kotlin on purpose: the interesting cases (a 5-minute session must not
 * announce "5 minutes left" the instant it starts, a phone that was asleep for
 * ten minutes must not fire four notifications in a row) are cheap to test and
 * expensive to debug on a device. See `TimerRemindersTest`.
 */
object TimerReminders {

    /** Minutes before the end at which the owner is reminded, largest first. */
    val THRESHOLDS = listOf(5, 1)

    /**
     * The single reminder that should fire now, or null.
     *
     * @param totalMinutes the session length; 0 means "no limit", so no reminders.
     * @param remainingMillis time left in the session.
     * @param alreadyNotified thresholds that have already produced a notification.
     *
     * The *smallest* due threshold wins: if the device was asleep and the session
     * jumped from six minutes left to forty seconds, the owner should read
     * "1 minute left", not a stale "5 minutes left". Callers then mark every
     * threshold at or above the returned one as sent (see [superseded]) so a
     * later tick cannot fire the older, larger one.
     */
    fun due(totalMinutes: Int, remainingMillis: Long, alreadyNotified: Set<Int>): Int? {
        if (totalMinutes <= 0 || remainingMillis <= 0L) return null
        return THRESHOLDS
            .filter { it < totalMinutes }
            .filter { it !in alreadyNotified }
            .filter { remainingMillis <= it * 60_000L }
            .minOrNull()
    }

    /** Every threshold made obsolete once [minutes] has been announced. */
    fun superseded(minutes: Int): Set<Int> = THRESHOLDS.filter { it >= minutes }.toSet()
}
