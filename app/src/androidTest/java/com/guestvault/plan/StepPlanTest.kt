package com.guestvault.plan

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One `@Test` per pass of the numbered manual test plan.
 *
 * The CI script invokes a single method at a time (`-e class ...#passNN`) after
 * clearing app data, so every pass starts from a fresh install. The methods
 * also run fine together (`-e class com.guestvault.plan.StepPlanTest`), which is
 * how they behave on a developer machine.
 *
 * A failing step is recorded, not thrown: the plan's point is a verdict for
 * every numbered row, and one broken row must not hide the rest.
 */
@RunWith(AndroidJUnit4::class)
class StepPlanTest : StepHarness() {

    @Before
    fun setUp() {
        prepare()
    }

    /**
     * Canary for the harness itself: proves the device reaches the app, the
     * verdict channel works and the report renders. Not one of the plan's
     * numbered steps.
     */
    @Test
    fun smoke() {
        launchApp()
        verify("S-1", "App reaches a known first screen (harness smoke test)") {
            val screen = waitForAny(
                "Skip",
                "Create your owner PIN",
                "Start Guest Session",
                "Welcome to Guest Mode",
                timeout = 45_000
            )
            expectTrue(screen != null, "the app never reached a known screen")
        }
    }
}
