package com.guestvault

import com.guestvault.domain.model.EndReason
import com.guestvault.domain.model.GuestSession
import com.guestvault.domain.model.SessionRecord
import com.guestvault.domain.model.WipeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionModelTest {

    private fun session(durationMinutes: Int) = GuestSession(
        startedAt = 1_000_000L,
        packages = setOf("com.android.chrome", "com.android.camera"),
        durationMinutes = durationMinutes,
        autoWipe = true,
        lockTaskEnabled = true
    )

    @Test
    fun `zero duration means no limit`() {
        val session = session(0)
        assertNull(session.endsAt)
        assertNull(session.remainingMillis(now = 5_000_000L))
    }

    @Test
    fun `remaining time counts down and never goes negative`() {
        val session = session(15)
        assertEquals(15 * 60_000L, session.remainingMillis(now = session.startedAt))
        assertEquals(60_000L, session.remainingMillis(now = session.startedAt + 14 * 60_000L))
        assertEquals(0L, session.remainingMillis(now = session.startedAt + 60 * 60_000L))
    }

    @Test
    fun `end reason falls back for unknown values`() {
        assertEquals(EndReason.TIMER_EXPIRED, EndReason.fromName("TIMER_EXPIRED"))
        assertEquals(EndReason.OWNER_UNLOCK, EndReason.fromName("nonsense"))
        assertEquals(EndReason.OWNER_UNLOCK, EndReason.fromName(null))
    }

    @Test
    fun `session record duration is clamped`() {
        val record = SessionRecord(
            startedAt = 1_000L,
            endedAt = 61_000L,
            appCount = 3,
            autoWiped = true,
            endReason = EndReason.OWNER_UNLOCK
        )
        assertEquals(60_000L, record.durationMillis)

        val reversed = record.copy(startedAt = 100_000L, endedAt = 50_000L)
        assertEquals(0L, reversed.durationMillis)
    }

    @Test
    fun `wipe result renders readable sizes`() {
        assertEquals("0 KB", WipeResult(0, 0).readableSize())
        assertEquals("12 KB", WipeResult(12 * 1024L, 3).readableSize())
        assertTrue(WipeResult(5 * 1024L * 1024L, 3).readableSize().endsWith("MB"))
    }
}
