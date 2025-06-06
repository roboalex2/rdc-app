package at.roboalex2.rdc.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log

class SmsCommandReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (msg in messages) {
            val sender = msg.originatingAddress ?: continue
            val body   = msg.messageBody ?: continue

            // Build intent for your Service
            Log.i(this.javaClass.name, "Received SMS from $sender: $body")
            val svcIntent = Intent(ctx, SmsProcessingService::class.java).apply {
                putExtra(SmsProcessingService.EXTRA_SENDER, sender)
                putExtra(SmsProcessingService.EXTRA_BODY, body)
            }

            // Start as foreground service on O+ to avoid background limits
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startService(svcIntent)
            } else {
                ctx.startService(svcIntent)
            }
        }
    }
}