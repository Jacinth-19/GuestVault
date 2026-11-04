package com.guestvault.domain.policy

import com.guestvault.domain.model.GuestSchedule
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Decides whether guest mode may run right now.
 *
 * Pure on purpose: a clock is passed in, so every branch — including a window
 * that crosses midnight and the "next opening" message — is covered by a fast
 * JVM test instead of only by waiting for 22:00 on a device.
 */
object SchedulePolicy {

    /**
     * The one place a schedule is judged.
     *
     * A window whose start equals its end is treated as "all day" rather than
     * as empty: an owner who drags both pickers to 07:00 means "always", and
     * silently locking them out is the one reading that helps nobody.
     */
    fun isOpen(
        schedule: GuestSchedule,
        at: ZonedDateTime
    ): Boolean {
        if (!schedule.enabled) return true
        if (schedule.days.isEmpty()) return false

        val minute = at.hour * 60 + at.minute
        val day = at.dayOfWeek.value
        val start = schedule.startMinuteOfDay.coerceIn(0, GuestSchedule.MINUTES_PER_DAY - 1)
        val end = schedule.endMinuteOfDay.coerceIn(0, GuestSchedule.MINUTES_PER_DAY - 1)

        if (start == end) return day in schedule.days

        return if (start < end) {
            // A plain same-day window.
            day in schedule.days && minute >= start && minute < end
        } else {
            // Crossing midnight: the evening half belongs to today, the morning
            // half belongs to the day after the one the window started on.
            when {
                minute >= start -> day in schedule.days
                minute < end -> previousDay(day) in schedule.days
                else -> false
            }
        }
    }

    fun isOpen(schedule: GuestSchedule, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        isOpen(schedule, ZonedDateTime.now(zone))

    /**
     * What to tell the owner when the door is shut — never a bare "not now".
     * Returns null when the schedule is open.
     */
    fun closedMessage(
        schedule: GuestSchedule,
        at: ZonedDateTime
    ): String? {
        if (isOpen(schedule, at)) return null
        val opens = nextOpening(schedule, at) ?: return "Guest mode is paused by your schedule."
        val whenLabel = if (opens.daysAhead == 0) {
            "today"
        } else if (opens.daysAhead == 1) {
            "tomorrow"
        } else {
            "on ${longDay(opens.day)}"
        }
        return "Guest mode is paused until ${opens.time} $whenLabel."
    }

    /** Where a closed schedule opens next, and how far away that is. */
    data class NextOpening(val day: DayOfWeek, val time: String, val daysAhead: Int)

    /**
     * The next instant the schedule opens. Null when it can never open because
     * no days are selected — which the UI prevents, but the policy still states.
     */
    fun nextOpening(schedule: GuestSchedule, at: ZonedDateTime): NextOpening? {
        if (schedule.days.isEmpty()) return null
        val start = schedule.startMinuteOfDay.coerceIn(0, GuestSchedule.MINUTES_PER_DAY - 1)
        val opensAt = formatMinute(start)

        // Today first, then the next six days: a weekly window always opens
        // within seven.
        for (offset in 0..6) {
            val day = at.dayOfWeek.plus(offset.toLong())
            if (day.value !in schedule.days) continue
            if (offset == 0) {
                val minute = at.hour * 60 + at.minute
                if (minute < start) return NextOpening(day, opensAt, 0)
            } else {
                return NextOpening(day, opensAt, offset)
            }
        }
        return null
    }

    fun longDay(day: DayOfWeek): String = day.name.lowercase().replaceFirstChar { it.uppercase() }

    /** "07:00 – 22:00 · every day" or "22:00 – 07:00 · Mon, Tue, Wed". */
    fun describe(schedule: GuestSchedule): String {
        val window = "${formatMinute(schedule.startMinuteOfDay)} – " +
            "${formatMinute(schedule.endMinuteOfDay)}"
        val days = when {
            schedule.days.isEmpty() -> "no days"
            schedule.isEveryDay -> "every day"
            schedule.days == WEEKDAYS -> "weekdays"
            schedule.days == WEEKEND -> "weekends"
            else -> schedule.days.sorted().joinToString(", ") { shortDay(it) }
        }
        return "$window · $days"
    }

    fun formatMinute(minuteOfDay: Int): String {
        val minute = minuteOfDay.coerceIn(0, GuestSchedule.MINUTES_PER_DAY - 1)
        return "%02d:%02d".format(minute / 60, minute % 60)
    }

    fun parseMinute(text: String): Int? {
        val parts = text.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun shortDay(iso: Int): String = when (iso) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        7 -> "Sun"
        else -> "?"
    }

    fun toLocalTime(minuteOfDay: Int): LocalTime =
        LocalTime.of((minuteOfDay / 60).coerceIn(0, 23), (minuteOfDay % 60).coerceIn(0, 59))

    private fun previousDay(iso: Int): Int = if (iso == 1) 7 else iso - 1

    private val WEEKDAYS: Set<Int> = setOf(1, 2, 3, 4, 5)
    private val WEEKEND: Set<Int> = setOf(6, 7)
}
