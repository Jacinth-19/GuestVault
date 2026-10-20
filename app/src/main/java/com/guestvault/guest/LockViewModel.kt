package com.guestvault.guest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guestvault.domain.model.EndReason
import com.guestvault.domain.usecase.EndGuestSessionUseCase
import com.guestvault.domain.usecase.ObserveSettingsUseCase
import com.guestvault.domain.usecase.VerifyPinUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PIN_LENGTH = 4
private const val MAX_ATTEMPTS = 5
private const val LOCKOUT_SECONDS = 30

data class LockUiState(
    val pinLength: Int = 0,
    val error: String? = null,
    val verifying: Boolean = false,
    val attemptsLeft: Int = MAX_ATTEMPTS,
    val lockedOutSeconds: Int = 0,
    val wipeMode: Boolean = false,
    val autoWipe: Boolean = true,
    val biometricEnabled: Boolean = true,
    val unlocked: Boolean = false
) {
    val lockedOut: Boolean get() = lockedOutSeconds > 0
}

@HiltViewModel
class LockViewModel @Inject constructor(
    private val verifyPin: VerifyPinUseCase,
    private val endGuestSession: EndGuestSessionUseCase,
    observeSettings: ObserveSettingsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    private var entered = ""
    private var failedAttempts = 0
    private var lockoutUntil = 0L
    private var lockoutJob: Job? = null
    private var ending = false

    init {
        viewModelScope.launch {
            observeSettings().collect { settings ->
                _state.update {
                    it.copy(
                        autoWipe = settings.autoWipe,
                        biometricEnabled = settings.biometricEnabled
                    )
                }
            }
        }
    }

    fun reset() {
        entered = ""
        _state.update {
            LockUiState(
                autoWipe = it.autoWipe,
                biometricEnabled = it.biometricEnabled
            )
        }
    }

    fun onDigit(digit: Char) {
        val current = _state.value
        if (current.verifying || current.lockedOut || current.unlocked) return
        if (entered.length >= PIN_LENGTH) return

        entered += digit
        _state.update { it.copy(pinLength = entered.length, error = null) }
        if (entered.length == PIN_LENGTH) submit()
    }

    fun onBackspace() {
        if (_state.value.verifying) return
        if (entered.isNotEmpty()) entered = entered.dropLast(1)
        _state.update { it.copy(pinLength = entered.length) }
    }

    fun toggleWipeMode() = _state.update { it.copy(wipeMode = !it.wipeMode) }

    fun onBiometricSucceeded() {
        viewModelScope.launch { finishSession() }
    }

    fun onBiometricError(message: String?) {
        if (message != null) _state.update { it.copy(error = message) }
    }

    private fun submit() {
        if (_state.value.verifying || ending) return
        if (System.currentTimeMillis() < lockoutUntil) return

        viewModelScope.launch {
            _state.update { it.copy(verifying = true, error = null) }
            val ok = verifyPin(entered)

            if (ok) {
                _state.update { it.copy(pinLength = PIN_LENGTH, verifying = false) }
                finishSession()
            } else {
                entered = ""
                failedAttempts++
                val remaining = (MAX_ATTEMPTS - failedAttempts).coerceAtLeast(0)
                if (remaining == 0) {
                    lockoutUntil = System.currentTimeMillis() + LOCKOUT_SECONDS * 1_000L
                    _state.update {
                        it.copy(pinLength = 0, verifying = false, attemptsLeft = 0)
                    }
                    startLockoutCountdown()
                } else {
                    _state.update {
                        it.copy(
                            pinLength = 0,
                            verifying = false,
                            attemptsLeft = remaining,
                            error = if (remaining == 1) {
                                "Incorrect PIN. 1 attempt left."
                            } else {
                                "Incorrect PIN. $remaining attempts left."
                            }
                        )
                    }
                }
            }
        }
    }

    private fun startLockoutCountdown() {
        lockoutJob?.cancel()
        lockoutJob = viewModelScope.launch {
            while (true) {
                val secondsLeft = ((lockoutUntil - System.currentTimeMillis()) / 1_000L)
                    .coerceAtLeast(0L)
                    .toInt()
                _state.update {
                    it.copy(
                        lockedOutSeconds = secondsLeft,
                        error = if (secondsLeft > 0) {
                            "Too many attempts. Try again in ${secondsLeft}s."
                        } else {
                            null
                        }
                    )
                }
                if (secondsLeft <= 0) break
                delay(1_000)
            }
            failedAttempts = 0
            _state.update { it.copy(lockedOutSeconds = 0, attemptsLeft = MAX_ATTEMPTS) }
        }
    }

    private suspend fun finishSession() {
        if (ending) return
        ending = true
        val reason = if (_state.value.wipeMode) EndReason.DATA_WIPED else EndReason.OWNER_UNLOCK
        _state.update { it.copy(verifying = true) }
        endGuestSession(reason)
        _state.update { it.copy(verifying = false, unlocked = true) }
    }

    override fun onCleared() {
        lockoutJob?.cancel()
        super.onCleared()
    }
}
