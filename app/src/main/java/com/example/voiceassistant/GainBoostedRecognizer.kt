package com.example.voiceassistant

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.vosk.Recognizer
import kotlin.concurrent.thread

/**
 * Captures the microphone directly instead of using Vosk's built-in SpeechService
 * wrapper, specifically so quiet/soft speech can be amplified in software before
 * recognition — Vosk's own wrapper doesn't expose any gain or sensitivity setting,
 * so this is the actual lever available for "can't hear me when I speak softly".
 *
 * Not a guaranteed fix — very quiet speech below the mic's noise floor can't be
 * recovered by amplification alone — but it should meaningfully help speech that's
 * quiet-but-audible, which is the common case.
 */
class GainBoostedRecognizer(
    private val recognizer: Recognizer,
    private val gain: Float = 3.0f,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    @Volatile private var running = false
    private var captureThread: Thread? = null

    companion object {
        private const val SAMPLE_RATE = 16000
    }

    fun start() {
        try {
            val minBufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = maxOf(minBufSize, 4096)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError("Couldn't initialize microphone")
                return
            }
            running = true
            audioRecord?.startRecording()
            captureThread = thread(start = true) { captureLoop(bufSize) }
        } catch (e: SecurityException) {
            onError("Microphone permission not granted")
        } catch (e: Exception) {
            onError(e.message ?: "Failed to start audio capture")
        }
    }

    private fun captureLoop(bufSize: Int) {
        val buffer = ShortArray(bufSize / 2)
        while (running) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
            if (read > 0) {
                // Software gain with clipping protection — boosts quiet speech
                // before Vosk ever sees it.
                for (i in 0 until read) {
                    val boosted = (buffer[i] * gain).toInt()
                    buffer[i] = boosted.coerceIn(-32768, 32767).toShort()
                }
                val bytes = shortsToBytes(buffer, read)
                try {
                    val isFinal = recognizer.acceptWaveForm(bytes, bytes.size)
                    if (isFinal) {
                        onResult(recognizer.result)
                    }
                } catch (e: Exception) {
                    if (running) onError(e.message ?: "Recognition error")
                }
            }
        }
    }

    private fun shortsToBytes(shorts: ShortArray, length: Int): ByteArray {
        val bytes = ByteArray(length * 2)
        for (i in 0 until length) {
            val v = shorts[i].toInt()
            bytes[i * 2] = (v and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    fun stop() {
        running = false
        try {
            captureThread?.join(500)
        } catch (e: InterruptedException) {
        }
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
        }
        audioRecord?.release()
        audioRecord = null
    }
}
