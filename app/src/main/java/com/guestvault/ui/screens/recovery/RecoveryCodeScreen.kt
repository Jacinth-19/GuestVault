package com.guestvault.ui.screens.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.guestvault.core.Premium
import com.guestvault.core.PremiumFeature
import com.guestvault.domain.policy.RecoveryCode
import com.guestvault.domain.repository.RecoveryCodeRepository
import com.guestvault.ui.components.GuestVaultTopBar
import com.guestvault.ui.components.IconBadge
import com.guestvault.ui.components.InfoBanner
import com.guestvault.ui.components.SecondaryButton
import com.guestvault.ui.theme.Warning
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecoveryUiState(
    val hasCode: Boolean = false,
    /**
     * A freshly generated code that has NOT been saved yet.
     *
     * It is deliberately held here rather than written straight to storage: the
     * code is shown exactly once, so the owner has to confirm they wrote it
     * down. Saving on generation would leave them locked out with a code they
     * never saw.
     */
    val pending: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class RecoveryCodeViewModel @Inject constructor(
    private val repository: RecoveryCodeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RecoveryUiState(hasCode = repository.hasCode()))
    val state: StateFlow<RecoveryUiState> = _state.asStateFlow()

    fun generate() {
        _state.update { it.copy(pending = RecoveryCode.generate(), saved = false) }
    }

    /** The owner says they have written it down. Only now is it stored. */
    fun savePending() {
        val code = _state.value.pending ?: return
        repository.setCode(code)
        _state.update { it.copy(hasCode = true, saved = true, pending = null) }
    }

    fun discardPending() = _state.update { it.copy(pending = null) }

    fun remove() {
        repository.clearCode()
        _state.update { RecoveryUiState(hasCode = false) }
    }
}

@Composable
fun RecoveryCodeRoute(
    onBack: () -> Unit,
    viewModel: RecoveryCodeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RecoveryCodeScreen(
        state = state,
        onBack = onBack,
        onGenerate = viewModel::generate,
        onSave = viewModel::savePending,
        onDiscard = viewModel::discardPending,
        onRemove = viewModel::remove
    )
}

@Composable
fun RecoveryCodeScreen(
    state: RecoveryUiState,
    onBack: () -> Unit,
    onGenerate: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onRemove: () -> Unit
) {
    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GuestVaultTopBar(
                title = "Emergency unlock code",
                subtitle = "One way back in if the PIN is forgotten",
                onBack = onBack
            )
        }
    ) { padding ->
        if (Premium.isLocked(PremiumFeature.RECOVERY_CODE)) {
            LockedRecovery(padding = padding, onBack = onBack)
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                InfoBanner(
                    icon = Icons.Rounded.Key,
                    title = "What this is",
                    text = "A ${RecoveryCode.LENGTH}-symbol code you keep somewhere other " +
                        "than this phone. On the lock screen it does what the PIN does, and " +
                        "then asks you to choose a new PIN. It is stored only as a hash — " +
                        "GuestVault cannot show it to you again."
                )
            }

            state.pending?.let { code ->
                item { PendingCodeCard(code = code) }
                item {
                    InfoBanner(
                        icon = Icons.Rounded.WarningAmber,
                        title = "Write it down now",
                        text = "This is the only time it is shown. If you lose it and forget " +
                            "the PIN, nothing can unlock the phone short of clearing the app.",
                        containerColor = Warning.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(text = "I've written it down", onClick = onSave)
                        SecondaryButton(text = "Show me a different code", onClick = onGenerate)
                        SecondaryButton(text = "Cancel", onClick = onDiscard)
                    }
                }
            } ?: item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.hasCode) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconBadge(icon = Icons.Rounded.Key)
                                Spacer(Modifier.height(0.dp))
                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(
                                        text = "A code is saved",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "GuestVault stores only its hash, so it cannot " +
                                            "be shown here. Replace it if you lost the paper.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    SecondaryButton(
                        text = if (state.hasCode) "Replace the code" else "Create a code",
                        onClick = onGenerate
                    )
                    if (state.hasCode) {
                        SecondaryButton(text = "Remove the code", onClick = onRemove)
                    }
                }
            }

            item {
                Text(
                    text = "The code is checked by the same cool-down as the PIN: five wrong " +
                        "tries start the same 30-second wait, doubling each round. A recovery " +
                        "route without a limit would be the weakest part of the lock.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PendingCodeCard(code: String) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 26.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = RecoveryCode.format(code),
                fontFamily = FontFamily.Monospace,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Letters and digits only — I, O, 0 and 1 are never used.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LockedRecovery(padding: PaddingValues, onBack: () -> Unit) {
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
            text = PremiumFeature.RECOVERY_CODE.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = PremiumFeature.RECOVERY_CODE.blurb,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        InfoBanner(
            icon = Icons.Rounded.Lock,
            title = "This is the free build",
            text = "GuestVault Pro is a separate paid app. It installs beside this one and " +
                "keeps your settings — there is nothing to unlock here, and nothing to buy " +
                "inside this app."
        )
        SecondaryButton(text = "Back", onClick = onBack)
    }
}
