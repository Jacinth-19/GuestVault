package com.guestvault.domain.repository

import com.guestvault.domain.model.GuestApp
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
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setActiveSession(session: GuestSession?)
    suspend fun addSessionRecord(record: SessionRecord)
    suspend fun clearHistory()

    /** Brute-force guard for the owner lock; survives process death. */
    suspend fun readUnlockGuard(): UnlockGuard
    suspend fun writeUnlockGuard(guard: UnlockGuard)
}

/**
 * Stores the 4-digit owner PIN.
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

/** The device's launchable apps, filtered for guest-mode use. */
interface AppCatalogRepository {
    suspend fun getLaunchableApps(): List<GuestApp>
}
