package com.guestvault.domain.usecase

import com.guestvault.data.session.SessionController
import com.guestvault.domain.model.EndReason
import com.guestvault.domain.model.GuestApp
import com.guestvault.domain.model.GuestSession
import com.guestvault.domain.model.OwnerSettings
import com.guestvault.domain.model.WipeResult
import com.guestvault.domain.repository.AppCatalogRepository
import com.guestvault.domain.repository.PinRepository
import com.guestvault.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Streams the owner's configuration. */
class ObserveSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    operator fun invoke(): Flow<OwnerSettings> = repository.settings
}

/** Reads the device's launchable apps (IO thread). */
class GetInstalledAppsUseCase @Inject constructor(
    private val repository: AppCatalogRepository
) {
    suspend operator fun invoke(): List<GuestApp> = repository.getLaunchableApps()
}

/**
 * Seeds a first-run allow-list: recommended apps that are actually installed,
 * falling back to the first few non-sensitive apps on stripped-down devices.
 */
class ApplyRecommendedDefaultsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appCatalogRepository: AppCatalogRepository
) {
    suspend operator fun invoke(force: Boolean = false): Set<String> {
        val current = settingsRepository.settings.first()
        if (!force && current.allowedPackages.isNotEmpty()) return current.allowedPackages

        val installed = appCatalogRepository.getLaunchableApps()
        val recommended = installed
            .filter { it.isRecommended && !it.isSensitive }
            .map { it.packageName }
            .toSet()

        val selected = recommended.ifEmpty {
            installed.filterNot { it.isSensitive }.take(6).map { it.packageName }.toSet()
        }
        settingsRepository.setAllowedPackages(selected)
        return selected
    }
}

class UpdateAllowedAppsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(packages: Set<String>) = repository.setAllowedPackages(packages)
}

class StartGuestSessionUseCase @Inject constructor(
    private val sessionController: SessionController
) {
    suspend operator fun invoke(
        packages: Set<String>,
        durationMinutes: Int,
        autoWipe: Boolean,
        lockTaskEnabled: Boolean
    ): Result<GuestSession> = runCatching {
        sessionController.start(packages, durationMinutes, autoWipe, lockTaskEnabled)
    }
}

class EndGuestSessionUseCase @Inject constructor(
    private val sessionController: SessionController
) {
    suspend operator fun invoke(reason: EndReason): WipeResult? =
        sessionController.end(reason)
}

/** PIN verification is intentionally off the main thread (PBKDF2 is slow). */
class VerifyPinUseCase @Inject constructor(
    private val pinRepository: PinRepository
) {
    suspend operator fun invoke(pin: String): Boolean = withContext(Dispatchers.Default) {
        pinRepository.verifyPin(pin)
    }
}
