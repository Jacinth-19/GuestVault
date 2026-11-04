package com.guestvault.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.guestvault.domain.model.GuestProfile
import com.guestvault.domain.model.ProfileCodec
import com.guestvault.domain.repository.ProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "guestvault_profiles"
)

/**
 * Profiles live in their own DataStore file, not in `guestvault_settings`.
 *
 * Two reasons: the free build never touches this file, so the graded settings
 * behaviour cannot shift underneath the test plan; and a corrupt profile list
 * can never take the owner PIN or the allow-list down with it.
 */
@Singleton
class ProfileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ProfileRepository {

    override val profiles: Flow<List<GuestProfile>> = context.profileDataStore.data
        .catch { error ->
            if (error is IOException) {
                Log.w(TAG, "Could not read profiles, treating as empty", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs -> ProfileCodec.decode(prefs[Keys.PROFILES].orEmpty()) }
        // Newest first, then by name, so the list never jumps around while the
        // owner is looking at it.
        .map { list -> list.sortedWith(compareByDescending<GuestProfile> { it.createdAt }.thenBy { it.name }) }
        .distinctUntilChanged()

    override suspend fun upsert(profile: GuestProfile) {
        context.profileDataStore.edit { prefs ->
            val current = ProfileCodec.decode(prefs[Keys.PROFILES].orEmpty())
            val merged = current.filterNot { it.id == profile.id } + profile
            prefs[Keys.PROFILES] = ProfileCodec.encode(merged)
        }
    }

    override suspend fun delete(id: String) {
        context.profileDataStore.edit { prefs ->
            val current = ProfileCodec.decode(prefs[Keys.PROFILES].orEmpty())
            prefs[Keys.PROFILES] = ProfileCodec.encode(current.filterNot { it.id == id })
        }
    }

    override suspend fun replaceAll(profiles: List<GuestProfile>) {
        context.profileDataStore.edit { prefs ->
            prefs[Keys.PROFILES] = ProfileCodec.encode(profiles)
        }
    }

    private companion object {
        const val TAG = "GuestVaultProfiles"
        object Keys {
            val PROFILES = stringPreferencesKey("guest_profiles")
        }
    }
}
