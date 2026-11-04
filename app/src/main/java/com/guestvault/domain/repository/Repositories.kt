package com.guestvault.domain.repository

import com.guestvault.domain.model.DailyUsage
import com.guestvault.domain.model.GuestApp
import com.guestvault.domain.model.GuestSchedule
import com.guestvault.domain.model.GuestSession
import com.guestvault.domain.model.OwnerSettings
import com.guestvault.domain.model.SessionRecord
import com.guestvault.domain.model.ThemeMode
import com.guestvault.domain.model.UnlockGuard
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for everything the owner configured.
 * Backed by an on-device DataStore file (see SettingsRepositoryImpl).
 */
interface SettingsRepository {
    val settings: Flow<OwnerSettings>

    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setAllowedPackages(packages: Set<String>)
    suspend fun setAutoWipe(enabled: Boolean)
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun setLockTaskEnabled(enabled: Boolean)
    suspend fun setDefaultSessionMinutes(minutes: Int)
    suspend fun setNotifyTimerAlerts(enabled: Boolean)
    suspend fun setNotifySessionSummary(enabled: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setAppLimits(limits: Map<String, Int>)
    suspend fun setSchedule(schedule: GuestSchedule)
    suspend fun setDailyBudgetMinutes(minutes: Int)
    suspend fun setPinDigits(digits: Int)
    suspend fun setIntruderPhotosEnabled(enabled: Boolean)

    /**
     * Guest time recorded for one calendar day.
     *
     * Implementations keep a single day's total and drop anything older: the
     * budget only ever asks about today, and storing a history of them would be
     * a record of when the phone was lent out.
     */
    suspend fun readDailyUsage(): DailyUsage
    suspend fun addDailyUsage(epochDay: Long, millis: Long)
    suspend fun setActiveSession(session: GuestSession?)
    suspend fun addSessionRecord(record: SessionRecord)
    suspend fun clearHistory()

    /** Brute-force guard for the owner lock; survives process death. */
    suspend fun readUnlockGuard(): UnlockGuard
    suspend fun writeUnlockGuard(guard: UnlockGuard)
}

/**
 * Stores the owner PIN — four digits by default, six when the owner chose the
 * paid option.
 *
 * The raw PIN is never persisted: implementations must only store a slow,
 * salted hash (PBKDF2) inside an encrypted preference file.
 */
interface PinRepository {
    fun isPinSet(): Boolean
    fun setPin(pin: String)
    fun verifyPin(pin: String): Boolean
    fun clearPin()
}

/**
 * The emergency unlock code.
 *
 * Separate from [PinRepository] because it has a different lifecycle: it is
 * generated in the owner-only screens, shown exactly once, and can be replaced
 * without touching the PIN. Like the PIN, only a salted hash is ever stored.
 */
interface RecoveryCodeRepository {
    fun hasCode(): Boolean
    fun setCode(code: String)
    fun verifyCode(code: String): Boolean
    fun clearCode()
}

/** The device's launchable apps, filtered for guest-mode use. */
interface AppCatalogRepository {
    suspend fun getLaunchableApps(): List<GuestApp>
}
