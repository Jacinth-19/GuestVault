package com.guestvault.ui.screens.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The splash progress line shipped as an **empty grey bar** in `00-splash.png`,
 * because its position came straight from an animation that ran `0f -> 1f` and
 * treated `1f` as "translate one full track-width". With animators disabled the
 * clock freezes at the end value, so the highlight parked entirely off the track
 * — and even with animations on, a third of every loop was empty.
 *
 * These tests pin the replacement mapping: both ends of the ping-pong clock are
 * on the track, every intermediate phase is on the track, and the static frame
 * used for reduced motion leaves a fully visible highlight.
 */
class SplashSweepTest {

    /** Width of the highlight as a fraction of the track. */
    private val highlightWidth = 0.40f

    /** Travel available to the highlight so it never overhangs the track. */
    private val travel = 1f - highlightWidth

    @Test
    fun `the ping-pong clock starts and ends at the left edge`() {
        assertEquals(0f, sweepFraction(0f), 0.0001f)
        assertEquals(1f, sweepFraction(1f), 0.0001f)
        assertEquals(0f, sweepFraction(2f), 0.0001f)
    }

    @Test
    fun `the second half of the cycle mirrors the first`() {
        for (step in 0..100) {
            val clock = step / 100f
            assertEquals(sweepFraction(clock), sweepFraction(2f - clock), 0.0001f)
        }
    }

    @Test
    fun `every phase keeps the highlight inside the track`() {
        var clock = 0f
        while (clock <= 2f) {
            val fraction = sweepFraction(clock)
            assertTrue(
                "fraction $fraction out of range at clock $clock",
                fraction in 0f..1f
            )
            // The regression this exists for: a full offset hides the highlight.
            val rightEdge = fraction * travel + highlightWidth
            assertTrue(
                "highlight overhangs the track at clock $clock (right edge $rightEdge)",
                rightEdge <= 1.0001f
            )
            clock += 0.01f
        }
    }

    @Test
    fun `the highlight is never fully invisible`() {
        var clock = 0f
        var visible = 0
        var samples = 0
        while (clock <= 2f) {
            val fraction = sweepFraction(clock)
            val left = fraction * travel
            val right = left + highlightWidth
            if (right > 0f && left < 1f) visible++
            samples++
            clock += 0.01f
        }
        assertEquals(samples, visible)
    }

    @Test
    fun `the reduced-motion frame shows a highlight near the start of the track`() {
        assertTrue(
            "static fraction must leave the highlight on the track",
            SWEEP_STATIC_FRACTION in 0.0f..1.0f
        )
        val rightEdge = SWEEP_STATIC_FRACTION * travel + highlightWidth
        assertTrue("static highlight overhangs the track", rightEdge <= 1.0001f)
        assertTrue("static highlight should be clearly visible", SWEEP_STATIC_FRACTION >= 0.15f)
    }

    @Test
    fun `a frozen clock at either end is still visible`() {
        // Compose may sample the start or the end of an infinite animation when
        // the duration scale is zero; both have to look like a progress line.
        for (frozen in listOf(0f, 1f, 2f)) {
            val fraction = sweepFraction(frozen)
            val rightEdge = fraction * travel + highlightWidth
            assertTrue("frozen at $frozen left no highlight", rightEdge > 0.2f)
        }
    }
}
