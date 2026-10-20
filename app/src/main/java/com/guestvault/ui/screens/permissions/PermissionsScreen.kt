package com.guestvault.ui.screens.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.guestvault.ui.components.GuestVaultDivider
import com.guestvault.ui.components.GuestVaultTopBar
import com.guestvault.ui.components.IconBadge
import com.guestvault.ui.components.InfoBanner
import com.guestvault.ui.components.GuestTextButton
import com.guestvault.ui.components.StatusPill
import com.guestvault.ui.theme.Success
import com.guestvault.ui.theme.Warning

/**
 * Permission transparency screen (Settings → Permissions & privacy).
 *
 * Every permission GuestVault can hold is listed with its current state, plus
 * the permissions that are deliberately *not* requested. This is both a Play
 * Store review aid and the honest answer to "what can this app see?".
 */
@Composable
fun PermissionsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }

    // Re-read the permission state when the owner comes back from system
    // settings, so the pills are never stale.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state = remember(refreshKey) {
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        val biometricReady = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

        PermissionState(
            notificationsGranted = notificationsGranted,
            biometricReady = biometricReady,
            notificationsPermanentlyDenied = !notificationsGranted &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        )
    }

    PermissionsScreen(
        state = state,
        onBack = onBack,
        onOpenNotificationSettings = {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        },
        onOpenBiometricSettings = {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_BIOMETRIC_ENROLL)
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    )
}

data class PermissionState(
    val notificationsGranted: Boolean,
    val biometricReady: Boolean,
    val notificationsPermanentlyDenied: Boolean
)

@Composable
fun PermissionsScreen(
    state: PermissionState,
    onBack: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBiometricSettings: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GuestVaultTopBar(
                title = "Permissions & privacy",
                subtitle = "Everything GuestVault can and cannot access",
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
                InfoBanner(
                    icon = Icons.Rounded.Public,
                    title = "No internet permission",
                    text = "GuestVault has no network access at all. Settings, the PIN hash " +
                        "and session history stay on this device and cannot be uploaded."
                )
            }

            item {
                PermissionGroup(title = "Permissions on this device") {
                    PermissionRow(
                        icon = Icons.Rounded.Notifications,
                        title = "Notifications",
                        subtitle = if (state.notificationsGranted) {
                            "Shows the ongoing \"Guest Mode is on\" notice and the timer."
                        } else {
                            "Optional. Without it the session still runs, but the timer is silent."
                        },
                        status = if (state.notificationsGranted) "Granted" else "Not granted",
                        granted = state.notificationsGranted,
                        actionText = if (state.notificationsGranted) null else "Open settings",
                        onAction = onOpenNotificationSettings
                    )
                    GuestVaultDivider()
                    PermissionRow(
                        icon = Icons.Rounded.Fingerprint,
                        title = "Biometrics",
                        subtitle = if (state.biometricReady) {
                            "Only used to unlock owner mode. Fingerprints never reach GuestVault."
                        } else {
                            "No fingerprint or face is enrolled, so the PIN is the only way out."
                        },
                        status = if (state.biometricReady) "Available" else "Not set up",
                        granted = state.biometricReady,
                        actionText = if (state.biometricReady) null else "Enroll",
                        onAction = onOpenBiometricSettings
                    )
                    GuestVaultDivider()
                    PermissionRow(
                        icon = Icons.Rounded.Timer,
                        title = "Foreground service",
                        subtitle = "Keeps a running session (and its timer) alive while the " +
                            "guest is in another app. No runtime prompt.",
                        status = "Automatic",
                        granted = true
                    )
                    GuestVaultDivider()
                    PermissionRow(
                        icon = Icons.Rounded.RestartAlt,
                        title = "Start at boot",
                        subtitle = "Used only to close and wipe a session that was left open " +
                            "when the phone restarted. Nothing runs in the background otherwise.",
                        status = "Automatic",
                        granted = true
                    )
                }
            }

            item {
                PermissionGroup(title = "Never requested") {
                    NotRequestedRow(
                        Icons.Rounded.Public,
                        "Internet",
                        "No ads, no analytics, no sync. GuestVault cannot phone home."
                    )
                    GuestVaultDivider()
                    NotRequestedRow(
                        Icons.Rounded.LocationOff,
                        "Location",
                        "Guest Mode does not need to know where the phone is."
                    )
                    GuestVaultDivider()
                    NotRequestedRow(
                        Icons.Rounded.Contacts,
                        "Contacts",
                        "The guest list is a package allow-list, not your address book."
                    )
                    GuestVaultDivider()
                    NotRequestedRow(
                        Icons.Rounded.Sms,
                        "SMS & call log",
                        "Messages and calls are never read, even when the app is allowed."
                    )
                    GuestVaultDivider()
                    NotRequestedRow(
                        Icons.Rounded.PhotoLibrary,
                        "Photos & files",
                        "GuestVault does not touch shared storage. Only its own cache is wiped."
                    )
                    GuestVaultDivider()
                    NotRequestedRow(
                        Icons.Rounded.Accessibility,
                        "Accessibility service",
                        "Not used. Blocking apps is done with a launcher, not by spying on the screen."
                    )
                    GuestVaultDivider()
                    NotRequestedRow(
                        Icons.Rounded.AdminPanelSettings,
                        "Device admin",
                        "Not requested, so Play has nothing to review and you cannot be " +
                            "locked out by a rogue policy."
                    )
                    GuestVaultDivider()
                    NotRequestedRow(
                        Icons.Rounded.Block,
                        "Usage access",
                        "Not needed: the guest only sees the icons you approved."
                    )
                }
            }

            item {
                Text(
                    text = "You can revoke Notifications at any time in Android settings. " +
                        "Revoking biometrics only disables the fingerprint shortcut — the PIN " +
                        "always works.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: String,
    granted: Boolean,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.padding(start = 54.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                text = status,
                containerColor = if (granted) {
                    Success.copy(alpha = 0.16f)
                } else {
                    Warning.copy(alpha = 0.18f)
                },
                contentColor = MaterialTheme.colorScheme.onSurface
            )
            if (actionText != null && onAction != null) {
                Spacer(Modifier.width(8.dp))
                GuestTextButton(text = actionText, onClick = onAction)
            }
        }
    }
}

@Composable
private fun NotRequestedRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon = icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Rounded.Block,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
    }
}
