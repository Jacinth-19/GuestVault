package com.guestvault.domain.model

/**
 * An app that is visible in the device's launcher and can therefore be offered
 * to a guest.
 *
 * Note: this is a *domain* model, so it intentionally carries no Android types
 * (icons are resolved by the UI layer from [packageName]).
 */
data class GuestApp(
    val packageName: String,
    val label: String,
    val category: AppCategory,
    val isRecommended: Boolean = false,
    /**
     * Sensitive apps (Settings, Play Store, Files, ...) can still be explicitly
     * allowed by the owner, but they are always flagged in the UI.
     */
    val isSensitive: Boolean = false
)

enum class AppCategory(val displayName: String) {
    ALL("All apps"),
    RECOMMENDED("Recommended"),
    COMMUNICATION("Calls & chat"),
    MEDIA("Photos & media"),
    TOOLS("Tools"),
    OTHER("Other")
}
