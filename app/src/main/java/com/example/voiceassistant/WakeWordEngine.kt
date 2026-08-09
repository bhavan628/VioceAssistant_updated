package com.example.voiceassistant

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * Offline wake-word detection using Vosk instead of Picovoice Porcupine.
 *
 * Why Vosk here: it's fully open-source (Apache 2.0), needs NO account, NO API key,
 * and NO login of any kind — you just bundle a model file. Good fit if Picovoice's
 * signup is blocking you for any reason.
 *
 * Honest tradeoff vs Porcupine: Vosk is a full speech-to-text engine, not a
 * lightweight keyword spotter. Running it continuously in the background uses
 * noticeably more CPU/battery than Porcupine's purpose-built wake-word model. It's a
 * fine choice to get something working without any signup step, but if battery life
 * becomes a real problem later, Porcupine (or Android's own on-device hotword APIs on
 * supported devices) is the more efficient long-term choice.
 *
 * One-time setup outside this code:
 *  1. Download a small Vosk model — e.g. "vosk-model-small-en-us-0.15" (~40MB) from
 *     https://alphacephei.com/vosk/models
 *  2. Unzip it and place the folder contents at:
 *     app/src/main/assets/model/  (so app/src/main/assets/model/README, /conf, etc.
 *     end up directly under assets/model/)
 *  3. No account, no key, nothing else needed.
 */
class WakeWordEngine(
    private val context: Context,
    private val wakePhrase: String,          // e.g. "hey assistant" — lowercase, no punctuation
    private val onWakeWordDetected: () -> Unit,
    private val onModelReady: (Model) -> Unit,
    private val onError: (String) -> Unit
) {
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognitionListener: RecognitionListener? = null

    fun start() {
        // Model loading + unpacking from assets happens off the calling thread via
        // Vosk's own StorageService helper.
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
                    // Restart listening if Vosk times out during silence.
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
            // Malformed/empty result — just ignore this cycle.
        }
    }

    /** Call before starting SpeechRecognizer — only one thing can hold the mic at a time.
     *  Fully shuts down (not just stops) so the AudioRecord is actually released;
     *  stop() alone leaves the hardware mic handle open on some devices, causing
     *  SpeechRecognizer's own mic request to fail right after. */
    fun pause() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
    }

    /** Call once a command has been fully handled, to resume wake-word listening.
     *  Recreates the recognizer/SpeechService from the already-unpacked model —
     *  no need to re-read from assets, so this is fast despite rebuilding. */
    fun resume() {
        model?.let { startListening(it) }
    }

    fun release() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        model = null
        recognitionListener = null
    }
}
