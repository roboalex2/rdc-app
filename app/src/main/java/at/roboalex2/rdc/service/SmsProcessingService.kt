package at.roboalex2.rdc.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.app.JobIntentService
import at.roboalex2.rdc.R
import at.roboalex2.rdc.persistence.AppDatabase
import at.roboalex2.rdc.persistence.entity.CommandEntity
import at.roboalex2.rdc.service.fetch.LocationFetcher
import kotlinx.coroutines.runBlocking
import java.util.*

class SmsProcessingService : Service() {
    companion object {
        private const val JOB_ID = 42
        private const val CHANNEL_ID = "worker_fg_channel"
        private const val CHANNEL_NAME = "Background Worker"
        private const val NOTIF_ID = 4242

        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_BODY   = "extra_body"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra(EXTRA_SENDER) ?: return START_NOT_STICKY
        val body   = intent.getStringExtra(EXTRA_BODY)   ?: return START_NOT_STICKY

        // 2) Parse and check permissions
        val parts = body.split("\\s+".toRegex(), limit = 2)
        val cmd = parts[0].lowercase(Locale.US)
        val args = if (parts.size > 1) parts[1].lowercase(Locale.US) else ""
        val dao    = AppDatabase.getDatabase(applicationContext).numberDao()
        val perms  = runBlocking { dao.getNumber(sender)?.permissions ?: emptyList() }

        if (perms.isNotEmpty()) {
            runBlocking {
                AppDatabase
                    .getDatabase(applicationContext)
                    .commandDao()
                    .insertCommand(
                        CommandEntity(
                            dateTime = Date().toString(),
                            issuer   = sender,
                            type     = body
                        )
                    )
            }
        }

        if ("location".equals(cmd, true) && "Location" in perms) {
            startForegroundServiceWithNotification();
            LocationFetcher.fetchLocation(applicationContext) { reply ->
                stopForeground(STOP_FOREGROUND_REMOVE);
                if (ContextCompat.checkSelfPermission(
                        this, Manifest.permission.SEND_SMS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    sendReply(this, sender, reply)
                }

                runBlocking {
                    AppDatabase
                        .getDatabase(this@SmsProcessingService)
                        .commandDao()
                        .insertCommand(
                            CommandEntity(
                                dateTime = Date().toString(),
                                issuer   = sender,
                                type     = cmd
                            )
                        )
                }

                showExecutionNotification(this, sender, cmd)
                stopSelf()
            }
        } else if ("soundalert".equals(cmd, true) && "SoundAlert" in perms) {
            startForegroundServiceWithNotification()

            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                sendReply(this, sender, "Playing Alert")
            }
            playAlertSound()
            runBlocking {
                AppDatabase
                    .getDatabase(this@SmsProcessingService)
                    .commandDao()
                    .insertCommand(
                        CommandEntity(
                            dateTime = Date().toString(),
                            issuer   = sender,
                            type     = cmd
                        )
                    )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            showExecutionNotification(this, sender, cmd)
            stopSelf()
        } else if ("flashlight".equals(cmd, true) && "Flashlight" in perms) {
            startForegroundServiceWithNotification()

            var result = "No execution"
            if ("true" == args || "on" == args || "an" == args) {
                result = setFlashlight(this, true)
            } else {
                result = setFlashlight(this, false)
            }

            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                sendReply(this, sender, result)
            }
            runBlocking {
                AppDatabase
                    .getDatabase(this@SmsProcessingService)
                    .commandDao()
                    .insertCommand(
                        CommandEntity(
                            dateTime = Date().toString(),
                            issuer   = sender,
                            type     = cmd
                        )
                    )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            showExecutionNotification(this, sender, cmd)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        // create channel if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Rdc is fetching data..." }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(chan)
            chan.setBypassDnd(true)
        }
        // build & start
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Fetching data…")
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    private fun playAlertSound() {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri) ?: return

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        // Set alarm stream to max volume
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }

        ringtone.play()
    }

    @RequiresPermission(Manifest.permission.SEND_SMS)
    private fun sendReply(ctx: Context, to: String, text: String) {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
            ctx.getSystemService(SmsManager::class.java)
                .createForSubscriptionId(subId)
        } else {
            SmsManager.getDefault()
        }
        smsManager.sendTextMessage(to, null, text, null, null)
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    fun setFlashlight(context: Context, turnOn: Boolean): String {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "No flashlight available"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraId, turnOn)
            }
            return "Flashlight: ${turnOn}"
        } catch (e: Exception) {
            e.printStackTrace()
            return "Flashlight error: " + e.message
        }
    }

    private fun showExecutionNotification(ctx: Context, issuer: String, command: String) {
        val channelId = "sms_command_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId, "SMS Commands", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifications for SMS commands" }
            ctx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(chan)
        }

        val notif = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Executed SMS Command")
            .setContentText("From $issuer → $command")
            .setAutoCancel(true)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(ctx).notify(
                issuer.hashCode() xor command.hashCode(), notif
            )
        }
    }
}