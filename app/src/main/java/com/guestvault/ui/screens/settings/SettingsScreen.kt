package com.guestvault.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.biometric.BiometricManager
import com.guestvault.core.AppVersion
import com.guestvault.core.Formatters
import com.guestvault.core.LegalText
import com.guestvault.domain.model.OwnerSettings
import com.guestvault.domain.model.ThemeMode
import com.guestvault.domain.repository.PinRepository
import com.guestvault.domain.repository.SettingsRepository
import com.guestvault.domain.usecase.ObserveSettingsUseCase
import com.guestvault.ui.components.GuestVaultDivider
import com.guestvault.ui.components.GuestVaultTopBar
import com.guestvault.ui.components.IconBadge
import com.guestvault.ui.components.LongTextDialog
import com.guestvault.ui.components.SettingRow
import com.guestvault.ui.components.SettingSwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val DURATION_OPTIONS = listOf(0, 5, 15, 30, 60)

data class SettingsUiState(
    val settings: OwnerSettings = OwnerSettings(),
    val pinSet: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val settingsRepository: SettingsRepository,
    private val pinRepository: PinRepository
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = observeSettings()
        .map { SettingsUiState(settings = it, pinSet = pinRepository.isPinSet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setAutoWipe(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoWipe(enabled)
    }

    fun setBiometric(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setBiometricEnabled(enabled)
    }

    fun setLockTask(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setLockTaskEnabled(enabled)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settingsRepository.setThemeMode(mode)
    }

    fun setDefaultSessionMinutes(minutes: Int) = viewModelScope.launch {
        settingsRepository.setDefaultSessionMinutes(minutes)
    }

    fun setTimerAlerts(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setNotifyTimerAlerts(enabled)
    }

    fun setSessionSummary(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setNotifySessionSummary(enabled)
    }
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onManageApps: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPermissions: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onChangePin = onChangePin,
        onManageApps = onManageApps,
        onOpenHistory = onOpenHistory,
        onOpenAbout = onOpenAbout,
        onOpenPermissions = onOpenPermissions,
        onAutoWipeChange = viewModel::setAutoWipe,
        onBiometricChange = viewModel::setBiometric,
        onLockTaskChange = viewModel::setLockTask,
        onThemeChange = viewModel::setThemeMode,
        onDefaultSessionChange = viewModel::setDefaultSessionMinutes,
        onTimerAlertsChange = viewModel::setTimerAlerts,
        onSessionSummaryChange = viewModel::setSessionSummary
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onManageApps: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPermissions: () -> Unit,
    onAutoWipeChange: (Boolean) -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onLockTaskChange: (Boolean) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDefaultSessionChange: (Int) -> Unit,
    onTimerAlertsChange: (Boolean) -> Unit,
    onSessionSummaryChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val settings = state.settings
    val biometricAvailable = remember(context) {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    val openSystemNotificationSettings: () -> Unit = {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { context.startActivity(intent) }
    }

    var showDurationDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GuestVaultTopBar(
                title = "Settings",
                subtitle = "Owner-only controls",
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                SettingsGroup(title = "Security") {
                    SettingRow(
                        icon = Icons.Rounded.Lock,
                        title = "Change owner PIN",
                        subtitle = if (state.pinSet) {
                            "A 4-digit PIN protects owner mode"
                        } else {
                            "Set a PIN to protect owner mode"
                        },
                        onClick = onChangePin
                    )
                    GuestVaultDivider()
                    SettingSwitchRow(
                        icon = Icons.Rounded.Fingerprint,
                        title = "Biometric unlock",
                        subtitle = if (biometricAvailable) {
                            "Use your fingerprint or face to leave Guest Mode"
                        } else {
                            "No fingerprint or face is enrolled on this device"
                        },
                        checked = settings.biometricEnabled && biometricAvailable,
                        onCheckedChange = onBiometricChange,
                        enabled = biometricAvailable,
                        switchDescription = "Biometric toggle"
                    )
                    GuestVaultDivider()
                    SettingSwitchRow(
                        icon = Icons.Rounded.Pin,
                        title = "Screen pinning",
                        subtitle = "Ask Android to keep guests inside GuestVault",
                        checked = settings.lockTaskEnabled,
                        onCheckedChange = onLockTaskChange,
                        switchDescription = "Lock task toggle"
                    )
                }
            }

            item {
                SettingsGroup(title = "Guest session") {
                    SettingRow(
                        icon = Icons.Rounded.Apps,
                        title = "Allowed apps",
                        subtitle = "Choose what guests can open",
                        trailingText = "${settings.allowedPackages.size}",
                        onClick = onManageApps
                    )
                    GuestVaultDivider()
                    SettingSwitchRow(
                        icon = Icons.Rounded.CleaningServices,
                        title = "Auto-wipe on exit",
                        subtitle = "Clear GuestVault's guest cache when a session ends",
                        checked = settings.autoWipe,
                        onCheckedChange = onAutoWipeChange,
                        switchDescription = "Auto-wipe toggle"
                    )
                    GuestVaultDivider()
                    SettingRow(
                        icon = Icons.Rounded.Timer,
                        title = "Default session timer",
                        subtitle = "Automatically end sessions",
                        trailingText = Formatters.minutesLabel(settings.defaultSessionMinutes),
                        onClick = { showDurationDialog = true }
                    )
                    GuestVaultDivider()
                    SettingRow(
                        icon = Icons.Rounded.History,
                        title = "Session history",
                        subtitle = "Recently ended sessions on this device",
                        trailingText = "${settings.history.size}",
                        onClick = onOpenHistory
                    )
                }
            }

            item {
                SettingsGroup(title = "Notifications") {
                    SettingSwitchRow(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "Timer reminders",
                        subtitle = "Warn me before a timed session ends",
                        checked = settings.notifyTimerAlerts,
                        onCheckedChange = onTimerAlertsChange,
                        switchDescription = "Timer reminder toggle"
                    )
                    GuestVaultDivider()
                    SettingSwitchRow(
                        icon = Icons.Rounded.Summarize,
                        title = "Session summary",
                        subtitle = "A short recap after each session ends",
                        checked = settings.notifySessionSummary,
                        onCheckedChange = onSessionSummaryChange,
                        switchDescription = "Session summary toggle"
                    )
                    GuestVaultDivider()
                    SettingRow(
                        icon = Icons.Rounded.NotificationsNone,
                        title = "Android notification settings",
                        subtitle = "Per-channel sound, vibration and lock-screen options",
                        onClick = openSystemNotificationSettings
                    )
                }
            }

            item {
                SettingsGroup(title = "Appearance") {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(icon = Icons.Rounded.DarkMode)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Theme",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Follow the system or pick one",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onThemeChange(mode) },
                                label = { Text(mode.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (mode) {
                                            ThemeMode.SYSTEM -> Icons.Rounded.SettingsBrightness
                                            ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                            ThemeMode.DARK -> Icons.Rounded.DarkMode
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroup(title = "About") {
                    SettingRow(
                        icon = Icons.Rounded.Shield,
                        title = "Permissions & privacy",
                        subtitle = "What GuestVault can access, and what it never asks for",
                        onClick = onOpenPermissions
                    )
                    GuestVaultDivider()
                    SettingRow(
                        icon = Icons.Rounded.Security,
                        title = "How GuestVault works",
                        subtitle = "Limitations, permissions and the honest bits",
                        onClick = onOpenAbout
                    )
                    GuestVaultDivider()
                    SettingRow(
                        icon = Icons.Rounded.PrivacyTip,
                        title = "Privacy policy",
                        subtitle = "What is stored, and what never leaves the phone",
                        onClick = { showPrivacyDialog = true }
                    )
                    GuestVaultDivider()
                    SettingRow(
                        icon = Icons.Rounded.Info,
                        title = "Version",
                        subtitle = "GuestVault for Android 10+",
                        trailingText = AppVersion.display
                    )
                }
            }

            item {
                Text(
                    text = "Borrow my phone. Keep your privacy.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    if (showDurationDialog) {
        DurationDialog(
            selectedMinutes = settings.defaultSessionMinutes,
            onSelect = {
                onDefaultSessionChange(it)
                showDurationDialog = false
            },
            onDismiss = { showDurationDialog = false }
        )
    }

    if (showPrivacyDialog) {
        LongTextDialog(
            title = "Privacy policy",
            body = LegalText.PRIVACY_POLICY,
            onDismiss = { showPrivacyDialog = false }
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun DurationDialog(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default session timer") },
        text = {
            Column {
                Text(
                    text = "New sessions start with this limit. You can change it per session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                DURATION_OPTIONS.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMinutes == minutes,
                            onClick = { onSelect(minutes) }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = Formatters.minutesLabel(minutes),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
