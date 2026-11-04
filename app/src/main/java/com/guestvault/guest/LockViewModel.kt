package com.guestvault.guest

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guestvault.core.GuestNotifications
import com.guestvault.core.Premium
import com.guestvault.data.intruder.IntruderCapture
import com.guestvault.domain.model.EndReason
import com.guestvault.domain.model.UnlockGuard
import com.guestvault.domain.policy.IntruderPolicy
import com.guestvault.domain.policy.RecoveryCode
import com.guestvault.domain.repository.RecoveryCodeRepository
import com.guestvault.domain.repository.SettingsRepository
import com.guestvault.domain.usecase.EndGuestSessionUseCase
import com.guestvault.domain.usecase.ObserveSettingsUseCase
import com.guestvault.domain.usecase.VerifyPinUseCase
import com.guestvault.domain.usecase.VerifyRecoveryCodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LOCK_TAG = "GuestVaultLock"

/** Four digits is the default; six is a Pro option the owner may switch on. */
private const val DEFAULT_PIN_LENGTH = 4

data class LockUiState(
    /** How many dots to draw. Comes from the owner's setting, never guessed. */
    val pinLength: Int = DEFAULT_PIN_LENGTH,
    val entered: Int = 0,
    val error: String? = null,
    val verifying: Boolean = false,
    val attemptsLeft: Int = LockoutPolicy.MAX_ATTEMPTS,
    val lockedOutSeconds: Int = 0,
    val guardLoaded: Boolean = false,
    val wipeMode: Boolean = false,
    val autoWipe: Boolean = true,
    val biometricEnabled: Boolean = true,
    val unlocked: Boolean = false,
    /** True once the emergency code was accepted; the screen then resets the PIN. */
    val recovered: Boolean = false,
    /** A recovery code exists, so offering the link is honest. */
    val recoveryAvailable: Boolean = false,
    val recoveryOpen: Boolean = false,
    val recoveryError: String? = null,
    val recoveryChecking: Boolean = false
) {
    val lockedOut: Boolean get() = lockedOutSeconds > 0

    /** The face of the lock after [LockoutPolicy.MAX_ATTEMPTS] failures. */
    val lastAttempt: Boolean get() = attemptsLeft <= 1
}

/**
 * Owner-lock state.
 *
 * The brute-force counter lives in DataStore (through [SettingsRepository]), not
 * in this ViewModel, so rotating the device, dismissing the activity from
 * Recents or letting the process be killed cannot reset the cool-down. The
 * policy itself is [LockoutPolicy], which has its own JVM unit tests.
 *
 * Three things are decided here and nowhere else:
 *
 * 1. **How many digits to accept.** The owner may choose six; the number comes
 *    from settings, so the dots and the auto-submit always agree.
 * 2. **When to take a photo.** A wrong PIN and an opted-in camera is the only
 *    trigger, and a failure to take one never disturbs the lock.
 * 3. **What the emergency code does.** It is a *second* credential with the
 *    same cool-down, and using it ends the session and hands the owner a screen
 *    to set a new PIN — the old one is not revealed, because it is only stored
 *    as a hash.
 */
