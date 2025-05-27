// SmsCommandReceiver.kt
package at.roboalex2.rdc.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import at.roboalex2.rdc.R
import at.roboalex2.rdc.persistence.AppDatabase
import at.roboalex2.rdc.persistence.entity.CommandEntity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val CHANNEL_ID = "sms_command_channel"
private const val CHANNEL_NAME = "SMS Commands"
private const val CHANNEL_DESC = "Notifications when SMS commands are executed"
private var channelCreated = false

class SmsCommandReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // 1. extract messages
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        // 2. tell the system we’re going async
        val pendingResult = goAsync()

        // 3. do all work off the main thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (msg in messages) {
                    val sender = msg.originatingAddress ?: continue
                    val body   = msg.messageBody.trim()
                    handleCommand(ctx, sender, body)
                }
            } finally {
                // 4. let the system know we’re done
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleCommand(ctx: Context, sender: String, body: String) {
        Log.i("SmsReceiver", "SMS from $sender: \"$body\"")
        val parts = body.split("\\s+".toRegex(), limit = 2)
        val cmd = parts[0].lowercase(Locale.US)

        val dao    = AppDatabase.getDatabase(ctx).numberDao()
        val record = dao.getNumber(sender)
        val perms  = record?.permissions ?: emptyList()

        if (cmd == "location" && "Location" in perms) {
            val reply = fetchLocation(ctx)

            // SMS permission guard
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) return

            // log to DB
            AppDatabase.getDatabase(ctx).commandDao().insertCommand(
                CommandEntity(
                    dateTime = Date().toString(),
                    issuer   = sender,
                    type     = "Location"
                )
            )
            Handler(Looper.getMainLooper()).post {
                showExecutionNotification(ctx, sender, cmd)
            }

            // send the SMS
            sendReply(ctx, sender, reply)
        }

        // TODO: handle other commands (flashlight, soundAlert, etc.)
    }

    @RequiresPermission(Manifest.permission.SEND_SMS)
    private fun sendReply(ctx: Context, to: String, text: String) {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
            ctx.getSystemService(SmsManager::class.java)
                .createForSubscriptionId(subscriptionId)
        } else {
            SmsManager.getDefault()
        }

        smsManager.sendTextMessage(to, null, text, null, null)
    }

    private suspend fun fetchLocation(ctx: Context): String {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return "Location permission missing"

        val client = LocationServices.getFusedLocationProviderClient(ctx)
        return try {
            val loc = suspendCancellableCoroutine<android.location.Location?> { cont ->
                val cancelSrc = CancellationTokenSource()
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelSrc.token)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }

                cont.invokeOnCancellation { cancelSrc.cancel() }
            }

            loc?.let { "Lat:${it.latitude}, Lon:${it.longitude}" }
                ?: "Location unavailable"
        } catch (e: Exception) {
            "Error getting location: ${e.message}"
        }
    }

    private fun ensureChannel(ctx: Context) {
        if (channelCreated) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = CHANNEL_DESC }

            ctx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
        channelCreated = true
    }

    private fun showExecutionNotification(ctx: Context, issuer: String, command: String) {
        ensureChannel(ctx)

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Executed SMS Command")
            .setContentText("From $issuer → $command")
            .setAutoCancel(true)
            .build()

        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(ctx)
                .notify(issuer.hashCode() xor command.hashCode(), notif)
        }
    }
}