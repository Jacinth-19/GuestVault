package com.guestvault.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.guestvault.core.AppVersion
import com.guestvault.core.Formatters
import com.guestvault.data.session.SessionController
import com.guestvault.domain.model.OwnerSettings
import com.guestvault.domain.model.SessionRecord
import com.guestvault.domain.usecase.ObserveSettingsUseCase
import com.guestvault.ui.components.AppIcon
import com.guestvault.ui.components.GuestVaultCard
import com.guestvault.ui.components.GuestVaultTopBar
import com.guestvault.ui.components.IconBadge
import com.guestvault.ui.components.InfoBanner
import com.guestvault.ui.components.SectionHeader
import com.guestvault.ui.components.StatusPill
import com.guestvault.ui.theme.HeroGradient
import com.guestvault.ui.theme.Indigo300
import com.guestvault.ui.theme.Indigo800
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val settings: OwnerSettings = OwnerSettings(),
    val loaded: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val sessionController: SessionController
) : ViewModel() {

    val state: StateFlow<DashboardUiState> = observeSettings()
        .map { DashboardUiState(settings = it, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun resumeGuestSession() = sessionController.openGuestUi()
}

@Composable
fun DashboardRoute(
    onStartSession: () -> Unit,
    onManageApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onStartSession = onStartSession,
        onResumeSession = viewModel::resumeGuestSession,
        onManageApps = onManageApps,
        onOpenSettings = onOpenSettings,
        onOpenHistory = onOpenHistory,
        onOpenAbout = onOpenAbout
    )
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onStartSession: () -> Unit,
    onResumeSession: () -> Unit,
    onManageApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val settings = state.settings

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GuestVaultTopBar(
                title = "GuestVault",
                subtitle = "Borrow my phone. Keep your privacy.",
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.semantics { contentDescription = "Settings" }
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (settings.hasActiveSession) {
                item {
                    InfoBanner(
                        icon = Icons.Rounded.LockOpen,
                        title = "Guest session in progress",
                        text = "Guest Mode is still active on this device.",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        actionText = "Resume guest mode",
                        onActionClick = onResumeSession
                    )
                }
            }

            item {
                HeroCard(
                    hasActiveSession = settings.hasActiveSession,
                    onStartSession = onStartSession,
                    onResumeSession = onResumeSession
                )
            }

            item { QuickStatsRow(settings) }

            item {
                AllowedAppsCard(
                    packages = settings.allowedPackages,
                    onManageApps = onManageApps
                )
            }

            if (settings.history.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Recent sessions",
                        actionText = "View all",
                        onActionClick = onOpenHistory
                    )
                }
                items(settings.history.take(2)) { record ->
                    HistoryRow(record = record)
                }
            }

            item {
                InfoBanner(
                    icon = Icons.Rounded.Info,
                    title = "How protected is this?",
                    text = "Guest profiles need system access, so GuestVault combines a " +
                        "restricted launcher, an app allow-list, screen pinning and a PIN-protected exit.",
                    actionText = "Learn more",
                    onActionClick = onOpenAbout
                )
            }

            item {
                Text(
                    text = "GuestVault ${AppVersion.display} · Android 10+ · " +
                        "Your data stays on this phone",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun HeroCard(
    hasActiveSession: Boolean,
    onStartSession: () -> Unit,
    onResumeSession: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(HeroGradient)
    ) {
        Icon(
            imageVector = Icons.Rounded.Shield,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = (-26).dp)
                .size(190.dp)
                .alpha(0.09f)
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = 250.dp, y = 160.dp)
                .clip(CircleShape)
                .background(Indigo300.copy(alpha = 0.10f))
        )

        Column(modifier = Modifier.padding(24.dp)) {
            StatusPill(
                text = if (hasActiveSession) "GUEST MODE ACTIVE" else "READY TO LEND",
                containerColor = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
                icon = if (hasActiveSession) Icons.Rounded.LockOpen else Icons.Rounded.Shield
            )

            Spacer(Modifier.height(18.dp))
            Text(
                text = if (hasActiveSession) {
                    "Your guest is\nstill using the phone."
                } else {
                    "Lend your phone.\nKeep your privacy."
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = MaterialTheme.typography.headlineSmall.lineHeight * 1.05f
            )

            Spacer(Modifier.height(10.dp))
            Text(
                text = if (hasActiveSession) {
                    "Return with your PIN to end the session and see your dashboard."
                } else {
                    "Create a temporary private space that only shows the apps you choose."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f)
            )

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = if (hasActiveSession) onResumeSession else onStartSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Indigo800
                )
            ) {
                Icon(
                    imageVector = if (hasActiveSession) Icons.Rounded.LockOpen else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (hasActiveSession) "Resume Guest Mode" else "Start Guest Session",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (hasActiveSession) {
                    "Protected by your owner PIN"
                } else {
                    "Takes a few seconds · Exit with your PIN"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun QuickStatsRow(settings: OwnerSettings) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Apps,
            value = settings.allowedPackages.size.toString(),
            label = "Allowed apps"
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Shield,
            value = if (settings.biometricEnabled) "PIN + bio" else "PIN",
            label = "Lock method"
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.CleaningServices,
            value = if (settings.autoWipe) "On" else "Off",
            label = "Auto-wipe"
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            IconBadge(icon = icon, size = 30.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AllowedAppsCard(packages: Set<String>, onManageApps: () -> Unit) {
    GuestVaultCard {
        SectionHeader(
            title = "Allowed apps",
            actionText = if (packages.isEmpty()) "Choose apps" else "Manage",
            onActionClick = onManageApps
        )

        if (packages.isEmpty()) {
            Row(
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(icon = Icons.Rounded.Apps)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "No apps selected yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Pick at least one app your guest can open.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val context = LocalContext.current
            val labeled = remember(packages) {
                packages.map { pkg ->
                    val label = runCatching {
                        val info = context.packageManager.getApplicationInfo(pkg, 0)
                        context.packageManager.getApplicationLabel(info).toString()
                    }.getOrDefault(pkg)
                    pkg to label
                }.sortedBy { it.second.lowercase() }
            }

            LazyRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(labeled, key = { it.first }) { (pkg, label) ->
                    Column(
                        modifier = Modifier.width(64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppIcon(packageName = pkg, label = label, size = 52.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(record: SessionRecord) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon = Icons.Rounded.History, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${Formatters.dayLabel(record.startedAt)} · ${Formatters.timeOnly(record.startedAt)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${record.appCount} apps · ${Formatters.duration(record.durationMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(
                text = if (record.autoWiped) "Wiped" else record.endReason.shortLabel,
                icon = if (record.autoWiped) Icons.Rounded.CleaningServices else Icons.Rounded.Timer
            )
        }
    }
}
