package com.guestvault.ui.screens.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.guestvault.core.Formatters
import com.guestvault.core.Premium
import com.guestvault.core.PremiumFeature
import com.guestvault.domain.model.GuestProfile
import com.guestvault.domain.repository.ProfileRepository
import com.guestvault.domain.repository.SettingsRepository
import com.guestvault.domain.usecase.ObserveSettingsUseCase
import com.guestvault.ui.components.ConfirmDialog
import com.guestvault.ui.components.EmptyState
import com.guestvault.ui.components.GuestVaultTopBar
import com.guestvault.ui.components.IconBadge
import com.guestvault.ui.components.InfoBanner
import com.guestvault.ui.components.PrimaryButton
import com.guestvault.ui.components.SecondaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfilesUiState(
    val profiles: List<GuestProfile> = emptyList(),
    val allowedCount: Int = 0,
    val defaultMinutes: Int = 0
)

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    observeSettings: ObserveSettingsUseCase
) : ViewModel() {

    val state: StateFlow<ProfilesUiState> =
        combine(profileRepository.profiles, observeSettings()) { profiles, settings ->
            ProfilesUiState(
                profiles = profiles,
                allowedCount = settings.allowedPackages.size,
                defaultMinutes = settings.defaultSessionMinutes
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesUiState())

    /** Set once a profile has been written into the settings; the screen navigates. */
    private val _applied = MutableStateFlow(false)
    val applied: StateFlow<Boolean> = _applied.asStateFlow()

    /**
     * Saves the *current* allow-list as a profile.
     *
     * A snapshot, not a link: editing the allow-list afterwards leaves the saved
     * profile alone, which is what makes "kids" still mean "kids" next week.
     */
    fun saveCurrentAs(name: String) = viewModelScope.launch {
        val settings = settingsRepository.settings.first()
        if (settings.allowedPackages.isEmpty()) return@launch
        profileRepository.upsert(
            GuestProfile(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifBlank { "Guest profile" },
                packages = settings.allowedPackages,
                durationMinutes = settings.defaultSessionMinutes,
                lockTaskEnabled = settings.lockTaskEnabled,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun delete(id: String) = viewModelScope.launch { profileRepository.delete(id) }

    /** Writes the profile into settings, then lets the screen open session setup. */
    fun startWith(profile: GuestProfile) = viewModelScope.launch {
        if (!profile.isUsable) return@launch
        settingsRepository.setAllowedPackages(profile.packages)
        settingsRepository.setDefaultSessionMinutes(profile.durationMinutes)
        settingsRepository.setLockTaskEnabled(profile.lockTaskEnabled)
        _applied.value = true
    }

    fun clearApplied() {
        _applied.value = false
    }
}

@Composable
fun ProfilesRoute(
    onBack: () -> Unit,
    onSessionSetup: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val applied by viewModel.applied.collectAsStateWithLifecycle()

    LaunchedEffect(applied) {
        if (applied) {
            viewModel.clearApplied()
            onSessionSetup()
        }
    }

    ProfilesScreen(
        state = state,
        onBack = onBack,
        onSaveCurrentAs = viewModel::saveCurrentAs,
        onDelete = viewModel::delete,
        onStartWith = viewModel::startWith
    )
}

@Composable
fun ProfilesScreen(
    state: ProfilesUiState,
    onBack: () -> Unit,
    onSaveCurrentAs: (String) -> Unit,
    onDelete: (String) -> Unit,
    onStartWith: (GuestProfile) -> Unit
) {
    var showNameDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<GuestProfile?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GuestVaultTopBar(
                title = "Guest profiles",
                subtitle = if (Premium.isPro) "Presets you can start in one tap" else "A GuestVault Pro feature",
                onBack = onBack
            )
        }
    ) { padding ->
        if (Premium.isLocked(PremiumFeature.PROFILES)) {
            LockedProfiles(padding = padding, onBack = onBack)
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                InfoBanner(
                    icon = Icons.Rounded.PersonOutline,
                    title = "A profile is a snapshot",
                    text = "Saving a profile copies the allow-list, the timer and the " +
                        "screen-pinning choice you have now. Changing the allow-list later " +
                        "leaves saved profiles untouched."
                )
            }

            item {
                PrimaryButton(
                    text = if (state.allowedCount == 0) {
                        "Allow some apps first"
                    } else {
                        "Save current setup (${state.allowedCount} apps)"
                    },
                    onClick = { showNameDialog = true },
                    enabled = state.allowedCount > 0,
                    icon = Icons.Rounded.Add
                )
            }

            if (state.profiles.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.WorkspacePremium,
                        title = "No profiles yet",
                        message = "Set up the allow-list the way this guest needs it, then " +
                            "save it here. Next time it is one tap."
                    )
                }
            } else {
                items(state.profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        onStart = { onStartWith(profile) },
                        onDelete = { pendingDelete = profile }
                    )
                }
            }
        }
    }

    if (showNameDialog) {
        NameDialog(
            suggestion = defaultName(state.profiles),
            onConfirm = { name ->
                showNameDialog = false
                onSaveCurrentAs(name)
            },
            onDismiss = { showNameDialog = false }
        )
    }

    pendingDelete?.let { profile ->
        ConfirmDialog(
            title = "Delete “${profile.name}”?",
            message = "The saved preset goes away. Your allow-list and sessions are not touched.",
            confirmLabel = "Delete profile",
            destructive = true,
            onConfirm = {
                pendingDelete = null
                onDelete(profile.id)
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: GuestProfile,
    onStart: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = Icons.Rounded.PersonOutline)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = buildString {
                            append(profile.appCount)
                            append(if (profile.appCount == 1) " app" else " apps")
                            append(" · ")
                            append(Formatters.minutesLabel(profile.durationMinutes))
                            append(" · ")
                            append(if (profile.lockTaskEnabled) "Screen pinning" else "Soft lock")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = "Start with this",
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    enabled = profile.isUsable,
                    icon = Icons.Rounded.PlayArrow
                )
                SecondaryButton(
                    text = "Delete",
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LockedProfiles(padding: PaddingValues, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        IconBadge(icon = Icons.Rounded.WorkspacePremium)
        Text(
            text = PremiumFeature.PROFILES.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = PremiumFeature.PROFILES.blurb,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        InfoBanner(
            icon = Icons.Rounded.Lock,
            title = "This is the free build",
            text = "GuestVault Pro is a separate paid app. It installs beside this one, " +
                "keeps your settings, and still has no network permission — there is " +
                "nothing to unlock here, and nothing to buy inside this app."
        )
        Spacer(Modifier.height(4.dp))
        SecondaryButton(text = "Back", onClick = onBack)
    }
}

@Composable
private fun NameDialog(
    suggestion: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(suggestion) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this profile") },
        text = {
            Column {
                Text(
                    text = "A short name you will recognise at a glance — “Kids”, " +
                        "“Work meeting”, “Neighbour”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    label = { Text("Profile name") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** "Kids", "Guest 2", … — a starting point, not a decision. */
private fun defaultName(existing: List<GuestProfile>): String {
    val used = existing.map { it.name }.toSet()
    if (!used.contains("Kids")) return "Kids"
    var index = 2
    while (used.contains("Guest $index")) index++
    return "Guest $index"
}
