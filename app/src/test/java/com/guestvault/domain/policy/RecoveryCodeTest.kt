package com.guestvault.domain.policy

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodeTest {

    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toSet()

    @Test
    fun `a generated code is eight symbols from an unambiguous alphabet`() {
        repeat(200) {
            val code = RecoveryCode.generate(Random(it))
            assertEquals(RecoveryCode.LENGTH, code.length)
            assertTrue("unexpected symbol in $code", code.all { it in alphabet })
            assertTrue(RecoveryCode.isValid(code))
        }
    }

    @Test
    fun `the alphabet has no look-alike characters`() {
        val code = RecoveryCode.generate(Random(7))
        listOf('I', 'O', '0', '1').forEach { assertFalse("$it in $code", it in code) }
    }

    @Test
    fun `two codes are not the same`() {
        val codes = (0 until 50).map { RecoveryCode.generate(Random(it)) }.toSet()
        assertEquals(50, codes.size)
    }

    @Test
    fun `typing it with dashes, spaces or lower case still works`() {
        val code = "ABCDEFGH"
        assertEquals("ABCDEFGH", RecoveryCode.normalise("abcd-efgh"))
        assertEquals("ABCDEFGH", RecoveryCode.normalise(" ABCD EFGH "))
        assertEquals("ABCDEFGH", RecoveryCode.normalise("abcd—efgh"))
    }

    @Test
    fun `it displays in two groups of four`() {
        assertEquals("ABCD-EFGH", RecoveryCode.format("ABCDEFGH"))
        assertEquals("ABCD-EFGH", RecoveryCode.format("abcd efgh"))
        // A half-typed code is shown as typed, not padded or refused.
        assertEquals("ABC", RecoveryCode.format("abc"))
    }

    @Test
    fun `a short or empty code is not valid`() {
        assertFalse(RecoveryCode.isValid(""))
        assertFalse(RecoveryCode.isValid("ABCD"))
        assertFalse(RecoveryCode.isValid("ABCDEFG"))
        assertTrue(RecoveryCode.isValid("ABCD-EFGH"))
        assertTrue(RecoveryCode.isValid("abcdefgh"))
    }

    @Test
    fun `ambiguous symbols typed by mistake do not become valid characters`() {
        // O and 0 are dropped, so a code containing them simply comes up short.
        assertEquals("AB", RecoveryCode.normalise("A0OB"))
        assertFalse(RecoveryCode.isValid("O0O0O0O0"))
    }
}
