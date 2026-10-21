package com.guestvault.guest

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guestvault.core.GuestNotifications
import com.guestvault.domain.model.EndReason
import com.guestvault.domain.model.UnlockGuard
import com.guestvault.domain.repository.SettingsRepository
import com.guestvault.domain.usecase.EndGuestSessionUseCase
import com.guestvault.domain.usecase.ObserveSettingsUseCase
import com.guestvault.domain.usecase.VerifyPinUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PIN_LENGTH = 4
private const val LOCK_TAG = "GuestVaultLock"

data class LockUiState(
    val pinLength: Int = 0,
    val error: String? = null,
    val verifying: Boolean = false,
    val attemptsLeft: Int = LockoutPolicy.MAX_ATTEMPTS,
    val lockedOutSeconds: Int = 0,
    val guardLoaded: Boolean = false,
    val wipeMode: Boolean = false,
    val autoWipe: Boolean = true,
    val biometricEnabled: Boolean = true,
    val unlocked: Boolean = false
) {
    val lockedOut: Boolean get() = lockedOutSeconds > 0
}

/**
 * Owner-lock state.
 *
 * The brute-force counter lives in DataStore (through [SettingsRepository]), not
 * in this ViewModel, so rotating the device, dismissing the activity from
 * Recents or letting the process be killed cannot reset the cool-down. The
 * policy itself is [LockoutPolicy], which has its own JVM unit tests.
 */
@HiltViewModel
class LockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val verifyPin: VerifyPinUseCase,
    private val endGuestSession: EndGuestSessionUseCase,
    private val settingsRepository: SettingsRepository,
    observeSettings: ObserveSettingsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    private var entered = ""
    private var guard = UnlockGuard()
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
        viewModelScope.launch {
            guard = settingsRepository.readUnlockGuard()
            val now = System.currentTimeMillis()
            val remaining = LockoutPolicy.remainingLockSeconds(guard, now)
            _state.update {
                it.copy(
                    guardLoaded = true,
                    attemptsLeft = LockoutPolicy.attemptsLeft(guard, now),
                    lockedOutSeconds = remaining
                )
            }
            if (remaining > 0) startLockoutCountdown()
        }
    }

    fun reset() {
        entered = ""
        _state.update { it.copy(pinLength = 0, error = null, unlocked = false) }
    }

    fun onDigit(digit: Char) {
        val current = _state.value
        if (!current.guardLoaded || current.verifying || current.lockedOut || current.unlocked) return
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
        viewModelScope.launch {
            settingsRepository.writeUnlockGuard(LockoutPolicy.reset())
            guard = LockoutPolicy.reset()
            GuestNotifications.clearSecurity(context)
            finishSession()
        }
    }

    fun onBiometricError(message: String?) {
        if (message != null) _state.update { it.copy(error = message) }
    }

    private fun submit() {
        if (_state.value.verifying || ending) return
        val now = System.currentTimeMillis()
        if (LockoutPolicy.isLockedOut(guard, now)) return

        val attempt = entered
        viewModelScope.launch {
            _state.update { it.copy(verifying = true, error = null) }
            val ok = verifyPin(attempt)

            if (ok) {
                guard = LockoutPolicy.reset()
                settingsRepository.writeUnlockGuard(guard)
                GuestNotifications.clearSecurity(context)
                _state.update {
                    it.copy(pinLength = PIN_LENGTH, verifying = false, attemptsLeft = LockoutPolicy.MAX_ATTEMPTS)
                }
                finishSession()
            } else {
                entered = ""
                val failureTime = System.currentTimeMillis()
                guard = LockoutPolicy.registerFailure(guard, failureTime)
                settingsRepository.writeUnlockGuard(guard)

                if (LockoutPolicy.isLockedOut(guard, failureTime)) {
                    // The length of the round is useful in a bug report and in
                    // the field: the UI only ever shows the seconds remaining.
                    Log.i(
                        LOCK_TAG,
                        "Cool-down started: ${LockoutPolicy.remainingLockSeconds(guard, failureTime)}s " +
                            "after ${guard.failedAttempts} failed attempts"
                    )
                    _state.update { it.copy(pinLength = 0, verifying = false, attemptsLeft = 0) }
                    // Meaningful signal, not noise: one heads-up per cool-down
                    // round, never one per wrong digit.
                    GuestNotifications.notifyLockout(
                        context = context,
                        failedAttempts = guard.failedAttempts,
                        lockSeconds = LockoutPolicy.remainingLockSeconds(guard, failureTime)
                    )
                    startLockoutCountdown()
                } else {
                    val left = LockoutPolicy.attemptsLeft(guard, failureTime)
                    _state.update {
                        it.copy(
                            pinLength = 0,
                            verifying = false,
                            attemptsLeft = left,
                            error = if (left == 1) {
                                "Incorrect PIN. 1 attempt left."
                            } else {
                                "Incorrect PIN. $left attempts left."
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
                val now = System.currentTimeMillis()
                val secondsLeft = LockoutPolicy.remainingLockSeconds(guard, now)
                _state.update {
                    it.copy(
                        lockedOutSeconds = secondsLeft,
                        attemptsLeft = LockoutPolicy.attemptsLeft(guard, now),
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
            _state.update { it.copy(lockedOutSeconds = 0) }
            GuestNotifications.notifyLockoutCleared(
                context = context,
                attemptsLeft = LockoutPolicy.attemptsLeft(guard, System.currentTimeMillis())
            )
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
