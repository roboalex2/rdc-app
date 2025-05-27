package at.roboalex2.rdc.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import at.roboalex2.rdc.presentation.NumberPermissionsScreen
import at.roboalex2.rdc.presentation.MainScreen
import at.roboalex2.rdc.presentation.PermissionNumberListScreen
import at.roboalex2.rdc.presentation.SettingsScreen

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Settings : Screen("settings")
    object PermissionList : Screen("permissionList/{permission}") {
        fun route(permission: String) = "permissionList/$permission"
    }
    object EditNumber : Screen("editNumber/{number}") {
        fun route(number: String) = "editNumber/$number"
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = Screen.Main.route) {
        composable(Screen.Main.route) {
            MainScreen(navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController)
        }
        composable("permissionList/{permission}") { backStack ->
            val permission = backStack.arguments?.getString("permission") ?: ""
            PermissionNumberListScreen(navController, permission)
        }
        composable("editNumber/{number}") { backStack ->
            val number = backStack.arguments?.getString("number") ?: ""
            NumberPermissionsScreen(navController, number)
        }
    }
}
