package com.guestvault.domain.policy

import kotlin.random.Random

/**
 * The emergency unlock code.
 *
 * A 4-digit PIN has 10,000 combinations and a guest can watch the owner type
 * it; a code that is written down at home and never typed in front of anyone
 * is the only recovery path that does not weaken the lock. Eight characters
 * from a 32-symbol alphabet is 2^40 — with the same cool-down as the PIN, that
 * is not guessable in the lifetime of the phone.
 *
 * The alphabet drops `I`, `O`, `0` and `1` so a code copied by hand off a
 * screen is never ambiguous. Only a salted hash is stored (the caller's job);
 * this object just generates, normalises and formats.
 */
object RecoveryCode {

    /** 8 symbols: 32^8 ≈ 1.1 × 10^12 combinations. */
    const val LENGTH = 8

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** "ABCD-EFGH" is easier to read back than "ABCDEFGH". */
    private const val GROUP = 4

    fun generate(random: Random = Random.Default): String =
        buildString(LENGTH) {
            repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }

    /**
     * Anything the owner types into the box becomes the canonical form:
     * upper case, symbols only. Dashes, spaces and lower case are all accepted
     * so a code copied with a stray space still works.
     */
    fun normalise(raw: String): String =
        raw.uppercase().filter { it in ALPHABET }

    fun format(code: String): String {
        val clean = normalise(code)
        if (clean.length != LENGTH) return clean
        return buildString {
            clean.chunked(GROUP).forEachIndexed { index, chunk ->
                if (index > 0) append('-')
                append(chunk)
            }
        }
    }

    fun isValid(code: String): Boolean = normalise(code).length == LENGTH
}
