package com.guestvault.ui

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guestvault.data.session.SessionController
import com.guestvault.domain.model.EndReason
import com.guestvault.domain.model.OwnerSettings
import com.guestvault.domain.model.ThemeMode
import com.guestvault.domain.repository.PinRepository
import com.guestvault.domain.usecase.ObserveSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppStartState(
    val onboardingCompleted: Boolean,
    val pinSet: Boolean
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val observeSettings: ObserveSettingsUseCase,
    private val pinRepository: PinRepository,
    private val sessionController: SessionController
) : ViewModel() {

    private val TAG = "AppViewModel"

    /** The raw flow: `first()` on the StateFlow below would return its null seed. */
    private val settingsFlow = observeSettings()

    private val settings = settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val themeMode: StateFlow<ThemeMode> = settings
        .map { it?.themeMode ?: ThemeMode.SYSTEM }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    /**
     * True while a guest session is running.
     *
     * The owner UI is never shown while this is true: if anyone launches
     * GuestVault from the launcher during a session, MainActivity immediately
     * hands over to the restricted guest home. Ending a session always requires
     * the owner PIN / biometrics first.
     */
    val sessionActive: StateFlow<Boolean> = settings
        .map { it?.activeSession != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // The BOOT_COMPLETED receiver can be delivered long after the device is
        // usable (and not at all while the keyguard is up). Recovering here too
        // guarantees a session that started before this boot is closed before
        // the UI trusts it.
        viewModelScope.launch {
            runCatching { settingsFlow.first() }.getOrNull()
                ?.let { recoverSessionFromBeforeBoot(it) }
        }
    }

    /**
     * Ends a guest session that began before this boot.
     *
     * The BOOT_COMPLETED receiver is the primary path, but it can be late (or
     * held until the keyguard is dismissed). Routing must not trust a session
     * that outlived a reboot, so this is checked on every cold start.
     */
    private suspend fun recoverSessionFromBeforeBoot(settings: OwnerSettings) {
        val session = settings.activeSession ?: return
        val bootedAt = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        if (session.startedAt < bootedAt) {
            Log.i(TAG, "Closing a guest session that started before this boot")
            sessionController.end(EndReason.RECOVERED)
        }
    }

    /** Decides where the splash screen should send the user. */
    suspend fun startState(): AppStartState {
        val current = settingsFlow.first()
        recoverSessionFromBeforeBoot(current)
        val pinSet = pinRepository.isPinSet()
        return AppStartState(
            // An owner PIN can only be created after onboarding. If storage
            // reports "not onboarded" while a PIN exists, the read failed rather
            // than the owner being new: sending them back to onboarding would
            // wipe the first-run flow and leave the guest session running.
            onboardingCompleted = current?.onboardingCompleted == true || pinSet,
            pinSet = pinSet
        )
    }
}
