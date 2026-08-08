package com.example.voiceassistant

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

class WakeWordEngine(
    private val context: Context,
    private val wakePhrase: String,
    private val onWakeWordDetected: () -> Unit,
    private val onError: (String) -> Unit
) {
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognitionListener: RecognitionListener? = null

    fun start() {
        StorageService.unpack(
            context, "model", "model",
            { unpackedModel ->
                model = unpackedModel
                startListening(unpackedModel)
            },
            { exception ->
                Log.e("WakeWordEngine", "Failed to unpack Vosk model", exception)
                onError(exception.message ?: "Failed to load wake-word model")
            }
        )
    }

    private fun startListening(model: Model) {
        try {
            val recognizer = Recognizer(model, 16000.0f)
            val listener = object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    checkForWakePhrase(hypothesis)
                }

                override fun onResult(hypothesis: String?) {
                    checkForWakePhrase(hypothesis)
                }

                override fun onFinalResult(hypothesis: String?) {
                    checkForWakePhrase(hypothesis)
                }

                override fun onError(exception: Exception?) {
                    onError(exception?.message ?: "Vosk recognition error")
                }

                override fun onTimeout() {
                    recognitionListener?.let { speechService?.startListening(it) }
                }
            }
            recognitionListener = listener
            speechService = SpeechService(recognizer, 16000.0f).apply {
                startListening(listener)
            }
        } catch (e: Exception) {
            Log.e("WakeWordEngine", "Failed to start Vosk recognizer", e)
            onError(e.message ?: "Unknown Vosk error")
        }
    }

    private fun checkForWakePhrase(hypothesisJson: String?) {
        if (hypothesisJson == null) return
        try {
            val text = JSONObject(hypothesisJson).optString("text", "")
                .ifBlank { JSONObject(hypothesisJson).optString("partial", "") }
            if (text.lowercase().contains(wakePhrase)) {
                onWakeWordDetected()
            }
        } catch (e: Exception) {
        }
    }

    fun pause() {
        speechService?.stop()
    }

    fun resume() {
        recognitionListener?.let { speechService?.startListening(it) }
    }

    fun release() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        model = null
        recognitionListener = null
    }
}
