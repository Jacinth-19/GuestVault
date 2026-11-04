package com.guestvault.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntruderPolicyTest {

    @Test
    fun `a photo is only taken when every condition holds`() {
        assertTrue(IntruderPolicy.shouldCapture(true, true, true, 1))
        assertFalse(IntruderPolicy.shouldCapture(false, true, true, 1))
        assertFalse(IntruderPolicy.shouldCapture(true, false, true, 1))
        assertFalse(IntruderPolicy.shouldCapture(true, true, false, 1))
        assertFalse(IntruderPolicy.shouldCapture(true, true, true, 0))
    }

    @Test
    fun `a device with no front camera never captures`() {
        assertFalse(IntruderPolicy.shouldCapture(true, false, true, 5))
    }

    private fun photo(name: String, at: Long) = IntruderPhoto(name, at)

    @Test
    fun `only the newest photos are kept`() {
        val photos = (1L..12L).map { photo("p$it.jpg", it * 1_000) }

        val kept = IntruderPolicy.keepNewest(photos, keep = 3)

        assertEquals(listOf("p12.jpg", "p11.jpg", "p10.jpg"), kept.map { it.name })
    }

    @Test
    fun `everything outside the cap is deleted`() {
        val photos = (1L..12L).map { photo("p$it.jpg", it * 1_000) }

        val doomed = IntruderPolicy.toDelete(photos, keep = 8).map { it.name }

        assertEquals((1L..4L).map { "p$it.jpg" }, doomed)
    }

    @Test
    fun `a store inside the cap deletes nothing`() {
        val photos = (1L..3L).map { photo("p$it.jpg", it) }

        assertTrue(IntruderPolicy.toDelete(photos, keep = 8).isEmpty())
        assertTrue(IntruderPolicy.toDelete(emptyList(), keep = 8).isEmpty())
    }

    @Test
    fun `the default cap is small`() {
        assertEquals(8, IntruderPolicy.MAX_PHOTOS)
        assertTrue(IntruderPolicy.MAX_PHOTOS <= 10)
    }

    @Test
    fun `the switch has a reason whenever it cannot be used`() {
        assertNull(IntruderPolicy.unavailableReason(cameraAvailable = true, permissionGranted = true))
        assertNotNull(IntruderPolicy.unavailableReason(cameraAvailable = false, permissionGranted = false))
        assertNotNull(IntruderPolicy.unavailableReason(cameraAvailable = true, permissionGranted = false))
    }
}
