package com.guestvault

import com.guestvault.core.Formatters
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `countdown formats minutes and hours`() {
        assertEquals("00:00", Formatters.countdown(0))
        assertEquals("00:59", Formatters.countdown(59_000))
        assertEquals("01:05", Formatters.countdown(65_000))
        assertEquals("1:02:03", Formatters.countdown((1 * 3600 + 2 * 60 + 3) * 1_000L))
        assertEquals("00:00", Formatters.countdown(-5_000))
    }

    @Test
    fun `duration is human readable`() {
        assertEquals("Less than a minute", Formatters.duration(30_000))
        assertEquals("5 min", Formatters.duration(5 * 60_000L))
        assertEquals("1 h", Formatters.duration(60 * 60_000L))
        assertEquals("1 h 05 min", Formatters.duration(65 * 60_000L))
    }

    @Test
    fun `timer options are labelled`() {
        assertEquals("No limit", Formatters.minutesLabel(0))
        assertEquals("5 min", Formatters.minutesLabel(5))
        assertEquals("1 hour", Formatters.minutesLabel(60))
    }
}
