package com.datausage.monitor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.datausage.monitor.ui.screen.appconfig.AppConfigScreen
import com.datausage.monitor.ui.screen.cost.CostScreen
import com.datausage.monitor.ui.screen.dashboard.DashboardScreen
import com.datausage.monitor.ui.screen.importexport.ImportExportScreen
import com.datausage.monitor.ui.screen.limits.LimitsScreen
import com.datausage.monitor.ui.screen.profiles.AddEditProfileScreen
import com.datausage.monitor.ui.screen.profiles.ProfileListScreen
import com.datausage.monitor.ui.screen.reports.ReportsScreen
import com.datausage.monitor.ui.screen.session.SessionScreen
import com.datausage.monitor.ui.screen.settings.SettingsScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.ProfileList.route
    ) {
        composable(Screen.ProfileList.route) {
            ProfileListScreen(
                onNavigateToAddProfile = {
                    navController.navigate(Screen.AddEditProfile.createRoute())
                },
                onNavigateToEditProfile = { profileId ->
                    navController.navigate(Screen.AddEditProfile.createRoute(profileId))
                },
                onNavigateToDashboard = { profileId ->
                    navController.navigate(Screen.Dashboard.createRoute(profileId))
                }
            )
        }

        composable(
            route = Screen.AddEditProfile.route,
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            AddEditProfileScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Dashboard.route,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType })
        ) {
            DashboardScreen(
                onNavigateToSession = { profileId ->
                    navController.navigate(Screen.Session.createRoute(profileId))
                },
                onNavigateToAppConfig = { profileId ->
                    navController.navigate(Screen.AppConfig.createRoute(profileId))
                },
                onNavigateToReports = { profileId ->
                    navController.navigate(Screen.Reports.createRoute(profileId))
                },
                onNavigateToLimits = { profileId ->
                    navController.navigate(Screen.Limits.createRoute(profileId))
                },
                onNavigateToCost = { profileId ->
                    navController.navigate(Screen.Cost.createRoute(profileId))
                },
                onNavigateToImportExport = { profileId ->
                    navController.navigate(Screen.ImportExport.createRoute(profileId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Session.route,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType })
        ) {
            SessionScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.AppConfig.route,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType })
        ) {
            AppConfigScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Reports.route,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType })
        ) {
            ReportsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Limits.route,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType })
        ) {
            LimitsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Cost.route,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType })
        ) {
            CostScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.ImportExport.route,
            arguments = listOf(navArgument("profileId") { type = NavType.LongType })
        ) {
            ImportExportScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
