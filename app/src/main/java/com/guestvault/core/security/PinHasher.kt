package com.guestvault.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Slow, salted PIN hashing.
 *
 * A 4-digit PIN only has 10k combinations, so no hash makes it brute-force
 * proof. The point here is to avoid ever storing the PIN in plain text and to
 * make an offline attack against a stolen preferences file expensive.
 *
 * Format: `pbkdf2_sha256$<iterations>$<base64(salt + hash)>`
 */
object PinHasher {

    private const val PREFIX = "pbkdf2_sha256"
    private const val DEFAULT_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private val secureRandom = SecureRandom()

    fun hash(pin: String, iterations: Int = DEFAULT_ITERATIONS): String {
        require(pin.isNotEmpty()) { "PIN must not be empty" }
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val hash = derive(pin, salt, iterations)
        val combined = ByteArray(salt.size + hash.size)
        salt.copyInto(combined, 0)
        hash.copyInto(combined, salt.size)
        val encoded = Base64.getEncoder().encodeToString(combined)
        return PREFIX + "$" + iterations + "$" + encoded
    }

    fun verify(pin: String, stored: String?): Boolean {
        if (stored.isNullOrBlank()) return false
        val parts = stored.split("$")
        if (parts.size != 3 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations <= 0) return false
        val combined = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        if (combined.size <= SALT_BYTES) return false

        val salt = combined.copyOfRange(0, SALT_BYTES)
        val expected = combined.copyOfRange(SALT_BYTES, combined.size)
        val actual = derive(pin, salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }
}
