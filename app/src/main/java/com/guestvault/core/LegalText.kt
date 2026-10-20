package com.guestvault.core

/**
 * Copy shown in Settings → Privacy policy and About → Limitations.
 * Kept in one place so the app and the README never contradict each other.
 */
object LegalText {

    const val PRIVACY_POLICY = """
GuestVault is built to keep your data on your phone.

WHAT WE STORE
• Your settings: allowed apps, auto-wipe preference, theme and session timer.
• A salted PBKDF2 hash of your 4-digit PIN inside Android's encrypted
  preferences. The PIN itself is never written to disk or sent anywhere.
• A small session history: start time, end time, number of allowed apps and
  how the session ended. We never record what the guest did.

WHAT WE DO NOT DO
• No account, no sign-up, no cloud sync and no analytics.
• No internet permission is requested. GuestVault cannot upload your data
  even if it wanted to.
• We do not read your messages, contacts, photos or files.

GUEST SESSIONS
• A session stores only the list of allowed packages and the timer.
• When auto-wipe is on, GuestVault clears its own cache and guest scratch
  folder. It can clear other apps' data only when the device is managed by
  GuestVault as Device Owner (an optional, advanced setup).

PERMISSIONS
• Notifications: shows the ongoing "Guest Mode is on" notice.
• Biometric: used only to unlock owner mode. Data stays in the Keystore.
• No accessibility service, no usage access, no device admin by default.

REMOVING YOUR DATA
Uninstall GuestVault or clear its storage to erase settings, the PIN hash and
the session history. Nothing leaves the device.
"""

    val LIMITATIONS = listOf(
        "A real Android guest user needs system/signature permissions. Normal apps cannot create one.",
        "GuestVault replaces the launcher experience instead: a restricted home screen that only shows the apps you allow.",
        "Blocking Home and Recents is only reliable with screen pinning (startLockTask) or full Device Owner / kiosk provisioning.",
        "The notification shade, quick settings and hardware keys stay available unless the screen is pinned.",
        "Clearing other apps' data requires Device Owner mode; GuestVault never claims otherwise.",
        "GuestVault does not use an Accessibility Service. Anything that needs one is out of scope by design."
    )

    const val ABOUT_INTRO =
        "GuestVault brings a practical Guest Mode to phones that don't ship one. " +
            "It is deliberately honest about what an ordinary Android app can and cannot do."

    const val HOW_IT_WORKS = """
1. You choose the apps a guest is allowed to open.
2. GuestVault stores the session and opens a restricted home screen.
3. Optionally, Android screen pinning keeps the guest inside that screen.
4. Your PIN (and fingerprint, if enabled) ends the session. Auto-wipe can
   clear the guest scratch space at the same time.
"""
}
