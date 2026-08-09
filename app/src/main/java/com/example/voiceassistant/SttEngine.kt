package com.example.voiceassistant

import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Captures a single voice command as text using Vosk.
 *
 * Takes an already-loaded Model (shared from WakeWordEngine, which unpacks it once at
 * startup) instead of calling StorageService.unpack() itself — that call was
 * asynchronous and added unpredictable extra delay every time a command was
 * captured, meaning actual mic listening often started after the caller had already
 * finished speaking. Building the Recognizer/SpeechService from an in-memory Model is
 * synchronous and effectively instant.
 */
class SttEngine(
    private val model: Model,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var speechService: SpeechService? = null

    fun startListening() {
        try {
            val recognizer = Recognizer(model, 16000.0f)
            val listener = object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                }

                override fun onResult(hypothesis: String?) {
                    handleFinal(hypothesis)
                }

                override fun onFinalResult(hypothesis: String?) {
                    handleFinal(hypothesis)
                }

                override fun onError(exception: Exception?) {
                    onError(exception?.message ?: "Vosk STT error")
                }

                override fun onTimeout() {
                    onError("No speech detected")
                }
            }
            speechService = SpeechService(recognizer, 16000.0f).apply {
                startListening(listener)
            }
        } catch (e: Exception) {
            Log.e("SttEngine", "Failed to start Vosk recognizer", e)
            onError(e.message ?: "Unknown speech recognition error")
        }
    }

    private fun handleFinal(hypothesisJson: String?) {
        if (hypothesisJson == null) {
            onError("No speech detected")
            return
        }
        try {
            val text = JSONObject(hypothesisJson).optString("text", "")
            speechService?.stop()
            speechService?.shutdown()
            speechService = null
            if (text.isBlank()) {
                onError("No speech detected")
            } else {
                onResult(text)
            }
        } catch (e: Exception) {
            onError("Couldn't read speech result")
        }
    }

    fun stopListening() {
        speechService?.stop()
    }

    fun release() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
    }
}
