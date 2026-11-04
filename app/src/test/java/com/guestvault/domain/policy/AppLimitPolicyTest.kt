package com.guestvault.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLimitPolicyTest {

    @Test
    fun `no limit means no budget`() {
        assertFalse(AppLimitPolicy.hasLimit(0))
        assertFalse(AppLimitPolicy.hasLimit(-5))
        assertEquals(0L, AppLimitPolicy.budgetMillis(0))
        assertFalse(AppLimitPolicy.isExhausted(0, 10_000_000L))
        assertNull("an unlimited app must render no caption at all", AppLimitPolicy.caption(0, 0L))
    }

    @Test
    fun `budget is minutes in millis`() {
        assertEquals(900_000L, AppLimitPolicy.budgetMillis(15))
        assertTrue(AppLimitPolicy.isExhausted(15, 900_000L))
        assertFalse(AppLimitPolicy.isExhausted(15, 899_999L))
    }

    @Test
    fun `remaining never goes negative`() {
        assertEquals(0L, AppLimitPolicy.remainingMillis(5, 10_000_000L))
        assertEquals(60_000L, AppLimitPolicy.remainingMillis(5, 240_000L))
    }

    @Test
    fun `captions round up and say when it is over`() {
        assertEquals("15 min left", AppLimitPolicy.caption(15, 0L))
        assertEquals("14 min left", AppLimitPolicy.caption(15, 60_000L))
        assertEquals("< 1 min", AppLimitPolicy.caption(15, 870_000L))
        assertEquals("Time's up", AppLimitPolicy.caption(15, 900_000L))
        assertEquals("Time's up", AppLimitPolicy.caption(15, 5_000_000L))
    }

    @Test
    fun `owner-facing label`() {
        assertEquals("No limit", AppLimitPolicy.label(0))
        assertEquals("30 min", AppLimitPolicy.label(30))
    }
}
