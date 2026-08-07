package com.example.voiceassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restarts the foreground service after a device reboot, so the user never has to
 * manually reopen the app.
 *
 * Important limitation: a BroadcastReceiver can't request runtime permissions itself.
 * This only works if the user already granted mic/contacts/SMS/call/notification
 * permissions once through MainActivity — if they never opened the app and granted
 * those, this will fail silently (or the service will start with reduced capability,
 * since AssistantForegroundService's own permission checks in each handler will just
 * return "I need X permission" replies rather than crashing).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val serviceIntent = Intent(context, AssistantForegroundService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
