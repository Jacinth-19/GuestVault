package com.guestvault.domain.policy

import com.guestvault.domain.model.GuestSchedule
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A schedule is the one feature that can lock an owner out of their own phone,
 * so every branch is pinned here rather than discovered at 22:00 on a device.
 */
class SchedulePolicyTest {

    private val zone: ZoneId = ZoneId.of("Africa/Accra")

    private fun at(day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        // 2026-11-02 is a Monday, so day 1 == Monday.
        ZonedDateTime.of(2026, 11, 1 + day, hour, minute, 0, 0, zone)

    private val officeHours = GuestSchedule(
        enabled = true,
        startMinuteOfDay = 7 * 60,
        endMinuteOfDay = 22 * 60
    )

    @Test
    fun `a disabled schedule is always open`() {
        val off = GuestSchedule(enabled = false, startMinuteOfDay = 600, endMinuteOfDay = 660)
        assertTrue(SchedulePolicy.isOpen(off, at(3, 4)))
        assertNull(SchedulePolicy.closedMessage(off, at(3, 4)))
    }

    @Test
    fun `inside the window is open`() {
        assertTrue(SchedulePolicy.isOpen(officeHours, at(3, 9)))
        assertTrue(SchedulePolicy.isOpen(officeHours, at(3, 7)))   // inclusive start
        assertTrue(SchedulePolicy.isOpen(officeHours, at(3, 21, 59)))
    }

    @Test
    fun `the end minute is already closed`() {
        assertFalse(SchedulePolicy.isOpen(officeHours, at(3, 22)))
        assertFalse(SchedulePolicy.isOpen(officeHours, at(3, 6, 59)))
    }

    @Test
    fun `a window crossing midnight spans two days`() {
        // 22:00 -> 07:00, Monday to Friday. Saturday 02:00 belongs to Friday's
        // window, so it must be open; Monday 02:00 belongs to Sunday, which is
        // not selected, so it must be shut.
        val nights = GuestSchedule(
            enabled = true,
            startMinuteOfDay = 22 * 60,
            endMinuteOfDay = 7 * 60,
            days = setOf(1, 2, 3, 4, 5)
        )
        assertTrue("Friday 23:00", SchedulePolicy.isOpen(nights, at(5, 23)))
        assertTrue("Saturday 02:00 (Friday's window)", SchedulePolicy.isOpen(nights, at(6, 2)))
        assertFalse("Monday 02:00 (Sunday's window)", SchedulePolicy.isOpen(nights, at(1, 2)))
        assertFalse("Saturday 12:00", SchedulePolicy.isOpen(nights, at(6, 12)))
    }

    @Test
    fun `start equal to end means all day, not never`() {
        val allDay = GuestSchedule(enabled = true, startMinuteOfDay = 420, endMinuteOfDay = 420)
        assertTrue(SchedulePolicy.isOpen(allDay, at(3, 3)))
        assertTrue(SchedulePolicy.isOpen(allDay, at(3, 23)))
    }

    @Test
    fun `only the selected days open`() {
        val weekendsOnly = GuestSchedule(
            enabled = true,
            startMinuteOfDay = 8 * 60,
            endMinuteOfDay = 20 * 60,
            days = setOf(6, 7)
        )
        assertTrue("Saturday", SchedulePolicy.isOpen(weekendsOnly, at(6, 12)))
        assertFalse("Wednesday", SchedulePolicy.isOpen(weekendsOnly, at(3, 12)))
    }

    @Test
    fun `no days at all is never open`() {
        val never = GuestSchedule(enabled = true, days = emptySet())
        assertFalse(SchedulePolicy.isOpen(never, at(3, 12)))
        assertTrue(SchedulePolicy.closedMessage(never, at(3, 12))!!.contains("paused"))
    }

    @Test
    fun `the closed message says when it opens again, not just that it is shut`() {
        val message = SchedulePolicy.closedMessage(officeHours, at(3, 5))
        assertTrue("was: $message", message!!.contains("07:00 today"))

        val tomorrow = SchedulePolicy.closedMessage(officeHours, at(3, 23))
        assertTrue("was: $tomorrow", tomorrow!!.contains("tomorrow"))
    }

    @Test
    fun `next opening skips days that are not selected`() {
        val fridayOnly = GuestSchedule(
            enabled = true,
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 17 * 60,
            days = setOf(5)
        )
        // Monday -> the window opens on Friday.
        val next = SchedulePolicy.nextOpening(fridayOnly, at(1, 12))!!
        assertEquals(5, next.day.value)
        assertEquals(4, next.daysAhead)
        assertEquals("09:00", next.time)
    }

    @Test
    fun `describe is readable`() {
        assertEquals(
            "07:00 – 22:00 · every day",
            SchedulePolicy.describe(GuestSchedule(enabled = true))
        )
        assertEquals(
            "09:00 – 17:00 · weekdays",
            SchedulePolicy.describe(
                GuestSchedule(
                    enabled = true,
                    startMinuteOfDay = 540,
                    endMinuteOfDay = 1020,
                    days = setOf(1, 2, 3, 4, 5)
                )
            )
        )
    }

    @Test
    fun `minute formatting round-trips`() {
        assertEquals("00:00", SchedulePolicy.formatMinute(0))
        assertEquals("07:30", SchedulePolicy.formatMinute(450))
        assertEquals("23:59", SchedulePolicy.formatMinute(1439))
        assertEquals(450, SchedulePolicy.parseMinute("07:30"))
        assertNull(SchedulePolicy.parseMinute("25:00"))
        assertNull(SchedulePolicy.parseMinute("07:99"))
        assertNull(SchedulePolicy.parseMinute("nonsense"))
    }
}
