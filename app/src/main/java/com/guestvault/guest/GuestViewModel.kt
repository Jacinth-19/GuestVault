package com.guestvault.guest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guestvault.core.AppLauncher
import com.guestvault.domain.model.AppCategory
import com.guestvault.domain.model.EndReason
import com.guestvault.domain.model.GuestApp
import com.guestvault.domain.model.GuestSession
import com.guestvault.domain.model.OwnerSettings
import com.guestvault.domain.usecase.EndGuestSessionUseCase
import com.guestvault.domain.usecase.GetInstalledAppsUseCase
import com.guestvault.domain.usecase.ObserveSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GuestUiState(
    val session: GuestSession? = null,
    val apps: List<GuestApp> = emptyList(),
    val settings: OwnerSettings = OwnerSettings(),
    val loaded: Boolean = false
)

@HiltViewModel
class GuestViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val getInstalledApps: GetInstalledAppsUseCase,
    private val appLauncher: AppLauncher,
    private val endGuestSession: EndGuestSessionUseCase
) : ViewModel() {

    private val catalog = MutableStateFlow<List<GuestApp>>(emptyList())
    private val catalogLoaded = MutableStateFlow(false)

    // Null until DataStore has emitted once. This prevents the guest screen
    // from briefly believing "no session" (and bouncing to owner mode).
    private val settingsState: StateFlow<OwnerSettings?> = observeSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    val state: StateFlow<GuestUiState> = combine(
        settingsState,
        catalog,
        catalogLoaded
    ) { settings, apps, loaded ->
        val session = settings?.activeSession
        val allowed = session?.packages.orEmpty().map { packageName ->
            apps.firstOrNull { it.packageName == packageName }
                ?: GuestApp(packageName, packageName, AppCategory.OTHER)
        }.sortedBy { it.label.lowercase() }

        GuestUiState(
            session = session,
            apps = allowed,
            settings = settings ?: OwnerSettings(),
            loaded = loaded && settings != null
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuestUiState())

    init {
        viewModelScope.launch {
            catalog.value = runCatching { getInstalledApps() }.getOrDefault(emptyList())
            catalogLoaded.value = true
        }
    }

    /** Only packages in the persisted allow-list can ever be launched. */
    fun launchApp(packageName: String) {
        val session = state.value.session ?: return
        if (packageName !in session.packages) {
            _messages.tryEmit("That app isn't part of this guest session.")
            return
        }
        appLauncher.launch(packageName).onFailure {
            _messages.tryEmit("Could not open that app.")
        }
    }

    fun endSession(reason: EndReason = EndReason.OWNER_UNLOCK) {
        viewModelScope.launch { endGuestSession(reason) }
    }
}
