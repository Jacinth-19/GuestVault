package com.guestvault.ui.screens.pin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
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
import com.guestvault.core.Premium
import com.guestvault.domain.model.ThemeMode
import com.guestvault.domain.repository.PinRepository
import com.guestvault.domain.repository.SettingsRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Why the PIN screen is open.
 *
 * [RESET] exists for one situation: the owner came back with the emergency
 * code, so the current PIN is unknown by definition. It therefore skips the
 * "enter your current PIN" stage that [CHANGE] starts with — asking for a PIN
 * nobody knows would be a dead end.
 */
enum class PinSetupMode(val key: String) {
    SETUP("setup"),
    CHANGE("change"),
    RESET("reset");

    companion object {
        fun fromKey(key: String?): PinSetupMode =
            entries.firstOrNull { it.key == key } ?: SETUP
    }
}

enum class PinStage { CURRENT, NEW, CONFIRM }

data class PinSetupUiState(
    val mode: PinSetupMode = PinSetupMode.SETUP,
    /** 4 or 6; the owner chooses before typing the new PIN. */
    val pinLength: Int = 4,
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
    private val settingsRepository: SettingsRepository,
    private val verifyPin: VerifyPinUseCase
) : ViewModel() {

    private val mode = PinSetupMode.fromKey(savedStateHandle.get<String>("mode"))

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<PinSetupUiState> = _state.asStateFlow()

    private var entered = ""
    private var firstEntry = ""

    private fun initialState(): PinSetupUiState = if (mode == PinSetupMode.CHANGE) {
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

    init {
        // The current length is the owner's choice, and it decides how many
        // dots the screen draws before the first digit is typed.
        viewModelScope.launch {
            val saved = runCatching { settingsRepository.settings.first().pinLength }
                .getOrDefault(4)
            if (_state.value.stage != PinStage.CURRENT) {
                _state.update { it.copy(pinLength = saved, subtitle = subtitleFor(saved)) }
            }
        }
    }

    /**
     * Switches between four and six digits.
     *
     * Only offered while choosing a *new* PIN: switching length halfway through
     * typing would silently change what counts as a complete entry. Any digits
     * already typed are dropped, because they were entered against the old
     * length.
     */
    fun setPinLength(length: Int) {
        val current = _state.value
        if (current.stage == PinStage.CURRENT) return
        if (current.pinLength == length) return
        if (length != 4 && length != 6) return
        entered = ""
        _state.update {
            it.copy(pinLength = length, filled = 0, error = null, subtitle = subtitleFor(length))
        }
    }

    private fun subtitleFor(length: Int): String =
        if (length == 6) {
            "You'll use these six digits to return from Guest Mode."
        } else {
            "You'll use these four digits to return from Guest Mode."
        }

    private fun lengthWord(length: Int): String = if (length == 6) "six" else "four"

    fun onDigit(digit: Char) {
        val current = _state.value
        if (current.verifying || current.completed) return
        // One length for the whole flow. The CURRENT stage uses the length the
        // stored PIN already has, which is the same value until a new one is
        // chosen — and a new one can only be chosen after CURRENT is passed.
        val length = current.pinLength
        if (entered.length >= length) return

        entered += digit
        _state.update { it.copy(filled = entered.length, error = null) }
        if (entered.length == length) handleComplete(entered)
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
                val length = _state.value.pinLength
                _state.update {
                    it.copy(
                        stage = PinStage.NEW,
                        title = "Choose a new PIN",
                        subtitle = "Pick ${lengthWord(length)} digits you will remember.",
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
        val length = _state.value.pinLength
        _state.update {
            it.copy(
                stage = PinStage.CONFIRM,
                title = "Confirm your PIN",
                subtitle = "Enter the same ${lengthWord(length)} digits one more time.",
                pill = "Step 2 of 2",
                filled = 0,
                error = null
            )
        }
    }

    private fun confirmPin(pin: String) {
        if (pin == firstEntry) {
            pinRepository.setPin(pin)
            // The lock screen reads this to know how many dots to draw, so it
            // is written in the same breath as the hash.
            viewModelScope.launch { settingsRepository.setPinDigits(pin.length) }
            val length = _state.value.pinLength
            _state.update { it.copy(completed = true, filled = length, error = null) }
        } else {
            // Stay on the confirm step: losing the first entry would make a
            // single mistyped digit restart the whole flow.
            entered = ""
            val length = _state.value.pinLength
            _state.update {
                it.copy(
                    stage = PinStage.CONFIRM,
                    title = "Confirm your PIN",
                    subtitle = "Enter the same ${lengthWord(length)} digits one more time.",
                    pill = "Step 2 of 2",
                    filled = 0,
                    error = "That PIN doesn't match. Try again."
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
        onLengthChange = viewModel::setPinLength,
        onBack = onBack
    )
}

@Composable
fun PinSetupScreen(
    state: PinSetupUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onLengthChange: (Int) -> Unit,
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
            PinDots(length = state.pinLength, filled = state.filled, error = state.error != null)

            // Paid build only, and only while choosing a new PIN: changing the
            // length mid-entry would change what counts as "complete". The free
            // build renders nothing here at all, so its PIN screen is exactly
            // what it was before six-digit PINs existed.
            if (Premium.isPro && state.stage != PinStage.CURRENT) {
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(4, 6).forEach { length ->
                        FilterChip(
                            selected = state.pinLength == length,
                            onClick = { onLengthChange(length) },
                            label = { Text("$length digits") },
                            enabled = !state.verifying
                        )
                    }
                }
            }

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
