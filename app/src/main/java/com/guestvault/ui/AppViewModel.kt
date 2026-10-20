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

    val themeMode: StateFlow<ThemeMode> = observeSettings()
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    /** Decides where the splash screen should send the user. */
    suspend fun startState(): AppStartState {
        val settings = observeSettings().first()
        return AppStartState(
            onboardingCompleted = settings.onboardingCompleted,
            pinSet = pinRepository.isPinSet()
        )
    }
}
