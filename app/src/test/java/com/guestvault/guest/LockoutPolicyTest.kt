package com.guestvault.guest

import com.guestvault.domain.model.UnlockGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockoutPolicyTest {

    private val start = 1_000_000L

    @Test
    fun `two failures do not lock the owner out`() {
        var guard = UnlockGuard()
        repeat(2) { guard = LockoutPolicy.registerFailure(guard, start) }

        assertEquals(2, guard.failedAttempts)
        assertFalse(LockoutPolicy.isLockedOut(guard, start))
        assertEquals(1, LockoutPolicy.attemptsLeft(guard, start))
    }

    @Test
    fun `third failure starts a thirty second cool-down`() {
        var guard = UnlockGuard()
        repeat(3) { guard = LockoutPolicy.registerFailure(guard, start) }

        assertTrue(LockoutPolicy.isLockedOut(guard, start))
        assertEquals(30, LockoutPolicy.remainingLockSeconds(guard, start))
        assertEquals(0, LockoutPolicy.attemptsLeft(guard, start))
    }

    @Test
    fun `cool-down expires without resetting the counter`() {
        var guard = UnlockGuard()
        repeat(3) { guard = LockoutPolicy.registerFailure(guard, start) }

        val afterLockout = start + 31_000L
        assertFalse(LockoutPolicy.isLockedOut(guard, afterLockout))
        // A fresh round of three attempts is allowed, but the second cool-down
        // is twice as long — that is what stops patient guessing.
        assertEquals(3, LockoutPolicy.attemptsLeft(guard, afterLockout))
        assertEquals(3, guard.failedAttempts)
    }

    @Test
    fun `second round doubles the cool-down`() {
        var guard = UnlockGuard()
        repeat(6) { guard = LockoutPolicy.registerFailure(guard, start) }

        assertTrue(LockoutPolicy.isLockedOut(guard, start))
        assertEquals(60, LockoutPolicy.remainingLockSeconds(guard, start))
    }

    @Test
    fun `cool-down is capped at fifteen minutes`() {
        var guard = UnlockGuard()
        // Twenty rounds of three attempts: 30s doubling hits the 15-minute cap.
        repeat(60) { guard = LockoutPolicy.registerFailure(guard, start) }

        assertEquals(900, LockoutPolicy.remainingLockSeconds(guard, start))
    }

    @Test
    fun `reset clears the counter and the deadline`() {
        var guard = UnlockGuard()
        repeat(3) { guard = LockoutPolicy.registerFailure(guard, start) }

        guard = LockoutPolicy.reset()

        assertEquals(0, guard.failedAttempts)
        assertEquals(0L, guard.lockedUntil)
        assertFalse(LockoutPolicy.isLockedOut(guard, start))
        assertEquals(3, LockoutPolicy.attemptsLeft(guard, start))
    }
}
