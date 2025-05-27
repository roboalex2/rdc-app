package at.roboalex2.rdc.presentation

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun PermissionNumberListScreen(navController: NavHostController, permission: String) {
    val settingsEntry = remember(navController) {
        navController.getBackStackEntry(Screen.Settings.route)
    }
    val viewModel: SettingsViewModel = viewModel(settingsEntry)
    val uiState by viewModel.uiState.collectAsState()
    val numbersWithPerm = uiState.numbers.filter { permission in it.permissions }

    var showDialog by remember { mutableStateOf(false) }
    var newNumber by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(permission) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Number")
            }
        }
    ) { padding ->

        if (showDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDialog = false
                    newNumber = ""
                },
                title = { Text("Add Number") },
                text = {
                    OutlinedTextField(
                        value = newNumber,
                        onValueChange = { newNumber = it },
                        label = { Text("Phone number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.addPermission(newNumber.trim(), permission)
                        showDialog = false
                        newNumber = ""
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDialog = false
                        newNumber = ""
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        LazyColumn(contentPadding = padding) {
            items(numbersWithPerm) { num ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(num.number, fontSize = 16.sp)
                    IconButton(onClick = { viewModel.removePermission(num.number, permission) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}