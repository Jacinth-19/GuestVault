package com.guestvault.domain.policy

/**
 * How long a guest may spend inside one app.
 *
 * The clock is deliberately simple and honest: it starts when the guest opens
 * the app and stops when they come back to the GuestVault guest home. Android
 * will not tell an ordinary app which *other* app is in front — doing so needs
 * `PACKAGE_USAGE_STATS`, which this app forbids — so guessing would be worse
 * than a rule the owner can understand and the guest can see.
 */
object AppLimitPolicy {

    /** 0 or absent means "no limit", matching every other duration in the app. */
    fun hasLimit(limitMinutes: Int): Boolean = limitMinutes > 0

    fun budgetMillis(limitMinutes: Int): Long =
        if (limitMinutes <= 0) 0L else limitMinutes * 60_000L

    fun remainingMillis(limitMinutes: Int, usedMillis: Long): Long {
        if (limitMinutes <= 0) return Long.MAX_VALUE
        return (budgetMillis(limitMinutes) - usedMillis).coerceAtLeast(0L)
    }

    fun isExhausted(limitMinutes: Int, usedMillis: Long): Boolean =
        limitMinutes > 0 && usedMillis >= budgetMillis(limitMinutes)

    /**
     * The short caption under a guest tile: "12 min left", "1 min left",
     * "< 1 min", "Time's up". Null when the app has no limit at all, so a
     * session without limits renders exactly as it always did.
     */
    fun caption(limitMinutes: Int, usedMillis: Long): String? {
        if (limitMinutes <= 0) return null
        val remaining = remainingMillis(limitMinutes, usedMillis)
        if (remaining <= 0L) return "Time's up"
        val minutes = (remaining + 59_999L) / 60_000L
        return if (minutes <= 1L) "< 1 min" else "$minutes min left"
    }

    /** "15 min" / "No limit" — the owner-facing label. */
    fun label(limitMinutes: Int): String =
        if (limitMinutes <= 0) "No limit" else "$limitMinutes min"
}
