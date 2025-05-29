package at.roboalex2.rdc.presentation

import android.telephony.PhoneNumberUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import at.roboalex2.rdc.model.NumberItem
import at.roboalex2.rdc.navigation.Screen
import at.roboalex2.rdc.view_model.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val viewModel: SettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val tabs = listOf("By Permission", "By Numbers")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tabs[uiState.tabIndex]) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = uiState.tabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = uiState.tabIndex == index,
                        onClick = { viewModel.setTabIndex(index) }
                    )
                }
            }
            when (uiState.tabIndex) {
                0 -> PermissionOverviewTab(navController)
                1 -> AllNumbersTab(navController, uiState.numbers, viewModel)
            }
        }
    }
}


@Composable
private fun PermissionOverviewTab(navController: NavHostController) {
    // Exactly these four permissions, no delete icons, no FAB
    val permissions = listOf("Location", "SoundAlert", "Flashlight", "Camera")

    LazyColumn {
        items(permissions) { perm ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Screen.PermissionList.route(perm)) }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(perm, fontSize = 16.sp)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun AllNumbersTab(
    navController: NavHostController,
    numbers: List<NumberItem>,
    viewModel: SettingsViewModel
) {
    var showDialog by remember { mutableStateOf(false) }
    var newNumber by remember { mutableStateOf("") }

    Scaffold(
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
                        onValueChange = { newNumber = PhoneNumberUtils.normalizeNumber(it) },
                        label = { Text("Phone number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.addNumber(newNumber.trim())
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
            items(numbers) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(item.number, fontSize = 16.sp)
                        Text(item.permissions.joinToString(), fontSize = 12.sp)
                    }
                    Row {
                        IconButton(onClick = { navController.navigate(Screen.EditNumber.route(item.number)) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { viewModel.deleteNumber(item.number) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}