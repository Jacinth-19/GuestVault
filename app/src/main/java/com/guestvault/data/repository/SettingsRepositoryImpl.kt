package com.guestvault.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.guestvault.domain.model.EndReason
import com.guestvault.domain.model.GuestSession
import com.guestvault.domain.model.OwnerSettings
import com.guestvault.domain.model.SessionRecord
import com.guestvault.domain.model.ThemeMode
import com.guestvault.domain.model.UnlockGuard
import com.guestvault.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "guestvault_settings"
)

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    override val settings: Flow<OwnerSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) {
                Log.w(TAG, "Could not read settings, using defaults", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { it.toOwnerSettings() }
        .distinctUntilChanged()

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setAllowedPackages(packages: Set<String>) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.ALLOWED_PACKAGES] = packages.sorted().joinToString("\n")
        }
    }

    override suspend fun setAutoWipe(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_WIPE] = enabled }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    override suspend fun setLockTaskEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.LOCK_TASK_ENABLED] = enabled }
    }

    override suspend fun setDefaultSessionMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_SESSION_MINUTES] = minutes }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setActiveSession(session: GuestSession?) {
        context.settingsDataStore.edit { prefs ->
            if (session == null) {
                prefs.remove(Keys.SESSION_STARTED_AT)
                prefs.remove(Keys.SESSION_PACKAGES)
                prefs.remove(Keys.SESSION_DURATION)
                prefs.remove(Keys.SESSION_AUTO_WIPE)
                prefs.remove(Keys.SESSION_LOCK_TASK)
            } else {
                prefs[Keys.SESSION_STARTED_AT] = session.startedAt
                prefs[Keys.SESSION_PACKAGES] = session.packages.sorted().joinToString("\n")
                prefs[Keys.SESSION_DURATION] = session.durationMinutes
                prefs[Keys.SESSION_AUTO_WIPE] = session.autoWipe
                prefs[Keys.SESSION_LOCK_TASK] = session.lockTaskEnabled
            }
        }
    }

    override suspend fun addSessionRecord(record: SessionRecord) {
        context.settingsDataStore.edit { prefs ->
            val existing = decodeHistory(prefs[Keys.HISTORY])
            val updated = (listOf(record) + existing).take(MAX_HISTORY)
            prefs[Keys.HISTORY] = updated.joinToString("\n") { it.encode() }
        }
    }

    override suspend fun clearHistory() {
        context.settingsDataStore.edit { it.remove(Keys.HISTORY) }
    }

    override suspend fun readUnlockGuard(): UnlockGuard = runCatching {
        val prefs = context.settingsDataStore.data.first()
        UnlockGuard(
            failedAttempts = prefs[Keys.UNLOCK_FAILED_ATTEMPTS] ?: 0,
            lockedUntil = prefs[Keys.UNLOCK_LOCKED_UNTIL] ?: 0L
        )
    }.onFailure { Log.w(TAG, "Could not read unlock guard", it) }
        .getOrDefault(UnlockGuard())

    override suspend fun writeUnlockGuard(guard: UnlockGuard) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.UNLOCK_FAILED_ATTEMPTS] = guard.failedAttempts
            prefs[Keys.UNLOCK_LOCKED_UNTIL] = guard.lockedUntil
        }
    }

    // ------------------------------------------------------------------ mapping

    private fun Preferences.toOwnerSettings(): OwnerSettings {
        val session = this[Keys.SESSION_STARTED_AT]?.let { startedAt ->
            GuestSession(
                startedAt = startedAt,
                packages = decodePackages(this[Keys.SESSION_PACKAGES]),
                durationMinutes = this[Keys.SESSION_DURATION] ?: 0,
                autoWipe = this[Keys.SESSION_AUTO_WIPE] ?: true,
                lockTaskEnabled = this[Keys.SESSION_LOCK_TASK] ?: true
            )
        }

        return OwnerSettings(
            onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: false,
            allowedPackages = decodePackages(this[Keys.ALLOWED_PACKAGES]),
            autoWipe = this[Keys.AUTO_WIPE] ?: true,
            biometricEnabled = this[Keys.BIOMETRIC_ENABLED] ?: true,
            lockTaskEnabled = this[Keys.LOCK_TASK_ENABLED] ?: true,
            defaultSessionMinutes = this[Keys.DEFAULT_SESSION_MINUTES] ?: 0,
            themeMode = ThemeMode.entries.firstOrNull { it.name == this[Keys.THEME_MODE] }
                ?: ThemeMode.SYSTEM,
            activeSession = session,
            history = decodeHistory(this[Keys.HISTORY])
        )
    }

    private fun decodePackages(raw: String?): Set<String> =
        raw?.split("\n")?.map(String::trim)?.filter(String::isNotEmpty)?.toSet().orEmpty()

    private fun decodeHistory(raw: String?): List<SessionRecord> =
        raw?.lineSequence()
            ?.mapNotNull { line -> SessionRecordCodec.decode(line) }
            ?.toList()
            .orEmpty()

    private object SessionRecordCodec {
        fun decode(raw: String): SessionRecord? {
            val parts = raw.split("|")
            if (parts.size < 5) return null
            return runCatching {
                SessionRecord(
                    startedAt = parts[0].toLong(),
                    endedAt = parts[1].toLong(),
                    appCount = parts[2].toInt(),
                    autoWiped = parts[3] == "1",
                    endReason = EndReason.fromName(parts[4])
                )
            }.getOrNull()
        }
    }

    private fun SessionRecord.encode(): String =
        "$startedAt|$endedAt|$appCount|${if (autoWiped) 1 else 0}|${endReason.name}"

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val ALLOWED_PACKAGES = stringPreferencesKey("allowed_packages")
        val AUTO_WIPE = booleanPreferencesKey("auto_wipe")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val LOCK_TASK_ENABLED = booleanPreferencesKey("lock_task_enabled")
        val DEFAULT_SESSION_MINUTES = intPreferencesKey("default_session_minutes")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HISTORY = stringPreferencesKey("session_history")

        val SESSION_STARTED_AT = longPreferencesKey("session_started_at")
        val SESSION_PACKAGES = stringPreferencesKey("session_packages")
        val SESSION_DURATION = intPreferencesKey("session_duration_minutes")
        val SESSION_AUTO_WIPE = booleanPreferencesKey("session_auto_wipe")
        val SESSION_LOCK_TASK = booleanPreferencesKey("session_lock_task")

        val UNLOCK_FAILED_ATTEMPTS = intPreferencesKey("unlock_failed_attempts")
        val UNLOCK_LOCKED_UNTIL = longPreferencesKey("unlock_locked_until")
    }

    private companion object {
        const val TAG = "SettingsRepository"
        const val MAX_HISTORY = 50
    }
}
