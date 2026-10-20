package com.guestvault.guest

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.guestvault.core.Formatters
import com.guestvault.domain.model.EndReason
import com.guestvault.domain.model.GuestApp
import com.guestvault.ui.components.AppIcon
import com.guestvault.ui.components.EmptyState
import com.guestvault.ui.components.StatusPill
import com.guestvault.ui.theme.HeroGradient
import kotlinx.coroutines.delay

@Composable
fun GuestHomeRoute(
    state: GuestUiState,
    activity: GuestHomeActivity,
    viewModel: GuestViewModel,
    onLaunchApp: (String) -> Unit,
    onEndSession: (EndReason) -> Unit,
    onReturnToOwner: () -> Unit
) {
    var showLock by rememberSaveable { mutableStateOf(false) }
    var lockHint by remember { mutableStateOf<String?>(null) }
    var pinned by remember { mutableStateOf(activity.isInLockTaskMode()) }
    var message by remember { mutableStateOf<String?>(null) }

    // Refresh the pinned indicator when we come back from the system dialog.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) pinned = activity.isInLockTaskMode()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message = it }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2_500)
            message = null
        }
    }

    // Screen pinning is opt-in per session (Confirm screen / Settings).
    LaunchedEffect(state.session?.lockTaskEnabled) {
        if (state.session?.lockTaskEnabled == true) activity.startPinnedMode()
    }

    // The session can end from the lock screen, the timer service or a wipe.
    LaunchedEffect(state.loaded, state.session) {
        if (state.loaded && state.session == null) onReturnToOwner()
    }

    // Never let Back leave the guest space: it opens the owner lock instead.
    BackHandler(enabled = !showLock) {
        lockHint = "Enter your PIN to leave Guest Mode"
        showLock = true
    }

    if (showLock) {
        GuestLockScreen(
            activity = activity,
            hint = lockHint,
            onCancel = {
                showLock = false
                lockHint = null
            },
            onUnlocked = onReturnToOwner
        )
    } else {
        GuestHomeScreen(
            state = state,
            pinned = pinned,
            message = message,
            onOpenLock = {
                lockHint = null
                showLock = true
            },
            onLaunchApp = onLaunchApp,
            onEndSession = onEndSession
        )
    }
}

@Composable
fun GuestHomeScreen(
    state: GuestUiState,
    pinned: Boolean,
    message: String?,
    onOpenLock: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onEndSession: (EndReason) -> Unit
) {
    val session = state.session ?: return

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val remaining = session.remainingMillis(now)

    // Redundant safety net in case the foreground service was killed.
    LaunchedEffect(remaining) {
        if (remaining != null && remaining <= 0L) onEndSession(EndReason.TIMER_EXPIRED)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp)
                .background(HeroGradient)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-60).dp)
                .size(230.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // ---------------------------------------------------------- header
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 12.dp, top = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Guest Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Only approved apps",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(
                        onClick = onOpenLock,
                        modifier = Modifier.semantics { contentDescription = "Lock screen" }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                Text(
                    text = "Welcome to Guest Mode",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "This phone is in a temporary, limited space.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Spacer(Modifier.height(14.dp))

                if (remaining != null) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Ends in ${Formatters.countdown(remaining)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    StatusPill(
                        text = "No time limit",
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White
                    )
                }

                Spacer(Modifier.height(26.dp))
            }

            // ------------------------------------------------------------ sheet
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Apps for this session",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.weight(1f))
                        StatusPill(
                            text = if (pinned) "Pinned" else "Soft lock",
                            icon = if (pinned) Icons.Rounded.Pin else Icons.Rounded.Lock
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    if (state.apps.isEmpty()) {
                        Box(modifier = Modifier.weight(1f)) {
                            EmptyState(
                                icon = Icons.Rounded.Shield,
                                title = "No apps allowed",
                                message = "Ask the owner to add apps for this guest session, then try again."
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(state.apps, key = { it.packageName }) { app ->
                                GuestAppTile(app = app, onClick = { onLaunchApp(app.packageName) })
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = onOpenLock,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Return to Owner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (state.settings.biometricEnabled) {
                            "Your host's PIN or fingerprint is required to unlock"
                        } else {
                            "Your host's PIN is required to unlock"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (message != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 110.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.inverseSurface
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun GuestAppTile(app: GuestApp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Open ${app.label}" }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(packageName = app.packageName, label = app.label, size = 54.dp)
        Spacer(Modifier.height(7.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
