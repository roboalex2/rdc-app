package at.roboalex2.rdc.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat

object SmsSenderService {

    fun sendReply(ctx: Context, to: String, text: String) {
        if (ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("SmsSenderService", "SEND_SMS permission not granted")
            return
        }

        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
            ctx.getSystemService(SmsManager::class.java)
                .createForSubscriptionId(subId)
        } else {
            SmsManager.getDefault()
        }
        smsManager.sendTextMessage(to, null, text, null, null)
    }
}