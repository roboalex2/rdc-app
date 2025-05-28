package at.roboalex2.rdc.service.fetch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

object LocationFetcher {

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