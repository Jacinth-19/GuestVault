package com.guestvault.data.session

import com.guestvault.domain.policy.AppLimitPolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Counts how long the guest has spent in each allowed app during one session.
 *
 * Only the current session is tracked, and only in memory: a session that ends
 * (or a process that dies) starts from zero, which is the honest reading of
 * "15 minutes of YouTube per hand-over". Nothing here is ever written to disk,
 * so no record of what the guest actually used can outlive the session.
 *
 * The clock runs while the guest is away from GuestVault's own guest home and
 * pauses when they come back — see [pause] and [resume].
 */
@Singleton
class AppUsageTracker @Inject constructor() {

    private val lock = Any()

    /** Accumulated millis per package for this session. */
    private val used = mutableMapOf<String, Long>()

    /** The app whose clock is currently running, if any. */
    private var current: String? = null
    private var runningSince: Long? = null

    private val _exhausted = MutableStateFlow<Set<String>>(emptySet())

    /** Packages that have used up their budget; the guest home dims their tiles. */
    val exhausted: StateFlow<Set<String>> = _exhausted.asStateFlow()

    private val _usage = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Spent millis per package, for the guest home's "12 min left" captions. */
    val usage: StateFlow<Map<String, Long>> = _usage.asStateFlow()

    fun startSession() = synchronized(lock) {
        used.clear()
        current = null
        runningSince = null
        _exhausted.value = emptySet()
        _usage.value = emptyMap()
    }

    /** The guest tapped [packageName]: stop the previous clock, start this one. */
    fun launch(packageName: String, now: Long = System.currentTimeMillis()) =
        synchronized(lock) {
            accumulate(now)
            current = packageName
            runningSince = now
            publish(now)
        }

    /** The guest came back to the guest home, or the screen went away. */
    fun pause(now: Long = System.currentTimeMillis()) = synchronized(lock) {
        accumulate(now)
        runningSince = null
        publish(now)
    }

    /** The guest went back into the last app. */
    fun resume(now: Long = System.currentTimeMillis()) = synchronized(lock) {
        if (current != null && runningSince == null) {
            runningSince = now
            publish(now)
        }
    }

    fun usedMillis(packageName: String, now: Long = System.currentTimeMillis()): Long =
        synchronized(lock) { spent(packageName, now) }

    /**
     * Called once a second by the session service while a guest session runs.
     *
     * Returns the package that has *just* run out of budget, so the caller can
     * bounce the guest back to the guest home exactly once. A package is
     * reported only on the tick where it crosses the line.
     */
    fun tick(limits: Map<String, Int>, now: Long = System.currentTimeMillis()): String? =
        synchronized(lock) {
            publish(now)
            val packageName = current ?: return null
            val limit = limits[packageName] ?: return null
            if (!AppLimitPolicy.hasLimit(limit)) return null
            if (packageName in _exhausted.value) return null
            if (!AppLimitPolicy.isExhausted(limit, spent(packageName, now))) return null

            // Park the elapsed time before stopping the clock, or the caption
            // would jump back to "full budget" the moment the app is locked.
            accumulate(now)
            _exhausted.value = _exhausted.value + packageName
            runningSince = null
            publish(now)
            packageName
        }

    private fun spent(packageName: String, now: Long): Long {
        val parked = used[packageName] ?: 0L
        val live = if (packageName == current) running(now) else 0L
        return parked + live
    }

    private fun accumulate(now: Long) {
        val packageName = current ?: return
        val live = running(now)
        if (live > 0L) used[packageName] = (used[packageName] ?: 0L) + live
        runningSince = now
    }

    private fun publish(now: Long) {
        val live = current?.let { mapOf(it to spent(it, now)) }.orEmpty()
        _usage.value = used + live
    }

    private fun running(now: Long): Long =
        runningSince?.let { (now - it).coerceAtLeast(0L) } ?: 0L
}
