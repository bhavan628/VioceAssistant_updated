package com.example.voiceassistant

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService

class WakeWordEngine(
    private val context: Context,
    private val wakePhrase: String,
    private val onWakeWordDetected: () -> Unit,
    private val onModelReady: (Model) -> Unit,
    private val onError: (String) -> Unit
) {
    private var model: Model? = null
    private var capture: GainBoostedRecognizer? = null
    private var listeningEnabled = false

    fun start() {
        StorageService.unpack(
            context, "model", "model",
            { unpackedModel ->
                model = unpackedModel
                onModelReady(unpackedModel)
                startListening(unpackedModel)
            },
            { exception ->
                Log.e("WakeWordEngine", "Failed to unpack Vosk model", exception)
                onError(exception.message ?: "Failed to load wake-word model")
            }
        )
    }

    private fun startListening(model: Model) {
        listeningEnabled = false
        try {
            val recognizer = Recognizer(model, 16000.0f)
            capture = GainBoostedRecognizer(
                recognizer = recognizer,
                gain = 3.0f,
                onResult = { json -> checkForWakePhrase(json) },
                onError = { message -> onError(message) }
            )
            capture?.start()
            android.os.Handler(context.mainLooper).postDelayed({ listeningEnabled = true }, 1200)
        } catch (e: Exception) {
            Log.e("WakeWordEngine", "Failed to start Vosk recognizer", e)
            onError(e.message ?: "Unknown Vosk error")
        }
    }

    private fun checkForWakePhrase(hypothesisJson: String?) {
        if (hypothesisJson == null || !listeningEnabled) return
        try {
            val text = JSONObject(hypothesisJson).optString("text", "")
            if (text.lowercase().contains(wakePhrase)) {
                onWakeWordDetected()
            }
        } catch (e: Exception) {
        }
    }

    fun pause() {
        capture?.stop()
        capture = null
    }

    fun resume() {
        model?.let { startListening(it) }
    }

    fun release() {
        capture?.stop()
        capture = null
        model = null
    }
}
