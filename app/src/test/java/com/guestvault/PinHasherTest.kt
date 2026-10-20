package com.guestvault

import com.guestvault.core.security.PinHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `hash verifies the original pin`() {
        val stored = PinHasher.hash("4726", iterations = 1_000)
        assertTrue(PinHasher.verify("4726", stored))
    }

    @Test
    fun `wrong pin is rejected`() {
        val stored = PinHasher.hash("4726", iterations = 1_000)
        assertFalse(PinHasher.verify("4727", stored))
        assertFalse(PinHasher.verify("", stored))
        assertFalse(PinHasher.verify("47260", stored))
    }

    @Test
    fun `salt makes identical pins produce different hashes`() {
        val first = PinHasher.hash("1234", iterations = 1_000)
        val second = PinHasher.hash("1234", iterations = 1_000)
        assertNotEquals(first, second)
        assertTrue(PinHasher.verify("1234", first))
        assertTrue(PinHasher.verify("1234", second))
    }

    @Test
    fun `plain text pins are never stored`() {
        val stored = PinHasher.hash("9876", iterations = 1_000)
        assertFalse(stored.contains("9876"))
        assertTrue(stored.startsWith("pbkdf2_sha256$"))
    }

    @Test
    fun `tampered or malformed hashes fail closed`() {
        assertFalse(PinHasher.verify("1234", null))
        assertFalse(PinHasher.verify("1234", ""))
        assertFalse(PinHasher.verify("1234", "not-a-hash"))
        assertFalse(PinHasher.verify("1234", "md5$1$abcd"))

        val stored = PinHasher.hash("1234", iterations = 1_000)
        val tampered = stored.dropLast(4) + "AAAA"
        assertFalse(PinHasher.verify("1234", tampered))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty pins cannot be hashed`() {
        PinHasher.hash("")
    }
}
