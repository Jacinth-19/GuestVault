package com.guestvault.domain.repository

import com.guestvault.domain.model.GuestProfile
import kotlinx.coroutines.flow.Flow

/**
 * Saved guest profiles.
 *
 * Kept apart from [SettingsRepository] on purpose: profiles are the paid
 * feature, and the free build's settings file — the one every graded test step
 * reads — stays byte-for-byte what it was.
 */
interface ProfileRepository {
    val profiles: Flow<List<GuestProfile>>

    suspend fun upsert(profile: GuestProfile)
    suspend fun delete(id: String)
    suspend fun replaceAll(profiles: List<GuestProfile>)
}
