package com.guestvault.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.guestvault.data.session.SessionController
import com.guestvault.domain.model.EndReason
import com.guestvault.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Closes stale guest sessions after a reboot or an app update.
 *
 * Why: the restricted guest home cannot be re-opened reliably before the owner
 * unlocks the phone, so a session that survives a reboot would leave the owner
 * locked out of their own dashboard while the guest UI is not even running. The
 * session is therefore ended with [EndReason.RECOVERED] — and wiped first when
 * auto-wipe is enabled — the next time the device starts.
 *
 * This receiver never starts a foreground service or an activity, so it is safe
 * under background-start restrictions.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var sessionController: SessionController

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val session = settingsRepository.settings.first().activeSession
                if (session != null) {
                    Log.i(TAG, "Closing guest session left over from a previous boot")
                    sessionController.end(EndReason.RECOVERED)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Could not recover the guest session", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootCompletedReceiver"
    }
}
