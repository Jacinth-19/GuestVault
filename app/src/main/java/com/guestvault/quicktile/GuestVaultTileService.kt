package com.guestvault.quicktile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.guestvault.MainActivity
import com.guestvault.R
import com.guestvault.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile ("Guest Mode").
 *
 * Idle  → opens the owner app on the *confirm* screen, so starting a session is
 *         still one deliberate tap ("Start Guest Mode") by the owner.
 * Active → jumps straight back into the running session's guest home.
 *
 * The tile never exposes an owner action: it cannot end a session, and it is
 * protected by the system-only `BIND_QUICK_SETTINGS_TILE` permission.
 */
@AndroidEntryPoint
class GuestVaultTileService : TileService() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val active = settingsRepository.settings.first().activeSession != null
            startActivityAndCollapseCompat(
                if (active) {
                    // MainActivity redirects into GuestHomeActivity on resume.
                    Intent(this@GuestVaultTileService, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                } else {
                    Intent(this@GuestVaultTileService, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(MainActivity.EXTRA_QUICK_START_SESSION, true)
                }
            )
        }
    }

    private fun refreshTile() {
        scope.launch {
            val settings = runCatching { settingsRepository.settings.first() }.getOrNull()
            val active = settings?.activeSession != null

            qsTile?.apply {
                state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                label = getString(R.string.qs_tile_label)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    subtitle = if (active) {
                        "Session running · tap to return"
                    } else {
                        "Tap to start a guest session"
                    }
                }
                icon = Icon.createWithResource(
                    this@GuestVaultTileService,
                    R.drawable.ic_status_shield
                )
                updateTile()
            }
        }
    }

    /**
     * `startActivityAndCollapse(Intent)` is deprecated in Android 14 and throws
     * on some OEM builds, so the PendingIntent overload is used when available.
     * The legacy call still has to run on API 29-33, where it is the only option.
     */
    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startActivityAndCollapseCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private companion object {
        const val REQUEST_CODE = 4712
    }
}
