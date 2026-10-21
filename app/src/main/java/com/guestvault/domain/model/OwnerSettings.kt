package com.guestvault.domain.model

/** Everything the owner has configured. Emitted by the settings repository. */
data class OwnerSettings(
    val onboardingCompleted: Boolean = false,
    val allowedPackages: Set<String> = emptySet(),
    val autoWipe: Boolean = true,
    val biometricEnabled: Boolean = true,
    /**
     * Screen pinning asks the system to lock the guest into GuestVault.
     * It always triggers a one-time system confirmation dialog, so it can be
     * switched off for devices where that is not desired.
     */
    val lockTaskEnabled: Boolean = true,
    val defaultSessionMinutes: Int = 0,
    /**
     * Reminders a few minutes before a timed session ends. Security alerts are
     * always on: they exist to tell the owner something is wrong, so allowing
     * them to be switched off would defeat the point.
     */
    val notifyTimerAlerts: Boolean = true,
    /**
     * A short recap once a session has ended. Nobody's safety depends on it, so
     * it is the one notification the owner has to opt into.
     */
    val notifySessionSummary: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val activeSession: GuestSession? = null,
    val history: List<SessionRecord> = emptyList()
) {
    val hasActiveSession: Boolean get() = activeSession != null

    /** Human readable lock method shown on the dashboard. */
    val lockMethodLabel: String get() = if (biometricEnabled) "PIN + biometrics" else "PIN only"
}
