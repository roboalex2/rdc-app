package at.roboalex2.rdc

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import at.roboalex2.rdc.navigation.AppNavHost
import at.roboalex2.rdc.ui.theme.RdcappTheme

class MainActivity : ComponentActivity() {
    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_SMS,
            Manifest.permission.SYSTEM_ALERT_WINDOW
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            RdcappTheme {
                PermissionRequester {
                    AppNavHost()
                }
            }
        }
    }

    @SuppressLint("NewApi")
    @Composable
    fun PermissionRequester(content: @Composable () -> Unit) {
        val alarmManager = this.getSystemService(AlarmManager::class.java)
        val filteredPermissions = REQUIRED_PERMISSIONS.filter { perm ->
            when (perm) {
                Manifest.permission.ACCESS_BACKGROUND_LOCATION ->
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

                Manifest.permission.FOREGROUND_SERVICE_LOCATION ->
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

                Manifest.permission.POST_NOTIFICATIONS ->
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

                Manifest.permission.SYSTEM_ALERT_WINDOW ->
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

                else -> true
            }
        }

        val allPermissionsGranted = filteredPermissions.all { perm ->
            if (perm == Manifest.permission.SYSTEM_ALERT_WINDOW)
                Settings.canDrawOverlays(this@MainActivity)
            else
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    perm
                ) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            content()
            return
        }

        var currentIndex by remember { mutableStateOf(0) }
        var allGranted by remember { mutableStateOf(false) }

        // Launcher for single permission
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                if (currentIndex < filteredPermissions.lastIndex) {
                    currentIndex++
                } else {
                    allGranted = true
                }
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Permission denied: ${filteredPermissions[currentIndex]}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val settingsLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (Settings.canDrawOverlays(this@MainActivity)) {
                if (currentIndex < filteredPermissions.lastIndex) {
                    currentIndex++
                } else {
                    allGranted = true
                }
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Permission denied: ${filteredPermissions[currentIndex]}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Launch whenever currentIndex changes and not done
        LaunchedEffect(currentIndex, allGranted) {
            if (!allGranted) {
                val permission = filteredPermissions[currentIndex]
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity, permission
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    if (permission == Manifest.permission.SYSTEM_ALERT_WINDOW) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${this@MainActivity.packageName}")
                        }
                        settingsLauncher.launch(intent)
                    } else {
                        permissionLauncher.launch(permission)
                    }
                } else {
                    // already granted, skip to next
                    if (currentIndex < filteredPermissions.lastIndex) {
                        currentIndex++
                    } else {
                        allGranted = true
                    }
                }
            }
        }

        if (allGranted) {
            content()
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val permName = filteredPermissions.getOrNull(currentIndex)
                    ?.substringAfterLast('.')
                Text(text = "Requesting $permName")
            }
        }
    }
}