@HiltViewModel
class LockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val verifyPin: VerifyPinUseCase,
    private val verifyRecoveryCode: VerifyRecoveryCodeUseCase,
    private val endGuestSession: EndGuestSessionUseCase,
    private val settingsRepository: SettingsRepository,
    private val recoveryCodeRepository: RecoveryCodeRepository,
    private val intruderCapture: IntruderCapture,
    observeSettings: ObserveSettingsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    private var entered = ""
    // Digits tapped before the stored guard has loaded. The guard lives in
    // DataStore, so a cold start takes a moment; dropping taps in that window
    // made the keypad look dead and ate the first digits of a fast entry.
    private val pending = StringBuilder()
    private var guard = UnlockGuard()
    private var lockoutJob: Job? = null
    private var ending = false

    /** Read once, so a refusal mid-session cannot change what is accepted. */
    private var intruderEnabled = false

    init {
        viewModelScope.launch {
            observeSettings().collect { settings ->
                val length = settings.pinLength
                _state.update {
                    it.copy(
                        autoWipe = settings.autoWipe,
                        biometricEnabled = settings.biometricEnabled,
                        pinLength = length
                    )
                }
                // Digits typed against the wrong length must not stand.
                if (entered.length > length) {
                    entered = entered.take(length)
                    _state.update { it.copy(entered = entered.length) }
                }
            }
        }
        viewModelScope.launch {
            // The length is part of the lock, so the keypad is not "ready"
            // until both the guard and the setting are known.
            val length = runCatching { settingsRepository.settings.first().pinLength }
                .getOrDefault(DEFAULT_PIN_LENGTH)
            intruderEnabled = runCatching {
                settingsRepository.settings.first().intruderPhotosEnabled
            }.getOrDefault(false)

            guard = settingsRepository.readUnlockGuard()
            val now = System.currentTimeMillis()
            val remaining = LockoutPolicy.remainingLockSeconds(guard, now)
            _state.update {
                it.copy(
                    guardLoaded = true,
                    pinLength = length,
                    attemptsLeft = LockoutPolicy.attemptsLeft(guard, now),
                    lockedOutSeconds = remaining,
                    // Only offer the link when a code actually exists.
                    recoveryAvailable = Premium.isPro && recoveryCodeRepository.hasCode()
                )
            }
            if (remaining > 0) {
                // Cool-down wins: input typed before it was known is discarded,
                // never counted as a fresh attempt.
                pending.clear()
                _state.update { it.copy(entered = entered.length) }
                startLockoutCountdown()
            } else {
                flushPending()
            }
        }
    }

    fun reset() {
        entered = ""
        pending.clear()
        _state.update { it.copy(entered = 0, error = null, unlocked = false) }
    }

    fun onDigit(digit: Char) {
        val current = _state.value
        if (current.verifying || current.lockedOut || current.unlocked) return
        if (!current.guardLoaded) {
            if (entered.length + pending.length >= current.pinLength) return
            pending.append(digit)
            _state.update { it.copy(entered = entered.length + pending.length, error = null) }
            return
        }
        acceptDigit(digit)
    }

    /** The real entry path, once the guard is known. */
    private fun acceptDigit(digit: Char) {
        val current = _state.value
        if (current.verifying || current.lockedOut || current.unlocked) return
        if (entered.length >= current.pinLength) return

        entered += digit
        _state.update { it.copy(entered = entered.length, error = null) }
        if (entered.length == current.pinLength) submit()
    }

    private fun flushPending() {
        if (pending.isEmpty()) return
        val queued = pending.toString()
        pending.clear()
        _state.update { it.copy(entered = entered.length) }
        queued.forEach { acceptDigit(it) }
    }

    fun onBackspace() {
        if (_state.value.verifying) return
        when {
            pending.isNotEmpty() -> pending.deleteAt(pending.length - 1)
            entered.isNotEmpty() -> entered = entered.dropLast(1)
        }
        _state.update { it.copy(entered = entered.length + pending.length) }
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

    // ------------------------------------------------------------- recovery

    fun openRecovery() {
        if (!_state.value.recoveryAvailable || _state.value.lockedOut) return
        _state.update { it.copy(recoveryOpen = true, recoveryError = null) }
    }

    fun closeRecovery() = _state.update { it.copy(recoveryOpen = false, recoveryError = null) }

    /**
     * Checks the emergency code.
     *
     * A wrong code counts as a failed attempt against the same guard, so the
     * cool-down protects this path exactly as it protects the PIN. That matters:
     * a recovery route with no rate limit would be the weakest point on the
     * whole lock screen.
     */
    fun submitRecovery(code: String) {
        val current = _state.value
        if (current.recoveryChecking || current.lockedOut) return

        if (!RecoveryCode.isValid(code)) {
            _state.update {
                it.copy(recoveryError = "That code has ${RecoveryCode.LENGTH} symbols.")
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(recoveryChecking = true, recoveryError = null) }
            val ok = verifyRecoveryCode(code)
            val now = System.currentTimeMillis()

            if (ok) {
                guard = LockoutPolicy.reset()
                settingsRepository.writeUnlockGuard(guard)
                GuestNotifications.clearSecurity(context)
                _state.update {
                    it.copy(recoveryChecking = false, recoveryOpen = false, recovered = true)
                }
                finishSession()
            } else {
                guard = LockoutPolicy.registerFailure(guard, now)
                settingsRepository.writeUnlockGuard(guard)
                if (LockoutPolicy.isLockedOut(guard, now)) {
                    _state.update {
                        it.copy(
                            recoveryChecking = false,
                            recoveryOpen = false,
                            entered = 0,
                            attemptsLeft = 0
                        )
                    }
                    entered = ""
                    startLockoutCountdown()
                } else {
                    val left = LockoutPolicy.attemptsLeft(guard, now)
                    _state.update {
                        it.copy(
                            recoveryChecking = false,
                            attemptsLeft = left,
                            recoveryError = "That code doesn't match. $left " +
                                if (left == 1) "attempt left." else "attempts left."
                        )
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------- the PIN

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
                    it.copy(
                        entered = _state.value.pinLength,
                        verifying = false,
                        attemptsLeft = LockoutPolicy.MAX_ATTEMPTS
                    )
                }
                finishSession()
            } else {
                entered = ""
                val failureTime = System.currentTimeMillis()
                guard = LockoutPolicy.registerFailure(guard, failureTime)
                settingsRepository.writeUnlockGuard(guard)
                captureIntruder()

                if (LockoutPolicy.isLockedOut(guard, failureTime)) {
                    // The length of the round is useful in a bug report and in
                    // the field: the UI only ever shows the seconds remaining.
                    Log.i(
                        LOCK_TAG,
                        "Cool-down started: ${LockoutPolicy.remainingLockSeconds(guard, failureTime)}s " +
                            "after ${guard.failedAttempts} failed attempts"
                    )
                    _state.update { it.copy(entered = 0, verifying = false, attemptsLeft = 0) }
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
                            entered = 0,
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

    /**
     * One photo per failed attempt, off the main thread.
     *
     * Every reason this might not happen is already handled inside
     * [IntruderCapture]; here it only has to be true that the owner asked for it
     * and that this is a Pro build.
     */
    private fun captureIntruder() {
        if (!Premium.isPro || !intruderEnabled) return
        if (!IntruderPolicy.shouldCapture(
                enabled = intruderEnabled,
                cameraAvailable = intruderCapture.cameraAvailable(),
                permissionGranted = intruderCapture.permissionGranted(),
                failedAttempts = guard.failedAttempts
            )
        ) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val file = intruderCapture.capture()
            if (file != null) Log.i(LOCK_TAG, "Intruder photo stored as ${file.name}")
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
        withContext(Dispatchers.Default) { endGuestSession(reason) }
        _state.update { it.copy(verifying = false, unlocked = true) }
    }

    override fun onCleared() {
        lockoutJob?.cancel()
        super.onCleared()
    }
}
