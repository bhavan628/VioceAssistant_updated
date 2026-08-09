package com.example.voiceassistant.handlers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Launches an activity Intent from the background service. Sending via PendingIntent
 * is treated more leniently by Android 10+'s Background Activity Launch restriction
 * than a raw context.startActivity() call made from a long-running service — plain
 * startActivity() was being silently dropped by the OS with zero error, across every
 * handler that opens something (apps, calls, alarms, WhatsApp, music apps).
 */
object ActivityLauncher {

    fun launch(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent.send()
        } catch (e: PendingIntent.CanceledException) {
            context.startActivity(intent)
        }
    }
}
