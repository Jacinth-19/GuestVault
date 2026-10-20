package com.guestvault.guest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guestvault.ui.components.InfoBanner
import com.guestvault.ui.components.PinDots
import com.guestvault.ui.components.PinKeypad
import com.guestvault.ui.components.StatusPill
import com.guestvault.ui.theme.HeroGradient

private const val PIN_LENGTH = 4

/**
 * Full-screen owner lock shown inside Guest Mode.
 *
 * Exit requires the owner PIN (or a fingerprint). Five wrong attempts trigger a
 * 30 second cool-down so a guest cannot sit there guessing all 10,000 PINs.
 */
@Composable
fun GuestLockScreen(
    activity: GuestHomeActivity,
    hint: String?,
    onCancel: () -> Unit,
    onUnlocked: () -> Unit,
    viewModel: LockViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gate = remember(activity) { BiometricGate(activity) }
    val biometricHardware = remember(gate) { gate.isAvailable() }

    LaunchedEffect(Unit) { viewModel.reset() }
    LaunchedEffect(state.unlocked) {
        if (state.unlocked) onUnlocked()
    }
    DisposableEffect(Unit) {
        onDispose { gate.cancel() }
    }

    val keypadEnabled = state.guardLoaded && !state.lockedOut && !state.verifying
    val useBiometrics = biometricHardware && state.biometricEnabled

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                StatusPill(text = "Owner only", icon = Icons.Rounded.Shield)
            }

            Spacer(Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(HeroGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = if (hint != null) "Owner lock" else "Return to Owner",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = hint
                    ?: "Enter your PIN or use your fingerprint to end the guest session.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(26.dp))
            PinDots(length = PIN_LENGTH, filled = state.pinLength, error = state.error != null)

            Spacer(Modifier.height(10.dp))
            Text(
                text = when {
                    state.verifying -> "Checking…"
                    else -> state.error.orEmpty()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.heightIn(min = 38.dp)
            )

            if (state.wipeMode) {
                InfoBanner(
                    icon = Icons.Rounded.Shield,
                    text = "Guest data will be cleared as soon as you unlock.",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.height(14.dp))
            }

            PinKeypad(
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace,
                onBiometric = if (useBiometrics) {
                    {
                        gate.authenticate(
                            onSuccess = viewModel::onBiometricSucceeded,
                            onError = viewModel::onBiometricError
                        )
                    }
                } else {
                    null
                },
                enabled = keypadEnabled
            )

            Spacer(Modifier.height(10.dp))
            TextButton(onClick = viewModel::toggleWipeMode, enabled = keypadEnabled) {
                Text(
                    text = if (state.wipeMode) "Cancel data clear" else "End session & clear data",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
