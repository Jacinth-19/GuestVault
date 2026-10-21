package com.guestvault.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    /**
     * The media workflow captures screenshots from a debug build, whose
     * `VERSION_NAME` is `1.2.0-debug`. If the suffix ever leaks into the splash
     * pill or the dashboard footer again, this fails before the screenshots do.
     */
    @Test
    fun `display version has no build-type suffix`() {
        assertTrue(AppVersion.display.isNotEmpty())
        assertFalse("'${AppVersion.display}' still contains a build-type suffix", AppVersion.display.contains('-'))
    }

    @Test
    fun `display version keeps the semantic part`() {
        val expected = com.guestvault.BuildConfig.VERSION_NAME.substringBefore('-')
        assertEquals(expected, AppVersion.display)
    }
}
