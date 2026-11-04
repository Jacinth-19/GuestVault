package com.guestvault.domain.model

/**
 * Guest time already used on one calendar day.
 *
 * Stored as a single running total per day, never per app and never with a
 * timestamp: the daily budget needs "how much today", and nothing in the app
 * should be able to reconstruct *when* the guest was on the phone. The day is
 * kept as an epoch day so a stale record is simply ignored rather than
 * migrated.
 */
data class DailyUsage(
    val epochDay: Long = Long.MIN_VALUE,
    val millis: Long = 0L
)
