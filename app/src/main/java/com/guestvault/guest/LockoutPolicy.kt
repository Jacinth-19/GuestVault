package com.guestvault.guest

import com.guestvault.domain.model.UnlockGuard

/**
 * Pure brute-force policy for the owner lock. No Android types, so it is
 * covered by fast JVM unit tests (`LockoutPolicyTest`).
 *
 * Rules:
 *  - [MAX_ATTEMPTS] wrong PINs in a row trigger a cool-down, and one security
 *    notification (see `GuestNotifications.notifyLockout`). Three is deliberate:
 *    the guest gets a fair chance at a typo, and the owner hears about it before
 *    a patient guesser gets far.
 *  - The cool-down doubles on every further round of failures (30s, 60s, ...)
 *    and is capped at [MAX_LOCKOUT_SECONDS].
 *  - Attempts are never reset by recreating the UI; only a correct PIN (or a
 *    successful biometric unlock) resets them.
 */
object LockoutPolicy {

    const val MAX_ATTEMPTS = 3
    const val BASE_LOCKOUT_SECONDS = 30L
    const val MAX_LOCKOUT_SECONDS = 900L

    /**
     * Attempts remaining before the next cool-down. While a cool-down is
     * running this is 0; when it expires the owner gets a fresh round of
     * [MAX_ATTEMPTS] (the cool-down itself grows instead).
     */
    fun attemptsLeft(guard: UnlockGuard, now: Long): Int {
        if (isLockedOut(guard, now)) return 0
        return MAX_ATTEMPTS - (guard.failedAttempts % MAX_ATTEMPTS)
    }

    fun remainingLockSeconds(guard: UnlockGuard, now: Long): Int =
        ((guard.lockedUntil - now) / 1_000L).coerceAtLeast(0L).toInt()

    fun isLockedOut(guard: UnlockGuard, now: Long): Boolean =
        remainingLockSeconds(guard, now) > 0

    /**
     * Records one wrong PIN. Returns the new guard; when the attempt lands on a
     * multiple of [MAX_ATTEMPTS] a cool-down starts, otherwise only the counter
     * moves.
     */
    fun registerFailure(guard: UnlockGuard, now: Long): UnlockGuard {
        val failures = guard.failedAttempts + 1
        if (failures % MAX_ATTEMPTS != 0) {
            return guard.copy(failedAttempts = failures, lockedUntil = 0L)
        }

        val round = failures / MAX_ATTEMPTS // 1st cool-down, 2nd cool-down, ...
        var seconds = BASE_LOCKOUT_SECONDS
        repeat(round - 1) { seconds = (seconds * 2).coerceAtMost(MAX_LOCKOUT_SECONDS) }

        return UnlockGuard(
            failedAttempts = failures,
            lockedUntil = now + seconds * 1_000L
        )
    }

    /** Called after a correct PIN or biometric: forgets every past failure. */
    fun reset(): UnlockGuard = UnlockGuard()
}
