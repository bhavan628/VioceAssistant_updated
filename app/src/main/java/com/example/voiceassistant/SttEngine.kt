package com.example.voiceassistant

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

class SttEngine(
    private val model: Model,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var capture: GainBoostedRecognizer? = null

    fun startListening() {
        try {
            val recognizer = Recognizer(model, 16000.0f)
            capture = GainBoostedRecognizer(
                recognizer = recognizer,
                gain = 3.0f,
                onResult = { json -> handleFinal(json) },
                onError = { message -> onError(message) }
            )
            capture?.start()
        } catch (e: Exception) {
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
            capture?.stop()
            capture = null
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
        capture?.stop()
    }

    fun release() {
        capture?.stop()
        capture = null
    }
}
