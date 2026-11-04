package com.guestvault.domain.policy

/** One photo taken after a failed unlock, as the UI and the store see it. */
data class IntruderPhoto(
    val name: String,
    val takenAt: Long
)

/**
 * Rules for the intruder-photo feature, kept away from the camera so they can
 * be tested on a machine with no camera at all — which is every CI runner.
 *
 * Nothing here decides *whether* the owner may take photos; that is their
 * choice in Settings. These rules answer the smaller questions: should this
 * particular failed attempt be photographed, and which files are old enough to
 * delete.
 */
object IntruderPolicy {

    /**
     * The cap on stored photos.
     *
     * Small on purpose. Every photo is a picture of a person who may not expect
     * one, so the feature keeps the last few and forgets the rest — it is a
     * deterrent and a record of the most recent attempt, not a gallery.
     */
    const val MAX_PHOTOS = 8

    /**
     * True when a failed unlock should be photographed.
     *
     * All four conditions matter: the owner switched it on, the device has a
     * front camera, the runtime permission is granted (it is asked for when the
     * feature is enabled, never at the lock screen), and an attempt actually
     * failed. A missing camera must never crash the lock screen — it just means
     * no photo.
     */
    fun shouldCapture(
        enabled: Boolean,
        cameraAvailable: Boolean,
        permissionGranted: Boolean,
        failedAttempts: Int
    ): Boolean = enabled && cameraAvailable && permissionGranted && failedAttempts > 0

    /** The newest [keep] photos, oldest last. */
    fun keepNewest(photos: List<IntruderPhoto>, keep: Int = MAX_PHOTOS): List<IntruderPhoto> =
        photos.sortedByDescending { it.takenAt }.take(keep.coerceAtLeast(0))

    /** Everything that falls outside the cap, and so should be deleted. */
    fun toDelete(photos: List<IntruderPhoto>, keep: Int = MAX_PHOTOS): List<IntruderPhoto> {
        val kept = keepNewest(photos, keep).mapTo(mutableSetOf()) { it.name }
        return photos.filter { it.name !in kept }
    }

    /**
     * Why the feature cannot be used right now, or null when it can.
     * Shown verbatim in Settings so a greyed-out switch always has a reason.
     */
    fun unavailableReason(cameraAvailable: Boolean, permissionGranted: Boolean): String? = when {
        !cameraAvailable -> "This phone has no front camera, so no photo can be taken."
        !permissionGranted -> "Allow camera access to use this."
        else -> null
    }
}
