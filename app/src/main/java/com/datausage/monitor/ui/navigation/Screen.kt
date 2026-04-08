package com.datausage.monitor.ui.navigation

sealed class Screen(val route: String) {
    data object ProfileList : Screen("profiles")
    data object AddEditProfile : Screen("profiles/edit?profileId={profileId}") {
        fun createRoute(profileId: Long? = null): String =
            if (profileId != null) "profiles/edit?profileId=$profileId"
            else "profiles/edit"
    }
    data object Dashboard : Screen("dashboard/{profileId}") {
        fun createRoute(profileId: Long): String = "dashboard/$profileId"
    }
    data object Session : Screen("session/{profileId}") {
        fun createRoute(profileId: Long): String = "session/$profileId"
    }
    data object AppConfig : Screen("appconfig/{profileId}") {
        fun createRoute(profileId: Long): String = "appconfig/$profileId"
    }
    data object Reports : Screen("reports/{profileId}") {
        fun createRoute(profileId: Long): String = "reports/$profileId"
    }
    data object Limits : Screen("limits/{profileId}") {
        fun createRoute(profileId: Long): String = "limits/$profileId"
    }
    data object Cost : Screen("cost/{profileId}") {
        fun createRoute(profileId: Long): String = "cost/$profileId"
    }
    data object ImportExport : Screen("importexport/{profileId}") {
        fun createRoute(profileId: Long): String = "importexport/$profileId"
    }
    data object Settings : Screen("settings")
    data object PermissionSetup : Screen("permission_setup")
}
