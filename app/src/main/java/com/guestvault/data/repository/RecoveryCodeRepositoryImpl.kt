package com.guestvault.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.guestvault.core.security.PinHasher
import com.guestvault.domain.policy.RecoveryCode
import com.guestvault.domain.repository.RecoveryCodeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage for the emergency unlock code.
 *
 * The code is normalised before hashing, so `abcd-efgh` and `ABCD EFGH` verify
 * against the same stored value. Like the PIN, only a salted PBKDF2 hash is
 * written, and the same encrypted-preferences-plus-fallback pairing is used —
 * see [PinRepositoryImpl] for why both stores exist.
 */
@Singleton
class RecoveryCodeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : RecoveryCodeRepository {

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

    private fun storedHash(): String? =
        runCatching { encrypted?.getString(KEY_CODE_HASH, null) }.getOrNull()
            ?: fallback.getString(KEY_CODE_HASH, null)

    override fun hasCode(): Boolean = storedHash() != null

    override fun setCode(code: String) {
        val normalised = RecoveryCode.normalise(code)
        require(RecoveryCode.isValid(normalised)) { "A recovery code is ${RecoveryCode.LENGTH} symbols" }
        val hash = PinHasher.hash(normalised)
        val now = System.currentTimeMillis()
        runCatching {
            encrypted?.edit()?.putString(KEY_CODE_HASH, hash)?.putLong(KEY_CODE_CREATED_AT, now)?.apply()
        }
        fallback.edit().putString(KEY_CODE_HASH, hash).putLong(KEY_CODE_CREATED_AT, now).apply()
    }

    override fun verifyCode(code: String): Boolean {
        val stored = storedHash() ?: return false
        val normalised = RecoveryCode.normalise(code)
        if (!RecoveryCode.isValid(normalised)) return false
        return PinHasher.verify(normalised, stored)
    }

    override fun clearCode() {
        runCatching { encrypted?.edit()?.remove(KEY_CODE_HASH)?.remove(KEY_CODE_CREATED_AT)?.apply() }
        fallback.edit().remove(KEY_CODE_HASH).remove(KEY_CODE_CREATED_AT).apply()
    }

    private companion object {
        const val TAG = "RecoveryCodeRepository"
        const val ENCRYPTED_FILE = "guestvault_secure_pin"
        const val FALLBACK_FILE = "guestvault_pin_fallback"
        const val KEY_CODE_HASH = "owner_recovery_code_hash"
        const val KEY_CODE_CREATED_AT = "owner_recovery_code_created_at"
    }
}
