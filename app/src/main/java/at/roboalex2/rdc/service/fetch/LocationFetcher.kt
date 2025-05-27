package at.roboalex2.rdc.service.fetch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

object LocationFetcher {
    private const val CHANNEL_ID = "location_fetch_channel"
    private const val CHANNEL_NAME = "Location Fetch"
    private const val NOTIF_ID = 1001

    /**
     * Starts a temporary notification, fetches a single high-accuracy location,
     * then removes the notification and calls onResult with one of:
     *  • "Lat:<lat>, Lon:<lon>"
     *  • "Location unavailable"
     *  • "Error getting location: <msg>"
     *  • "Location permission missing"
     */
    fun fetchLocation(context: Context, onResult: (String) -> Unit) {
        val fineOk = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val backOk = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineOk || !backOk) {
            onResult("Permission missing")
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(context)
        val cancelSrc = CancellationTokenSource()

        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelSrc.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    onResult("Lat:${loc.latitude}, Lon:${loc.longitude}")
                } else {
                    onResult("Location unavailable")
                }
            }
            .addOnFailureListener {
                onResult("Location unavailable")
            }
    }
}