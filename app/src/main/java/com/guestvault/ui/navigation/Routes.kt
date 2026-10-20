package com.guestvault.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val CONFIRM_SESSION = "confirm_session"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val ABOUT = "about"

    const val PIN_SETUP = "pin_setup?mode={mode}"
    const val SELECT_APPS = "select_apps?next={next}"

    fun pinSetup(mode: String = "setup"): String = "pin_setup?mode=$mode"

    fun selectApps(next: String = "confirm"): String = "select_apps?next=$next"
}
