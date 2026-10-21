package com.guestvault.guest

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.guestvault.core.Formatters
import com.guestvault.core.GuestNotifications
import com.guestvault.core.notifications.TimerReminders
import com.guestvault.data.session.SessionController
import com.guestvault.domain.model.EndReason
import com.guestvault.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps a guest session alive and enforces the
 * optional session timer.
 *
 * Why a service? Without it, Android could kill GuestVault while the guest is
 * in another app, and a timed session would never expire. The service is
 * declared as `specialUse` because a guest session is a user-visible, long
 * running "device lock timer" — not a media or location use case.
 */
@AndroidEntryPoint
class GuestSessionService : Service() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var sessionController: SessionController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundSafely()
        observeSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely()
        if (observeJob == null) observeSession()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observeJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSafely() {
        val notification = GuestNotifications.buildSessionNotification(this, remainingLabel = null)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    GuestNotifications.ID_SESSION,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(GuestNotifications.ID_SESSION, notification)
            }
        } catch (t: Throwable) {
            // Can happen if the OS refuses a background FGS start. The session
            // itself stays persisted; the owner dashboard can resume it.
            Log.w(TAG, "Could not start guest session foreground service", t)
            stopSelf()
        }
    }

    private fun observeSession() {
        observeJob = scope.launch {
            settingsRepository.settings
                .map { it.activeSession }
                .distinctUntilChanged()
                .collectLatest { session ->
                    if (session == null) {
                        GuestNotifications.clearTimerReminders(this@GuestSessionService)
                        stopSelf()
                        return@collectLatest
                    }

                    var lastLabel: String? = null
                    val notified = mutableSetOf<Int>()
                    while (isActive) {
                        val remaining = session.remainingMillis()
                        val label = remaining?.let(Formatters::countdown)

                        if (label != lastLabel) {
                            GuestNotifications.updateSessionNotification(
                                this@GuestSessionService,
                                label
                            )
                            lastLabel = label
                        }

                        if (remaining != null) {
                            // One reminder per threshold, checked every tick so a
                            // dozing device still gets the most relevant one.
                            val due = TimerReminders.due(
                                totalMinutes = session.durationMinutes,
                                remainingMillis = remaining,
                                alreadyNotified = notified
                            )
                            if (due != null) {
                                notified += TimerReminders.superseded(due)
                                val enabled = runCatching {
                                    settingsRepository.settings
                                        .first()
                                        .notifyTimerAlerts
                                }.getOrDefault(true)
                                if (enabled) {
                                    GuestNotifications.notifyTimerReminder(
                                        this@GuestSessionService,
                                        due
                                    )
                                }
                            }
                        }

                        if (remaining != null && remaining <= 0L) {
                            Log.i(TAG, "Guest session timer expired")
                            sessionController.end(EndReason.TIMER_EXPIRED)
                            stopSelf()
                            return@collectLatest
                        }
                        delay(1_000)
                    }
                }
        }
    }

    companion object {
        private const val TAG = "GuestSessionService"

        fun start(context: Context) {
            val intent = Intent(context, GuestSessionService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuestSessionService::class.java))
        }
    }
}
