package com.guestvault.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimerRemindersTest {

    private fun minutes(n: Int) = n * 60_000L

    @Test
    fun `open-ended sessions never remind`() {
        assertNull(TimerReminders.due(totalMinutes = 0, remainingMillis = minutes(3), alreadyNotified = emptySet()))
    }

    @Test
    fun `nothing fires while the session is long`() {
        assertNull(
            TimerReminders.due(
                totalMinutes = 30,
                remainingMillis = minutes(20),
                alreadyNotified = emptySet()
            )
        )
    }

    @Test
    fun `five minute warning fires at five minutes`() {
        assertEquals(
            5,
            TimerReminders.due(30, minutes(5), emptySet())
        )
    }

    @Test
    fun `one minute warning fires at one minute`() {
        assertEquals(1, TimerReminders.due(30, minutes(1), setOf(5)))
    }

    @Test
    fun `a threshold is never announced twice`() {
        assertNull(TimerReminders.due(30, minutes(4), setOf(5)))
    }

    @Test
    fun `short sessions skip the five minute warning`() {
        // A two-minute session would otherwise announce "5 minutes left" the
        // moment it starts, which is nonsense.
        assertNull(TimerReminders.due(totalMinutes = 2, remainingMillis = minutes(2), alreadyNotified = emptySet()))
        assertEquals(1, TimerReminders.due(totalMinutes = 2, remainingMillis = minutes(1), emptySet()))
    }

    @Test
    fun `a sleeping device gets the most relevant reminder only`() {
        // Phone dozed through the five-minute mark and woke with 40 seconds left:
        // the owner should read "1 minute left", not a stale five.
        assertEquals(1, TimerReminders.due(30, 40_000L, emptySet()))
    }

    @Test
    fun `expired sessions do not remind`() {
        assertNull(TimerReminders.due(30, 0L, emptySet()))
    }

    @Test
    fun `superseded marks larger thresholds too`() {
        assertEquals(setOf(1, 5), TimerReminders.superseded(1))
        assertEquals(setOf(5), TimerReminders.superseded(5))
    }
}
