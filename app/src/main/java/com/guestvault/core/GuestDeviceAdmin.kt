package com.guestvault.core

import android.app.admin.DeviceAdminReceiver

/**
 * Empty device-admin receiver.
 *
 * GuestVault never asks to become a device admin during normal use. The
 * receiver only exists so the app *can* be provisioned as Device Owner on a
 * dedicated device (`adb shell dpm set-device-owner ...`), which is the only
 * Android-supported way for a normal app to clear another app's data after a
 * guest session.
 */
class GuestDeviceAdmin : DeviceAdminReceiver()
