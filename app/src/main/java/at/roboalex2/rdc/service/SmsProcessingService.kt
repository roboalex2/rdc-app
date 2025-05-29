package at.roboalex2.rdc.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneNumberUtils
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import at.roboalex2.rdc.R
import at.roboalex2.rdc.persistence.AppDatabase
import at.roboalex2.rdc.persistence.entity.CommandEntity
import at.roboalex2.rdc.service.fetch.LocationFetcher
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsProcessingService : Service() {
    companion object {
        private const val CHANNEL_ID = "worker_rdc_channel"
        private const val CHANNEL_NAME = "Background Worker"
        private const val NOTIF_ID = 2424

        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_BODY = "extra_body"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ServiceCast")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = PhoneNumberUtils.normalizeNumber(
            intent?.getStringExtra(EXTRA_SENDER) ?: return START_NOT_STICKY
        )
        val body = intent.getStringExtra(EXTRA_BODY) ?: return START_NOT_STICKY
        Log.i(this.javaClass.name, "Foreground service SMS with $sender: $body")

        val parts = body.split("\\s+".toRegex(), limit = 2)
        val cmd = parts[0].lowercase(Locale.US)
        val args = if (parts.size > 1) parts[1].lowercase(Locale.US) else ""
        val dao = AppDatabase.getDatabase(applicationContext).numberDao()
        val perms = runBlocking {
            val allNumbers = dao.getAllNumbers().firstOrNull().orEmpty()
            allNumbers.firstOrNull { PhoneNumberUtils.compare(it.number, sender) }?.permissions
                ?: emptyList()
        }
        Log.i(this.javaClass.name, "Foreground service SMS with $cmd, $args and perm success")

        if (perms.isEmpty()) {
            Log.i(this.javaClass.name, "No permissions set for $sender")
            stopSelf()
            return START_NOT_STICKY
        }

        if ("location".equals(cmd, true) && "Location" in perms) {
            ensureForegroundServiceWithNotification();
            LocationFetcher.fetchLocation(applicationContext) { reply ->
                SmsSenderService.sendReply(this, sender, reply)
                saveCommandExecution(sender, cmd)
                stopForeground(STOP_FOREGROUND_REMOVE)
                showExecutionNotification(this, sender, cmd)
                stopSelf()
            }
        } else if (("soundalert".equals(cmd, true) || "sound".equals(cmd, true)) && "SoundAlert" in perms) {
            ensureForegroundServiceWithNotification()
            playAlertSound()
            SmsSenderService.sendReply(this, sender, "Playing Alert")
            saveCommandExecution(sender, cmd)
            stopForeground(STOP_FOREGROUND_REMOVE)
            showExecutionNotification(this, sender, cmd)
            stopSelf()
        } else if (("flashlight".equals(cmd, true) || "light".equals(cmd, true)) && "Flashlight" in perms) {
            ensureForegroundServiceWithNotification()
            val result: String = if ("true" == args || "on" == args || "an" == args) {
                setFlashlight(this, true)
            } else {
                setFlashlight(this, false)
            }
            SmsSenderService.sendReply(this, sender, result)
            saveCommandExecution(sender, "$cmd $args")
            stopForeground(STOP_FOREGROUND_REMOVE)
            showExecutionNotification(this, sender, cmd)
            stopSelf()
        } else if (("camera".equals(cmd, true) || "photo".equals(cmd, true)) && "Camera" in perms) {
            val activity = Intent(this, PhotoCaptureActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(PhotoCaptureActivity.EXTRA_RECIPIENT, sender)
                putExtra(PhotoCaptureActivity.EXTRA_ARGS, args)
            }

            val toBundle = ActivityOptions.makeBasic().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
            }.toBundle()

            val fullScreenPI = PendingIntent.getActivity(
                this, 0, activity,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                toBundle
            )
            ensureForegroundServiceWithNotification(fullScreenPI)
            startActivity(activity, toBundle)
            saveCommandExecution(sender, cmd)
            stopForeground(STOP_FOREGROUND_REMOVE)
            showExecutionNotification(this, sender, cmd)
            stopSelf()
            return START_NOT_STICKY
        }

        stopSelf()
        return START_NOT_STICKY
    }

    private fun saveCommandExecution(issuer: String, command: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            .format(Date())

        runBlocking {
            AppDatabase
                .getDatabase(this@SmsProcessingService)
                .commandDao()
                .insertCommand(
                    CommandEntity(
                        dateTime = timestamp,
                        issuer = issuer,
                        type = command
                    )
                )
        }
    }

    private fun ensureForegroundServiceWithNotification(
        fullScreenPendingIntent: PendingIntent? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Rdc is executing command..."
                setBypassDnd(true)
                setShowBadge(true)
            }.also {
                getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(it)
            }
        }

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Executing Remote Command…")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)

        fullScreenPendingIntent?.let {
            Log.i(this.javaClass.name, "Setting full screen intent for notification")
            notif.setContentText("Tap me for photo")
            notif.addAction(
                R.drawable.ic_launcher_foreground,
                "Take Photo",
                it
            )
            notif.setFullScreenIntent(it, true)
            notif.setSilent(false)
            notif.setOngoing(false)
            notif.setAutoCancel(true)
        }
        startForeground(NOTIF_ID, notif.build())
    }

    private fun playAlertSound() {
        val alarmUri = getAlarmToneNamed("Beep") ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri) ?: return
        ringtone.isLooping = true

        val audioManager = getSystemService(AudioManager::class.java) ?: return
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }

        ringtone.play()
        Handler(Looper.getMainLooper()).postDelayed({
            if (ringtone.isPlaying) {
                ringtone.stop()
            }
        }, 30_000)
    }

    private fun getAlarmToneNamed(name: String): Uri? {
        val manager = RingtoneManager(this)
        manager.setType(RingtoneManager.TYPE_ALARM)

        val cursor = manager.cursor
        while (cursor.moveToNext()) {
            val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
            if (title.equals(name, ignoreCase = true)) {
                return manager.getRingtoneUri(cursor.position)
            }
        }
        return null
    }

    private fun setFlashlight(context: Context, turnOn: Boolean): String {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return "Permission for flashlight not granted"
        }

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