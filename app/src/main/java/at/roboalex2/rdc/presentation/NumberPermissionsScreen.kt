package at.roboalex2.rdc.presentation

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import at.roboalex2.rdc.navigation.Screen
import at.roboalex2.rdc.view_model.SettingsViewModel

@SuppressLint("UnrememberedGetBackStackEntry")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberPermissionsScreen(
    navController: NavHostController,
    number: String
) {
    val settingsEntry = remember(navController) {
        navController.getBackStackEntry(Screen.Settings.route)
    }
    val viewModel: SettingsViewModel = viewModel(settingsEntry)
    val uiState by viewModel.uiState.collectAsState()
    val item = uiState.numbers.find { it.number == number }
    val perms = listOf("Location", "SoundAlert", "Flashlight", "Camera")



    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(number) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                // first apply the scaffold’s inner padding (e.g. below the TopBar)
                .padding(paddingValues)
                // then your own 16dp content padding
                .padding(16.dp)
                .fillMaxSize()
        ) {
            perms.forEach { perm ->
                val checked = perm in (item?.permissions ?: emptyList())
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { new ->
                            if (new) viewModel.addPermission(number, perm)
                            else viewModel.removePermission(number, perm)
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(perm, fontSize = 16.sp)
                }
            }
        }
    }
}

