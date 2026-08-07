package com.example.voiceassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class VoiceAssistantApp : Application() {

    companion object {
        const val CHANNEL_ID = "assistant_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Assistant Status",
                NotificationManager.IMPORTANCE_LOW // low = no sound/heads-up, just persistent
            ).apply {
                description = "Shows when the voice assistant is listening"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
