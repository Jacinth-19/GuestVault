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
    /**
     * Per-app budgets for the guest session, in minutes. A package that is
     * absent has no limit; 0 is treated the same way, matching every other
     * duration in the app.
     */
    val appLimits: Map<String, Int> = emptyMap(),
    /** When guest mode may run at all. Disabled by default. */
    val schedule: GuestSchedule = GuestSchedule(),
    /**
     * Total guest minutes allowed per calendar day, across every session.
     * 0 means no daily budget, so an unset value changes nothing.
     */
    val dailyBudgetMinutes: Int = 0,
    /**
     * How many digits the owner PIN has: 4 or 6. Anything else is read as 4,
     * so a corrupt value can never make the lock accept a shorter PIN.
     */
    val pinDigits: Int = 4,
    /** Take a photo after a failed unlock. Off by default; Pro only. */
    val intruderPhotosEnabled: Boolean = false,
    val activeSession: GuestSession? = null,
    val history: List<SessionRecord> = emptyList()
) {
    val hasActiveSession: Boolean get() = activeSession != null

    /** The PIN length the lock actually uses; 4 unless the owner chose 6. */
    val pinLength: Int get() = if (pinDigits == 6) 6 else 4

    /** Human readable lock method shown on the dashboard. */
    val lockMethodLabel: String get() = if (biometricEnabled) "PIN + biometrics" else "PIN only"
}
