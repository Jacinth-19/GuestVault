package com.guestvault.domain.model

/**
 * Package lists used to build a sensible guest allow-list.
 *
 * We deliberately recommend only apps that do not expose personal data
 * (phone, camera, browser, clock, calculator, maps). Apps that can reveal the
 * owner's life are flagged as sensitive and start switched off.
 */
object AppCatalogDefaults {

    /** Safe, useful apps a guest usually needs. */
    val RECOMMENDED: Set<String> = setOf(
        "com.google.android.dialer",
        "com.android.dialer",
        "com.android.camera",
        "com.android.camera2",
        "com.google.android.GoogleCamera",
        "com.android.chrome",
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.google.android.calculator",
        "com.android.calculator2",
        "com.google.android.apps.maps"
    )

    /**
     * Apps that expose owner data or device control. They can be allowed,
     * but the UI warns the owner first.
     */
    val SENSITIVE: Set<String> = setOf(
        "com.android.settings",
        "com.android.vending",
        "com.google.android.documentsui",
        "com.android.documentsui",
        "com.google.android.apps.photos",
        "com.google.android.apps.gallery",
        "com.android.gallery3d",
        "com.sec.android.gallery3d",
        "com.google.android.apps.messaging",
        "com.android.messaging",
        "com.samsung.android.messaging",
        "com.android.contacts",
        "com.google.android.contacts",
        "com.google.android.gm",
        "com.google.android.gm.lite",
        "com.android.email",
        "com.google.android.apps.docs",
        "com.google.android.apps.walletnfcrel",
        "com.google.android.apps.nbu.files",
        "com.android.chrome" /* browser history/accounts: flagged, but still useful */
    )

    private val communicationPackages = setOf(
        "com.google.android.dialer", "com.android.dialer",
        "com.google.android.apps.messaging", "com.android.messaging",
        "com.android.contacts", "com.google.android.contacts",
        "com.google.android.gm", "com.whatsapp", "org.telegram.messenger",
        "com.google.android.apps.messaging"
    )

    private val mediaPackages = setOf(
        "com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera",
        "com.google.android.apps.photos", "com.google.android.youtube",
        "com.google.android.apps.youtube.music", "com.spotify.music",
        "com.sec.android.gallery3d", "com.android.gallery3d"
    )

    private val toolsPackages = setOf(
        "com.android.chrome", "com.google.android.apps.maps",
        "com.google.android.deskclock", "com.android.deskclock",
        "com.google.android.calculator", "com.android.calculator2",
        "com.google.android.calendar", "com.android.calendar",
        "com.google.android.keep"
    )

    fun categoryFor(packageName: String): AppCategory = when {
        packageName in toolsPackages -> AppCategory.TOOLS
        packageName in mediaPackages -> AppCategory.MEDIA
        packageName in communicationPackages -> AppCategory.COMMUNICATION
        else -> AppCategory.OTHER
    }

    fun isRecommended(packageName: String): Boolean = packageName in RECOMMENDED

    fun isSensitive(packageName: String): Boolean = packageName in SENSITIVE
}
