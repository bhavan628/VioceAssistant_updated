package com.example.voiceassistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Captures a single voice command as text, entirely headlessly (no Activity, no UI).
 *
 * Tradeoff note: this uses Android's on-device/system SpeechRecognizer, which is free
 * and needs no API key, but accuracy varies by device/OEM and it typically needs a
 * data connection on many phones for the best model (a few OEMs ship a fully offline
 * model — not guaranteed). If you need consistent accuracy across all devices, a cloud
 * STT API (Google Cloud Speech-to-Text, Whisper API) is worth the added latency, cost,
 * and API-key management. This class stays swappable — SttEngine is called the same
 * way from AssistantForegroundService either way.
 */
class SttEngine(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition not available on this device")
            return
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    if (text.isNullOrBlank()) {
                        onError("Didn't catch that")
                    } else {
                        onResult(text)
                    }
                }

                override fun onError(error: Int) {
                    onError("STT error code $error")
                }

                // Required overrides we don't need to act on:
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Gives more room for slower/paused speech before Android decides you're
            // done talking and cuts off — defaults are quite aggressive (~1-2s).
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000)
            // Keeps this fully headless — SpeechRecognizer works without any visible UI
            // when driven this way (unlike RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            // fired via startActivityForResult, which WOULD show the mic dialog).
        }
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
    }
}
