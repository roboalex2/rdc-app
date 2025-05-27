package at.roboalex2.rdc

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import at.roboalex2.rdc.navigation.AppNavHost
import at.roboalex2.rdc.ui.theme.RdcappTheme

class MainActivity : ComponentActivity() {
    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.CAMERA
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

    @Composable
    fun PermissionRequester(content: @Composable () -> Unit) {
        val allPermissionsGranted = REQUIRED_PERMISSIONS.all { perm ->
            ContextCompat.checkSelfPermission(this@MainActivity, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (allPermissionsGranted) {
            content()
            return
        }

        var currentIndex by remember { mutableStateOf(0) }
        var allGranted by remember { mutableStateOf(false) }
        var requestedDnd by remember { mutableStateOf(false) }

        // Launcher for single permission
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                if (currentIndex < REQUIRED_PERMISSIONS.lastIndex) {
                    // move to next permission
                    currentIndex++
                } else {
                    allGranted = true
                }
            } else {
                // optional: handle denial
            }
        }

        // Launch whenever currentIndex changes and not done
        LaunchedEffect(currentIndex, allGranted) {
            if (!allGranted) {
                val permission = REQUIRED_PERMISSIONS[currentIndex]
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity, permission
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(permission)
                } else {
                    // already granted, skip to next
                    if (currentIndex < REQUIRED_PERMISSIONS.lastIndex) {
                        currentIndex++
                    } else {
                        allGranted = true
                    }
                }
            } else if (!requestedDnd) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (!nm.isNotificationPolicyAccessGranted) {
                    registerDndBypassChannel()
                    requestedDnd = true
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }
        }

        if (allGranted && requestedDnd) {
            content()
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val permName = REQUIRED_PERMISSIONS.getOrNull(currentIndex)
                    ?.substringAfterLast('.') ?: "DND access"
                Text(text = "Requesting $permName")
            }
        }
    }

    private fun registerDndBypassChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "worker_fg_channel"
            val channel = NotificationChannel(
                channelId,
                "Background Worker",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setBypassDnd(true)
                enableLights(true)
                enableVibration(true)
                description = "Used for alerts that bypass Do Not Disturb"
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)

            // Post dummy notification to make app show in DND settings
            val notif = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("DND Bypass Activated")
                .setContentText("This alert ensures your app can bypass DND.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .build()

            nm.notify(4242, notif)
        }
    }
}
