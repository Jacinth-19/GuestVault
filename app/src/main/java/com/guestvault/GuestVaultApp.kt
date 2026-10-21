package com.guestvault

import android.app.Application
import com.guestvault.core.GuestNotifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GuestVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        GuestNotifications.ensureChannels(this)
    }
}
