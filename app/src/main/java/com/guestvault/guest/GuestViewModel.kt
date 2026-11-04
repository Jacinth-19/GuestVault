package com.guestvault.guest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guestvault.core.AppLauncher
import com.guestvault.data.session.AppUsageTracker
import com.guestvault.domain.model.AppCategory
import com.guestvault.domain.model.EndReason
import com.guestvault.domain.model.GuestApp
import com.guestvault.domain.model.GuestSession
import com.guestvault.domain.model.OwnerSettings
import com.guestvault.domain.policy.AppLimitPolicy
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
    val loaded: Boolean = false,
    /** Minutes allowed per app; absent means no limit. */
    val limits: Map<String, Int> = emptyMap(),
    /** Millis already spent per app in this session. */
    val usage: Map<String, Long> = emptyMap(),
    /** Apps that have used up their budget this session. */
    val exhausted: Set<String> = emptySet()
) {
    fun limitFor(packageName: String): Int = limits[packageName] ?: 0

    fun captionFor(packageName: String): String? =
        AppLimitPolicy.caption(limitFor(packageName), usage[packageName] ?: 0L)

    fun isExhausted(packageName: String): Boolean = packageName in exhausted
}

@HiltViewModel
class GuestViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val getInstalledApps: GetInstalledAppsUseCase,
    private val appLauncher: AppLauncher,
    private val endGuestSession: EndGuestSessionUseCase,
    private val usageTracker: AppUsageTracker
) : ViewModel() {

    private val catalog = MutableStateFlow<List<GuestApp>>(emptyList())
    private val catalogLoaded = MutableStateFlow(false)

    // Null until DataStore has emitted once. This prevents the guest screen
    // from briefly believing "no session" (and bouncing to owner mode).
    private val settingsState: StateFlow<OwnerSettings?> = observeSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    private val usage = usageTracker.usage
    private val exhausted = usageTracker.exhausted

    val state: StateFlow<GuestUiState> = combine(
        combine(settingsState, catalog, catalogLoaded) { settings, apps, loaded ->
            Triple(settings, apps, loaded)
        },
        usage,
        exhausted
    ) { (settings, apps, loaded), spent, usedUp ->
        val session = settings?.activeSession
        val allowed = session?.packages.orEmpty().map { packageName ->
            apps.firstOrNull { it.packageName == packageName }
                ?: GuestApp(packageName, packageName, AppCategory.OTHER)
        }.sortedBy { it.label.lowercase() }

        GuestUiState(
            session = session,
            apps = allowed,
            settings = settings ?: OwnerSettings(),
            loaded = loaded && settings != null,
            limits = settings?.appLimits.orEmpty(),
            usage = spent,
            exhausted = usedUp
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
        if (packageName in usageTracker.exhausted.value) {
            _messages.tryEmit("That app has used up its time for this session.")
            return
        }
        // Start the budget clock before the app appears, so the very first
        // second is counted rather than lost to the launch animation.
        usageTracker.launch(packageName)
        appLauncher.launch(packageName).onFailure {
            usageTracker.pause()
            _messages.tryEmit("Could not open that app.")
        }
    }

    fun endSession(reason: EndReason = EndReason.OWNER_UNLOCK) {
        viewModelScope.launch { endGuestSession(reason) }
    }
}
