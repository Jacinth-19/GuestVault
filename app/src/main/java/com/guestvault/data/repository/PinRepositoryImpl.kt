package com.guestvault.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.guestvault.core.security.PinHasher
import com.guestvault.domain.repository.PinRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PIN storage.
 *
 * Primary path: [EncryptedSharedPreferences] (AES-256, key material in the
 * Android Keystore). Only a PBKDF2 hash is written, never the PIN itself.
 *
 * Fallback path: a private preferences file. This exists because some OEM
 * builds and emulators fail to initialise the keystore; losing the ability to
 * set a PIN would make the app unusable. The fallback still stores only a
 * salted hash — no plain text secret is ever written to disk.
 */
@Singleton
class PinRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PinRepository {

    private val encrypted: SharedPreferences? by lazy { openEncrypted() }
    private val fallback: SharedPreferences by lazy {
        context.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
    }

    private fun openEncrypted(): SharedPreferences? = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            ENCRYPTED_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) {
        Log.w(TAG, "Encrypted preferences unavailable, using hash-only fallback", t)
        null
    }

    /**
     * Both stores are written, and either can satisfy a read.
     *
     * The keystore can be invalidated (factory reset of the key material, an
     * emulator restore, a locked device after a reboot) while the app's own
     * preferences survive. Reading only the encrypted store in that state made
     * a set PIN look unset, and the app dropped the owner back into onboarding
     * mid-session. The stored value is a PBKDF2 hash either way.
     */
    private fun storedHash(): String? =
        runCatching { encrypted?.getString(KEY_PIN_HASH, null) }.getOrNull()
            ?: fallback.getString(KEY_PIN_HASH, null)

    override fun isPinSet(): Boolean = storedHash() != null

    override fun setPin(pin: String) {
        val hash = PinHasher.hash(pin)
        val now = System.currentTimeMillis()
        runCatching {
            encrypted?.edit()?.putString(KEY_PIN_HASH, hash)?.putLong(KEY_PIN_UPDATED_AT, now)?.apply()
        }
        fallback.edit().putString(KEY_PIN_HASH, hash).putLong(KEY_PIN_UPDATED_AT, now).apply()
    }

    override fun verifyPin(pin: String): Boolean {
        val stored = storedHash() ?: return false
        return PinHasher.verify(pin, stored)
    }

    override fun clearPin() {
        runCatching { encrypted?.edit()?.remove(KEY_PIN_HASH)?.remove(KEY_PIN_UPDATED_AT)?.apply() }
        fallback.edit().remove(KEY_PIN_HASH).remove(KEY_PIN_UPDATED_AT).apply()
    }

    private companion object {
        const val TAG = "PinRepository"
        const val ENCRYPTED_FILE = "guestvault_secure_pin"
        const val FALLBACK_FILE = "guestvault_pin_fallback"
        const val KEY_PIN_HASH = "owner_pin_hash"
        const val KEY_PIN_UPDATED_AT = "owner_pin_updated_at"
    }
}
