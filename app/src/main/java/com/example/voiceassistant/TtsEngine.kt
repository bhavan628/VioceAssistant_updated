package com.example.voiceassistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Speaks a reply aloud and reports back when it's done, so the service knows when it's
 * safe to resume wake-word listening. No UI involved — TextToSpeech plays through the
 * device's normal audio output regardless of screen state.
 */
class TtsEngine(
    context: Context,
    private val onReady: () -> Unit,
    private val onSpeechFinished: () -> Unit
) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                isReady = true
                onReady()
            }
        }
    }

    fun speak(text: String) {
        if (!isReady) return
        val utteranceId = UUID.randomUUID().toString()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                onSpeechFinished()
            }
            @Deprecated("Deprecated in API 21+, required for older devices")
            override fun onError(utteranceId: String?) {
                onSpeechFinished()
            }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
