package com.guestvault.domain.model

/**
 * When guest mode is allowed to run at all.
 *
 * Stored as minute-of-day rather than a timestamp so a schedule survives
 * daylight-saving shifts and time-zone changes: "07:00 to 22:00" means what the
 * wall clock says, not what it said when the owner set it.
 *
 * [days] uses ISO day numbers (1 = Monday … 7 = Sunday) so the ordering is the
 * same one a calendar shows.
 */
data class GuestSchedule(
    val enabled: Boolean = false,
    val startMinuteOfDay: Int = 7 * 60,
    val endMinuteOfDay: Int = 22 * 60,
    val days: Set<Int> = DEFAULT_DAYS
) {
    /** True when the window covers every day, which is the common case. */
    val isEveryDay: Boolean get() = days.size == 7

    companion object {
        val DEFAULT_DAYS: Set<Int> = (1..7).toSet()
        const val MINUTES_PER_DAY = 24 * 60
    }
}
