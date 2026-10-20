package com.guestvault.domain.model

import java.util.concurrent.TimeUnit

/**
 * A guest session as persisted in DataStore.
 *
 * Design notes:
 * - Only a package *allow-list* is stored; nothing about the guest's activity is.
 * - [durationMinutes] == 0 means "no limit".
 * - Keeping the session on disk means an accidental process death (or a reboot)
 *   can never silently "forget" that a stranger is holding the phone.
 */
data class GuestSession(
    val startedAt: Long,
    val packages: Set<String>,
    val durationMinutes: Int,
    val autoWipe: Boolean,
    val lockTaskEnabled: Boolean
) {
    val endsAt: Long?
        get() = if (durationMinutes > 0) startedAt + TimeUnit.MINUTES.toMillis(durationMinutes.toLong()) else null

    fun remainingMillis(now: Long = System.currentTimeMillis()): Long? =
        endsAt?.let { (it - now).coerceAtLeast(0L) }
}

enum class EndReason(val label: String, val shortLabel: String) {
    OWNER_UNLOCK("Returned to owner", "Owner"),
    TIMER_EXPIRED("Timer expired", "Timer"),
    DATA_WIPED("Session & data cleared", "Cleared"),
    RECOVERED("Recovered after restart", "Recovered");

    companion object {
        fun fromName(name: String?): EndReason =
            entries.firstOrNull { it.name == name } ?: OWNER_UNLOCK
    }
}

/** One line in the owner-only session history. */
data class SessionRecord(
    val startedAt: Long,
    val endedAt: Long,
    val appCount: Int,
    val autoWiped: Boolean,
    val endReason: EndReason
) {
    val durationMillis: Long get() = (endedAt - startedAt).coerceAtLeast(0L)
}

/** Result of an auto-wipe, surfaced in the UI after a session ends. */
data class WipeResult(
    val bytesFreed: Long,
    val filesRemoved: Int
) {
    fun readableSize(): String {
        if (bytesFreed <= 0) return "0 KB"
        val kb = bytesFreed / 1024.0
        if (kb < 1024) return "%.0f KB".format(kb)
        val mb = kb / 1024.0
        return if (mb < 1024) "%.1f MB".format(mb) else "%.2f GB".format(mb / 1024.0)
    }
}
