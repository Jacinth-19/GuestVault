package com.guestvault.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class AppStartState(
    val onboardingCompleted: Boolean,
    val pinSet: Boolean
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val observeSettings: ObserveSettingsUseCase,
    private val pinRepository: PinRepository
) : ViewModel() {

    private val settings = observeSettings()
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

    /** Decides where the splash screen should send the user. */
    suspend fun startState(): AppStartState {
        val current = settings.first()
        return AppStartState(
            onboardingCompleted = current?.onboardingCompleted == true,
            pinSet = pinRepository.isPinSet()
        )
    }
}
