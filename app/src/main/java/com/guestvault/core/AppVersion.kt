package com.guestvault.core

import com.guestvault.BuildConfig

/**
 * The version string the app *shows*.
 *
 * [BuildConfig.VERSION_NAME] carries the Gradle build-type suffix in debug
 * builds (`1.2.0-debug`). That is useful in a crash report and wrong on a
 * splash screen, a README screenshot or a Play Store listing, so every
 * user-visible version goes through here.
 *
 * One place, because the whole point of the version pill is that it can never
 * disagree with the dashboard footer or the About screen again.
 */
object AppVersion {

    /** `1.2.0-debug` → `1.2.0`; a release build is returned untouched. */
    val display: String
        get() = BuildConfig.VERSION_NAME.substringBefore('-')
}

/**
 * The public privacy policy the Play listing points at. It has to stay a live,
 * non-geofenced web page; [LegalText.PRIVACY_POLICY] is the offline copy.
 */
const val PRIVACY_POLICY_URL = "https://jacinth-19.github.io/GuestVault/privacy.html"
