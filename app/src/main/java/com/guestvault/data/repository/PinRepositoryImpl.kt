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

    private val prefs: SharedPreferences by lazy { openPreferences() }

    private fun openPreferences(): SharedPreferences = try {
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
        context.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
    }

    override fun isPinSet(): Boolean = prefs.getString(KEY_PIN_HASH, null) != null

    override fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN_HASH, PinHasher.hash(pin)).apply()
        prefs.edit().putLong(KEY_PIN_UPDATED_AT, System.currentTimeMillis()).apply()
    }

    override fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return PinHasher.verify(pin, stored)
    }

    override fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).remove(KEY_PIN_UPDATED_AT).apply()
    }

    private companion object {
        const val TAG = "PinRepository"
        const val ENCRYPTED_FILE = "guestvault_secure_pin"
        const val FALLBACK_FILE = "guestvault_pin_fallback"
        const val KEY_PIN_HASH = "owner_pin_hash"
        const val KEY_PIN_UPDATED_AT = "owner_pin_updated_at"
    }
}
