package com.guestvault.domain.policy

import com.guestvault.domain.model.DailyUsage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyBudgetPolicyTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 3, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun day(day: Int): Long = LocalDate.of(2026, 3, day).toEpochDay()

    // ------------------------------------------------------------- no session

    @Test
    fun `usage carries over from earlier sessions today`() {
        val now = at(10, 15, 0)
        val base = DailyUsage(epochDay = day(10), millis = 20 * 60_000L)

        assertEquals(20 * 60_000L, DailyBudgetPolicy.usedToday(base, null, now, zone))
    }

    @Test
    fun `yesterday's usage is ignored, so the budget resets at midnight`() {
        val now = at(11, 0, 30)
        val base = DailyUsage(epochDay = day(10), millis = 90 * 60_000L)

        assertEquals(0L, DailyBudgetPolicy.usedToday(base, null, now, zone))
    }

    // ---------------------------------------------------------- running session

    @Test
    fun `a running session counts from its start, not from the beginning of time`() {
        val now = at(10, 15, 0)
        val started = at(10, 14, 0)

        // One hour, not one minute: this is the assertion that caught the
        // minutes-versus-milliseconds mistake in the first version.
        assertEquals(60 * 60_000L, DailyBudgetPolicy.usedToday(DailyUsage(), started, now, zone))
    }

    @Test
    fun `a session that began yesterday only charges today with the minutes after midnight`() {
        val startedYesterday = at(9, 23, 50)
        val now = at(10, 0, 10)

        val used = DailyBudgetPolicy.usedToday(DailyUsage(), startedYesterday, now, zone)

        // 23:50 → 00:10 is twenty minutes of session, but only ten of them
        // belong to today.
        assertEquals(10 * 60_000L, used)
    }

    @Test
    fun `carried usage and the running session add up`() {
        val now = at(10, 15, 0)
        val base = DailyUsage(epochDay = day(10), millis = 5 * 60_000L)

        val used = DailyBudgetPolicy.usedToday(base, at(10, 14, 0), now, zone)

        // Five carried minutes plus the hour the guest has been in the app.
        assertEquals(5 * 60_000L + 60 * 60_000L, used)
    }

    @Test
    fun `a clock that jumped backwards cannot produce a negative total`() {
        val now = at(10, 10, 0)
        val started = at(10, 12, 0)

        assertEquals(0L, DailyBudgetPolicy.usedToday(DailyUsage(), started, now, zone))
    }

    // ------------------------------------------------------------- exhaustion

    @Test
    fun `zero minutes means no daily budget at all`() {
        assertFalse(DailyBudgetPolicy.hasBudget(0))
        assertFalse(DailyBudgetPolicy.isExhausted(0, 10 * 60 * 60_000L))
        assertEquals(Long.MAX_VALUE, DailyBudgetPolicy.remainingMillis(0, 9_999_999L))
        assertNull(DailyBudgetPolicy.caption(0, 0L))
    }

    @Test
    fun `the budget is exhausted exactly at the line, not before`() {
        assertFalse(DailyBudgetPolicy.isExhausted(30, 30 * 60_000L - 1))
        assertTrue(DailyBudgetPolicy.isExhausted(30, 30 * 60_000L))
        assertTrue(DailyBudgetPolicy.isExhausted(30, 31 * 60_000L))
    }

    @Test
    fun `remaining never goes negative`() {
        assertEquals(0L, DailyBudgetPolicy.remainingMillis(30, 45 * 60_000L))
    }

    // ---------------------------------------------------------------- captions

    @Test
    fun `caption counts down in whole minutes and rounds up`() {
        assertEquals("30 min left today", DailyBudgetPolicy.caption(30, 0L))
        assertEquals("20 min left today", DailyBudgetPolicy.caption(30, 10 * 60_000L))
        assertEquals("< 1 min left today", DailyBudgetPolicy.caption(30, 29 * 60_000L + 30_000L))
        assertEquals(DailyBudgetPolicy.END_MESSAGE, DailyBudgetPolicy.caption(30, 30 * 60_000L))
    }

    // ------------------------------------------------------------ the midnight split

    @Test
    fun `a session inside one day is one part`() {
        val parts = DailyBudgetPolicy.split(at(10, 14, 0), at(10, 14, 30), zone)

        assertEquals(listOf(day(10) to 30 * 60_000L), parts)
    }

    @Test
    fun `a session across midnight is charged to both days`() {
        val parts = DailyBudgetPolicy.split(at(10, 23, 50), at(11, 0, 10), zone)

        assertEquals(
            listOf(day(10) to 10 * 60_000L, day(11) to 10 * 60_000L),
            parts
        )
        assertEquals(20 * 60_000L, parts.sumOf { it.second })
    }

    @Test
    fun `an empty or backwards session has no parts`() {
        assertTrue(DailyBudgetPolicy.split(at(10, 12, 0), at(10, 12, 0), zone).isEmpty())
        assertTrue(DailyBudgetPolicy.split(at(10, 12, 0), at(10, 11, 0), zone).isEmpty())
    }
}
