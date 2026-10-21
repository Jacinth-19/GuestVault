package com.guestvault.ui.screens.confirm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.guestvault.core.Formatters
import com.guestvault.domain.model.AppCategory
import com.guestvault.domain.model.GuestApp
import com.guestvault.domain.repository.SettingsRepository
import com.guestvault.domain.usecase.GetInstalledAppsUseCase
import com.guestvault.domain.usecase.StartGuestSessionUseCase
import com.guestvault.ui.components.AppIcon
import com.guestvault.ui.components.GuestVaultCard
import com.guestvault.ui.components.GuestVaultTopBar
import com.guestvault.ui.components.InfoBanner
import com.guestvault.ui.components.PrimaryButton
import com.guestvault.ui.components.SectionHeader
import com.guestvault.ui.components.SettingSwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val DURATION_OPTIONS = listOf(0, 5, 15, 30, 60)

data class ConfirmUiState(
    val apps: List<GuestApp> = emptyList(),
    val durationMinutes: Int = 0,
    val autoWipe: Boolean = true,
    val lockTaskEnabled: Boolean = true,
    val biometricEnabled: Boolean = true,
    val starting: Boolean = false,
    val error: String? = null,
    val started: Boolean = false,
    val loaded: Boolean = false
)

@HiltViewModel
class ConfirmSessionViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val getInstalledApps: GetInstalledAppsUseCase,
    private val startGuestSession: StartGuestSessionUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ConfirmUiState())
    val state: StateFlow<ConfirmUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val installed = getInstalledApps().associateBy { it.packageName }
            val apps = settings.allowedPackages
                .map { pkg -> installed[pkg] ?: GuestApp(pkg, pkg, AppCategory.OTHER) }
                .sortedBy { it.label.lowercase() }

            _state.update {
                it.copy(
                    apps = apps,
                    durationMinutes = settings.defaultSessionMinutes,
                    autoWipe = settings.autoWipe,
                    lockTaskEnabled = settings.lockTaskEnabled,
                    biometricEnabled = settings.biometricEnabled,
                    loaded = true
                )
            }
        }
    }

    fun setDuration(minutes: Int) = _state.update { it.copy(durationMinutes = minutes, error = null) }

    fun setAutoWipe(enabled: Boolean) = _state.update { it.copy(autoWipe = enabled) }

    fun setLockTask(enabled: Boolean) = _state.update { it.copy(lockTaskEnabled = enabled) }

    fun start() {
        val current = _state.value
        if (current.starting || current.started) return
        if (current.apps.isEmpty()) {
            _state.update { it.copy(error = "Choose at least one app before starting.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(starting = true, error = null) }
            startGuestSession(
                packages = current.apps.map { it.packageName }.toSet(),
                durationMinutes = current.durationMinutes,
                autoWipe = current.autoWipe,
                lockTaskEnabled = current.lockTaskEnabled
            ).onSuccess {
                _state.update { state -> state.copy(starting = false, started = true) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        starting = false,
                        error = error.message ?: "Could not start the guest session."
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmSessionRoute(
    onBack: () -> Unit,
    onEditApps: () -> Unit,
    onSessionStarted: () -> Unit,
    viewModel: ConfirmSessionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.started) {
        if (state.started) onSessionStarted()
    }

    ConfirmSessionScreen(
        state = state,
        onBack = onBack,
        onEditApps = onEditApps,
        onDurationChange = viewModel::setDuration,
        onAutoWipeChange = viewModel::setAutoWipe,
        onLockTaskChange = viewModel::setLockTask,
        onStart = viewModel::start
    )
}

@Composable
fun ConfirmSessionScreen(
    state: ConfirmUiState,
    onBack: () -> Unit,
    onEditApps: () -> Unit,
    onDurationChange: (Int) -> Unit,
    onAutoWipeChange: (Boolean) -> Unit,
    onLockTaskChange: (Boolean) -> Unit,
    onStart: () -> Unit
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // The session must run even if notifications are declined.
        onStart()
    }

    val startWithPermission = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onStart()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GuestVaultTopBar(
                title = "Start guest session",
                subtitle = "One last look before you hand over",
                onBack = onBack
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    if (state.error != null) {
                        Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    PrimaryButton(
                        text = "Start Guest Mode",
                        onClick = startWithPermission,
                        loading = state.starting,
                        enabled = state.apps.isNotEmpty(),
                        icon = Icons.Rounded.PlayArrow
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (state.biometricEnabled) {
                            "Return with your PIN or fingerprint · ${state.apps.size} apps available"
                        } else {
                            "Return with your PIN · ${state.apps.size} apps available"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.loaded && state.apps.isEmpty()) {
                item {
                    InfoBanner(
                        icon = Icons.Rounded.Shield,
                        title = "Pick at least one app",
                        text = "Guests need something to do. Choose the apps they may open.",
                        actionText = "Choose apps",
                        onActionClick = onEditApps
                    )
                }
            }

            item {
                GuestVaultCard {
                    SectionHeader(
                        title = "Guest sees ${state.apps.size} apps",
                        actionText = "Edit",
                        onActionClick = onEditApps
                    )
                    Spacer(Modifier.height(6.dp))
                    state.apps.chunked(2).forEach { rowApps ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowApps.forEach { app ->
                                AppSummaryChip(app = app, modifier = Modifier.weight(1f))
                            }
                            if (rowApps.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item {
                GuestVaultCard {
                    SectionHeader(title = "Session length")
                    Text(
                        text = "The guest is automatically returned to you when the timer ends.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    // The chips show what exists; this line shows what is chosen,
                    // which is also the only place the choice is stated in words.
                    Text(
                        text = if (state.durationMinutes > 0) {
                            "Ends automatically after ${Formatters.minutesLabel(state.durationMinutes)}"
                        } else {
                            "No time limit"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DURATION_OPTIONS) { minutes ->
                            FilterChip(
                                selected = state.durationMinutes == minutes,
                                onClick = { onDurationChange(minutes) },
                                label = { Text(Formatters.minutesLabel(minutes)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            item {
                GuestVaultCard {
                    SectionHeader(title = "Protection")
                    SettingSwitchRow(
                        icon = Icons.Rounded.CleaningServices,
                        title = "Clear guest data when finished",
                        subtitle = "Wipes GuestVault's guest cache. Other apps need Device Owner mode.",
                        checked = state.autoWipe,
                        onCheckedChange = onAutoWipeChange,
                        switchDescription = "Auto-wipe toggle",
                        horizontalPadding = 0.dp
                    )
                    SettingSwitchRow(
                        icon = Icons.Rounded.Pin,
                        title = "Pin the screen",
                        subtitle = "Asks Android to keep the guest inside GuestVault. A system confirmation appears once.",
                        checked = state.lockTaskEnabled,
                        onCheckedChange = onLockTaskChange,
                        switchDescription = "Screen pinning toggle",
                        horizontalPadding = 0.dp
                    )
                }
            }

            item {
                InfoBanner(
                    icon = Icons.Rounded.Shield,
                    title = "Your data stays yours",
                    text = "Guests cannot open apps outside this list, and your owner PIN can't be " +
                        "changed from inside Guest Mode."
                )
            }

            item {
                InfoBanner(
                    icon = Icons.Rounded.Lock,
                    title = "Honest limits",
                    text = "The notification shade and hardware buttons remain available unless you " +
                        "enable screen pinning. Android does not let one app erase another app's " +
                        "data either, so a wipe only clears what this session created. True user " +
                        "isolation requires system privileges.",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AppSummaryChip(app: GuestApp, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(packageName = app.packageName, label = app.label, size = 26.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
