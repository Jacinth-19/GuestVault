package com.guestvault.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.guestvault.ui.screens.about.AboutRoute
import com.guestvault.ui.screens.confirm.ConfirmSessionRoute
import com.guestvault.ui.screens.dashboard.DashboardRoute
import com.guestvault.ui.screens.history.HistoryRoute
import com.guestvault.ui.screens.onboarding.OnboardingRoute
import com.guestvault.ui.screens.pin.PinSetupRoute
import com.guestvault.ui.screens.selectapps.SelectAppsRoute
import com.guestvault.ui.screens.settings.SettingsRoute
import com.guestvault.ui.screens.splash.SplashRoute

@Composable
fun GuestVaultNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        composable(Routes.SPLASH) {
            SplashRoute(
                onReady = { startState ->
                    val destination = when {
                        !startState.onboardingCompleted -> Routes.ONBOARDING
                        !startState.pinSet -> Routes.pinSetup()
                        else -> Routes.DASHBOARD
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ONBOARDING) {
            OnboardingRoute(
                onFinished = {
                    navController.navigate(Routes.pinSetup()) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.PIN_SETUP,
            arguments = listOf(
                navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = "setup"
                }
            )
        ) { entry ->
            val mode = entry.arguments?.getString("mode") ?: "setup"
            PinSetupRoute(
                onDone = {
                    if (mode == "change") {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.PIN_SETUP) { inclusive = true }
                        }
                    }
                },
                onBack = if (mode == "change") {
                    { navController.popBackStack() }
                } else {
                    null
                }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardRoute(
                onStartSession = { navController.navigate(Routes.CONFIRM_SESSION) },
                onManageApps = { navController.navigate(Routes.selectApps(next = "confirm")) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }

        composable(
            route = Routes.SELECT_APPS,
            arguments = listOf(
                navArgument("next") {
                    type = NavType.StringType
                    defaultValue = "confirm"
                }
            )
        ) { entry ->
            val next = entry.arguments?.getString("next") ?: "confirm"
            SelectAppsRoute(
                onBack = { navController.popBackStack() },
                continueLabel = if (next == "back") "Save" else "Continue",
                onContinue = {
                    if (next == "back") {
                        navController.popBackStack()
                    } else if (!navController.popBackStack(Routes.CONFIRM_SESSION, inclusive = false)) {
                        navController.navigate(Routes.CONFIRM_SESSION)
                    }
                }
            )
        }

        composable(Routes.CONFIRM_SESSION) {
            ConfirmSessionRoute(
                onBack = { navController.popBackStack() },
                onEditApps = { navController.navigate(Routes.selectApps(next = "confirm")) },
                onSessionStarted = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.DASHBOARD) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                onChangePin = { navController.navigate(Routes.pinSetup(mode = "change")) },
                onManageApps = { navController.navigate(Routes.selectApps(next = "back")) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }

        composable(Routes.HISTORY) {
            HistoryRoute(onBack = { navController.popBackStack() })
        }

        composable(Routes.ABOUT) {
            AboutRoute(onBack = { navController.popBackStack() })
        }
    }
}
