package com.guestvault.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The budget clock is the part a guest can actually feel, so its arithmetic is
 * pinned here with an explicit "now" instead of wall-clock sleeps.
 */
class AppUsageTrackerTest {

    private lateinit var tracker: AppUsageTracker

    private val youtube = "com.google.android.youtube"
    private val limits = mapOf(youtube to 1) // one minute

    @Before
    fun setUp() {
        tracker = AppUsageTracker()
        tracker.startSession()
    }

    @Test
    fun `starts at zero`() {
        assertEquals(0L, tracker.usedMillis(youtube, 1_000L))
        assertNull(tracker.tick(limits, 1_000L))
    }

    @Test
    fun `counts only while the app is in front`() {
        tracker.launch(youtube, now = 0L)
        tracker.pause(now = 30_000L)          // 30 s spent
        tracker.resume(now = 90_000L)
        tracker.pause(now = 100_000L)         // 10 s more, the gap is not counted

        assertEquals(40_000L, tracker.usedMillis(youtube, 100_000L))
    }

    @Test
    fun `one long stretch is measured from launch`() {
        tracker.launch(youtube, now = 0L)
        assertEquals(45_000L, tracker.usedMillis(youtube, 45_000L))
    }

    @Test
    fun `a budget is reported exactly once, on the tick that crosses it`() {
        tracker.launch(youtube, now = 0L)

        assertNull("still inside the budget", tracker.tick(limits, 59_000L))
        assertEquals(youtube, tracker.tick(limits, 60_000L))
        assertNull("must not fire again", tracker.tick(limits, 61_000L))
        assertNull("nor on any later tick", tracker.tick(limits, 300_000L))
        assertTrue(youtube in tracker.exhausted.value)
    }

    @Test
    fun `an app with no budget never fires`() {
        tracker.launch(youtube, now = 0L)
        assertNull(tracker.tick(emptyMap(), 10_000_000L))
        assertNull(tracker.tick(mapOf(youtube to 0), 10_000_000L))
        assertTrue(tracker.exhausted.value.isEmpty())
    }

    @Test
    fun `switching apps parks the previous clock`() {
        val maps = "com.example.maps"
        tracker.launch(youtube, now = 0L)
        tracker.launch(maps, now = 20_000L)
        tracker.pause(now = 50_000L)

        assertEquals("youtube kept its 20 s", 20_000L, tracker.usedMillis(youtube, 50_000L))
        assertEquals("maps got its own 30 s", 30_000L, tracker.usedMillis(maps, 50_000L))
    }

    @Test
    fun `a new session clears everything`() {
        tracker.launch(youtube, now = 0L)
        tracker.tick(limits, 120_000L)
        assertTrue(tracker.exhausted.value.isNotEmpty())

        tracker.startSession()

        assertEquals(0L, tracker.usedMillis(youtube, 200_000L))
        assertTrue(tracker.exhausted.value.isEmpty())
        assertNull(tracker.tick(limits, 200_000L))
    }

    @Test
    fun `resume after a session end does not resurrect a clock`() {
        tracker.launch(youtube, now = 0L)
        tracker.pause(now = 10_000L)
        tracker.startSession()
        tracker.resume(now = 20_000L)

        assertEquals(0L, tracker.usedMillis(youtube, 30_000L))
        assertFalse(youtube in tracker.exhausted.value)
    }
}
