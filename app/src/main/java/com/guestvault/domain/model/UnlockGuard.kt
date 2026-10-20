package com.guestvault.domain.model

/**
 * Persistent brute-force guard for the owner lock.
 *
 * Kept on disk (not just in a ViewModel) so that rotating the screen, killing the
 * activity from Recents, or letting the process die never resets the attempt
 * counter. [failedAttempts] counts failures since the last successful unlock;
 * [lockedUntil] is an absolute epoch-millis deadline.
 */
data class UnlockGuard(
    val failedAttempts: Int = 0,
    val lockedUntil: Long = 0L
)
