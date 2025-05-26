package at.roboalex2.rdc.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Settings : Screen("settings")
    object PermissionList : Screen("permissionList/{permission}") {
        fun route(permission: String) = "permissionList/$permission"
    }
    object AllNumbers : Screen("allNumbers")
    object EditNumber : Screen("editNumber/{number}") {
        fun route(number: String) = "editNumber/$number"
    }
}

