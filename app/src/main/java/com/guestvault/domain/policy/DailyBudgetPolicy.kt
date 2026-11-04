package com.guestvault.domain.policy

import com.guestvault.domain.model.DailyUsage
import java.time.Instant
import java.time.ZoneId

/**
 * How much guest time is left today.
 *
 * A per-app budget is counted per hand-over (see [AppLimitPolicy]); a daily
 * budget is counted across every session of one calendar day, so a guest cannot
 * get "five minutes of video" six times in an afternoon.
 *
 * The number is *derived*, never ticked. Usage is the total of sessions that
 * have already ended today plus the elapsed part of the running one. That means
 * the count survives a process death, a reboot and a clock change without a
 * single write per second — and it cannot drift away from the session history,
 * because both are computed from the same start times.
 *
 * Pure on purpose: every branch is covered by a JVM test with an explicit
 * clock, including a session that crosses midnight.
 */
object DailyBudgetPolicy {

    /** What the owner sees when the day is spent. Used by the gate and the UI. */
    const val END_MESSAGE = "Today's guest time is used up. It resets at midnight."

    /** 0 or absent means "no daily limit", matching every other duration. */
    fun hasBudget(minutes: Int): Boolean = minutes > 0

    fun budgetMillis(minutes: Int): Long =
        if (minutes <= 0) 0L else minutes * 60_000L

    fun epochDay(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()

    fun startOfDay(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(now)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    fun startOfNextDay(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(now)
            .atZone(zone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    /**
     * Guest time used today.
     *
     * [base] is what earlier sessions used on [base]'s own day; it only counts
     * when that day is today, so the record rolls over at midnight without
     * anything having to run at 00:00. [sessionStartedAt] is the start of the
     * session that is running right now, if any — a session that began before
     * midnight only counts from midnight.
     */
    fun usedToday(
        base: DailyUsage,
        sessionStartedAt: Long?,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val today = epochDay(now, zone)
        val carried = if (base.epochDay == today) base.millis.coerceAtLeast(0L) else 0L
        val live = sessionStartedAt?.let { started ->
            val from = maxOf(started, startOfDay(now, zone))
            if (now > from) now - from else 0L
        } ?: 0L
        return carried + live
    }

    fun remainingMillis(minutes: Int, usedMillis: Long): Long {
        if (minutes <= 0) return Long.MAX_VALUE
        return (budgetMillis(minutes) - usedMillis).coerceAtLeast(0L)
    }

    fun isExhausted(minutes: Int, usedMillis: Long): Boolean =
        minutes > 0 && usedMillis >= budgetMillis(minutes)

    /**
     * The owner-facing line under the daily budget chips:
     * "42 min left today", "< 1 min left today", or the end message.
     * Null when no daily budget is set, so an unlimited day renders nothing.
     */
    fun caption(minutes: Int, usedMillis: Long): String? {
        if (minutes <= 0) return null
        val remaining = remainingMillis(minutes, usedMillis)
        if (remaining <= 0L) return END_MESSAGE
        val left = (remaining + 59_999L) / 60_000L
        return if (left <= 1L) "< 1 min left today" else "$left min left today"
    }

    /**
     * Splits one session across the days it touches, for storage.
     *
     * A session that ends at 00:10 must only charge ten minutes to the new day —
     * otherwise a late hand-over would spend the whole of tomorrow before it
     * began. Capped at eight days: a session longer than that is a broken clock
     * or a bug, and the cap keeps a corrupt start time from looping.
     */
    fun split(
        startedAt: Long,
        endedAt: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Pair<Long, Long>> {
        if (endedAt <= startedAt) return emptyList()
        val parts = mutableListOf<Pair<Long, Long>>()
        var cursor = startedAt
        var guard = 0
        while (cursor < endedAt && guard < MAX_DAYS) {
            val boundary = startOfNextDay(cursor, zone)
            val end = minOf(boundary, endedAt)
            parts += epochDay(cursor, zone) to (end - cursor)
            cursor = end
            guard++
        }
        return parts
    }

    private const val MAX_DAYS = 8
}
