package com.guestvault.domain.model

/**
 * A reusable allow-list preset the owner can start a session from.
 *
 * A profile is a *snapshot*, not a live link: editing the allow-list afterwards
 * does not change a saved profile, and saving again creates another one. That
 * is what makes "kids", "colleague" and "stranger" useful — each keeps the
 * settings that suited it last time.
 */
data class GuestProfile(
    val id: String,
    val name: String,
    val packages: Set<String> = emptySet(),
    /** 0 means "no limit", matching [GuestSession.durationMinutes]. */
    val durationMinutes: Int = 0,
    val lockTaskEnabled: Boolean = true,
    /** Creation time, used only to keep a stable order in the list. */
    val createdAt: Long = 0L
) {
    val appCount: Int get() = packages.size

    /** A profile with no apps cannot start a session, so it cannot be used. */
    val isUsable: Boolean get() = packages.isNotEmpty()
}
