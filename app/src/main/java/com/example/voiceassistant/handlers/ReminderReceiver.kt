package com.example.voiceassistant.handlers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Fires when a silent reminder set via AlarmHandler.scheduleSilentReminder() triggers.
 * Speaks the reminder aloud with a throwaway TTS instance — no Activity, no Clock app.
 * Only relevant if you use the silent-reminder path instead of the AlarmClock intent
 * path in AlarmHandler.handle().
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.speak("This is your reminder", TextToSpeech.QUEUE_FLUSH, null, "reminder")
            }
        }
    }
}
