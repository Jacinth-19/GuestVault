package com.guestvault.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The profile list is stored as text, so the codec is the one thing between a
 * saved preset and a lost preset. It is pure Kotlin precisely so it can be
 * tested here instead of only on a device.
 */
class ProfileCodecTest {

    private val kids = GuestProfile(
        id = "a1",
        name = "Kids",
        packages = setOf("com.android.chrome", "com.google.android.youtube"),
        durationMinutes = 30,
        lockTaskEnabled = true,
        createdAt = 1_700_000_000_000L
    )

    private val stranger = GuestProfile(
        id = "b2",
        name = "Stranger",
        packages = setOf("com.android.chrome"),
        durationMinutes = 0,
        lockTaskEnabled = false,
        createdAt = 1_700_000_500_000L
    )

    @Test
    fun `round trip keeps every field`() {
        val decoded = ProfileCodec.decode(ProfileCodec.encode(listOf(kids, stranger)))

        assertEquals(2, decoded.size)
        assertEquals(kids, decoded[0])
        assertEquals(stranger, decoded[1])
    }

    @Test
    fun `names may contain the field separators`() {
        val awkward = kids.copy(name = "Mum | Dad; the dog\nand the cat 100%")

        val decoded = ProfileCodec.decode(ProfileCodec.encode(listOf(awkward)))

        assertEquals(1, decoded.size)
        assertEquals(awkward.name, decoded[0].name)
        // The fields after the name must not have shifted.
        assertEquals(awkward.packages, decoded[0].packages)
        assertEquals(30, decoded[0].durationMinutes)
        assertTrue(decoded[0].lockTaskEnabled)
        assertEquals(awkward.createdAt, decoded[0].createdAt)
    }

    @Test
    fun `packages are stored sorted so the file is stable`() {
        val encoded = ProfileCodec.encode(listOf(kids))

        assertTrue(encoded.contains("com.android.chrome;com.google.android.youtube"))
    }

    @Test
    fun `a corrupt line is skipped instead of taking the file down`() {
        val encoded = ProfileCodec.encode(listOf(kids))
        val damaged = "this is not a profile\n$encoded\n\n|missing|fields|0|1|0"

        val decoded = ProfileCodec.decode(damaged)

        assertEquals(1, decoded.size)
        assertEquals("Kids", decoded[0].name)
    }

    @Test
    fun `an empty file decodes to an empty list`() {
        assertEquals(emptyList<GuestProfile>(), ProfileCodec.decode(""))
        assertEquals(emptyList<GuestProfile>(), ProfileCodec.decode("\n\n"))
    }

    @Test
    fun `a blank name gets a readable fallback`() {
        val nameless = kids.copy(name = "")

        val decoded = ProfileCodec.decode(ProfileCodec.encode(listOf(nameless)))

        assertEquals("Guest profile", decoded[0].name)
    }

    @Test
    fun `a profile with no apps cannot start a session`() {
        assertTrue(kids.isUsable)
        assertTrue(!kids.copy(packages = emptySet()).isUsable)
    }
}
