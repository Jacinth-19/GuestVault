package com.guestvault.ui.screens.pin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.guestvault.domain.model.ThemeMode
import com.guestvault.domain.repository.PinRepository
import com.guestvault.domain.usecase.VerifyPinUseCase
import com.guestvault.ui.components.GuestVaultTopBar
import com.guestvault.ui.components.PinDots
import com.guestvault.ui.components.PinKeypad
import com.guestvault.ui.components.StatusPill
import com.guestvault.ui.theme.HeroGradient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PIN_LENGTH = 4

enum class PinMode { SETUP, CHANGE }

enum class PinStage { CURRENT, NEW, CONFIRM }

data class PinSetupUiState(
    val mode: PinMode = PinMode.SETUP,
    val stage: PinStage = PinStage.NEW,
    val title: String = "Create your owner PIN",
    val subtitle: String = "You'll use these four digits to return from Guest Mode.",
    val pill: String = "Step 1 of 2",
    val filled: Int = 0,
    val error: String? = null,
    val verifying: Boolean = false,
    val completed: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

@HiltViewModel
class PinSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pinRepository: PinRepository,
    private val verifyPin: VerifyPinUseCase
) : ViewModel() {

    private val mode = if (savedStateHandle.get<String>("mode") == "change") {
        PinMode.CHANGE
    } else {
        PinMode.SETUP
    }

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<PinSetupUiState> = _state.asStateFlow()

    private var entered = ""
    private var firstEntry = ""

    private fun initialState(): PinSetupUiState = if (mode == PinMode.CHANGE) {
        PinSetupUiState(
            mode = mode,
            stage = PinStage.CURRENT,
            title = "Enter your current PIN",
            subtitle = "Confirm it's you before choosing a new PIN.",
            pill = "Change PIN · 1 of 3"
        )
    } else {
        PinSetupUiState(mode = mode)
    }

    fun onDigit(digit: Char) {
        val current = _state.value
        if (current.verifying || current.completed) return
        if (entered.length >= PIN_LENGTH) return

        entered += digit
        _state.update { it.copy(filled = entered.length, error = null) }
        if (entered.length == PIN_LENGTH) handleComplete(entered)
    }

    fun onBackspace() {
        if (_state.value.verifying) return
        if (entered.isNotEmpty()) entered = entered.dropLast(1)
        _state.update { it.copy(filled = entered.length) }
    }

    private fun handleComplete(pin: String) {
        when (_state.value.stage) {
            PinStage.CURRENT -> verifyCurrent(pin)
            PinStage.NEW -> advanceToConfirm(pin)
            PinStage.CONFIRM -> confirmPin(pin)
        }
    }

    private fun verifyCurrent(pin: String) {
        viewModelScope.launch {
            _state.update { it.copy(verifying = true) }
            val ok = verifyPin(pin)
            entered = ""
            if (ok) {
                _state.update {
                    it.copy(
                        stage = PinStage.NEW,
                        title = "Choose a new PIN",
                        subtitle = "Pick four digits you will remember.",
                        pill = "Change PIN · 2 of 3",
                        filled = 0,
                        verifying = false
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        filled = 0,
                        verifying = false,
                        error = "That PIN doesn't match. Try again."
                    )
                }
            }
        }
    }

    private fun advanceToConfirm(pin: String) {
        firstEntry = pin
        entered = ""
        _state.update {
            it.copy(
                stage = PinStage.CONFIRM,
                title = "Confirm your PIN",
                subtitle = "Enter the same four digits one more time.",
                pill = "Step 2 of 2",
                filled = 0,
                error = null
            )
        }
    }

    private fun confirmPin(pin: String) {
        if (pin == firstEntry) {
            pinRepository.setPin(pin)
            _state.update { it.copy(completed = true, filled = PIN_LENGTH, error = null) }
        } else {
            entered = ""
            firstEntry = ""
            _state.update {
                it.copy(
                    stage = PinStage.NEW,
                    title = "Choose a new PIN",
                    subtitle = "The PINs didn't match, so let's start again.",
                    pill = if (mode == PinMode.CHANGE) "Change PIN · 2 of 3" else "Step 1 of 2",
                    filled = 0,
                    error = "Those PINs didn't match."
                )
            }
        }
    }
}

@Composable
fun PinSetupRoute(
    onDone: () -> Unit,
    onBack: (() -> Unit)?,
    viewModel: PinSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.completed) {
        if (state.completed) onDone()
    }

    PinSetupScreen(
        state = state,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        onBack = onBack
    )
}

@Composable
fun PinSetupScreen(
    state: PinSetupUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBack: (() -> Unit)?
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (onBack != null) {
                GuestVaultTopBar(title = "Owner lock", onBack = onBack)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(HeroGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(52.dp)
                )
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            StatusPill(text = state.pill)
            Spacer(Modifier.height(16.dp))

            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))
            PinDots(length = PIN_LENGTH, filled = state.filled, error = state.error != null)

            Spacer(Modifier.height(12.dp))
            Text(
                text = state.error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.heightIn(min = 20.dp)
            )

            Spacer(Modifier.height(12.dp))
            PinKeypad(
                onDigit = onDigit,
                onBackspace = onBackspace,
                enabled = !state.verifying
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = "GuestVault never stores your PIN in plain text.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
