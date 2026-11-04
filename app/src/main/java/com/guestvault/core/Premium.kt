package com.guestvault.core

import com.guestvault.BuildConfig

/**
 * What the paid build adds.
 *
 * Kept as an enum rather than a set of booleans so a locked screen can name the
 * thing the owner would be buying, instead of showing a greyed-out control with
 * no explanation.
 */
enum class PremiumFeature(val title: String, val blurb: String) {
    PROFILES(
        "Guest profiles",
        "Save a preset for each kind of guest — kids, a colleague, a stranger — " +
            "and start the right one in a single tap."
    ),
    PER_APP_LIMITS(
        "Per-app time limits",
        "Cap a single app inside a session, so an hour of hand-over is not an " +
            "hour of video — and choose the hours guest mode may run at all."
    ),
    THEMES(
        "Themes & branding",
        "Choose the accent GuestVault uses, so a handed-over phone still looks " +
            "like your phone."
    ),
    EXPORT_IMPORT(
        "Export & import",
        "Move every profile and setting to a new phone from a file you own — " +
            "never through a server."
    )
}

/**
 * The single place that answers "is this available here?".
 *
 * `BuildConfig.PRO` is compiled per flavor, so the free APK cannot be talked
 * into a premium feature at runtime: there is no licence flag to flip, no
 * receipt to forge and no server to ask — which is the point, because this app
 * has no network permission to ask over.
 */
object Premium {
    val isPro: Boolean get() = BuildConfig.PRO

    /** True in the free build, and always false in the paid one. */
    fun isLocked(feature: PremiumFeature): Boolean = !isPro
}
