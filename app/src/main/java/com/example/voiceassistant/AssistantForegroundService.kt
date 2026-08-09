package com.example.voiceassistant

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.voiceassistant.handlers.AlarmHandler
import com.example.voiceassistant.handlers.CalculationHandler
import com.example.voiceassistant.handlers.CallHandler
import com.example.voiceassistant.handlers.LockScreenHandler
import com.example.voiceassistant.handlers.MusicHandler
import com.example.voiceassistant.handlers.MessageHandler
import com.example.voiceassistant.handlers.NewsHandler
import com.example.voiceassistant.handlers.OpenAppHandler
import com.example.voiceassistant.handlers.TimeHandler

class AssistantForegroundService : Service() {

    enum class State {
        IDLE,
        LISTENING_FOR_WAKE_WORD,
        CAPTURING_COMMAND,
        CLASSIFYING,
        EXECUTING_COMMAND,
        SPEAKING
    }

    private var state: State = State.IDLE
    private var wakeWordEngine: WakeWordEngine? = null
    private var sttEngine: SttEngine? = null
    private var ttsEngine: TtsEngine? = null
    private var sttRetried = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("Assistant is starting..."))

        ttsEngine = TtsEngine(
            context = this,
            onReady = { },
            onSpeechFinished = { onCommandHandled() }
        )

        wakeWordEngine = WakeWordEngine(
            context = this,
            wakePhrase = "hello",
            onWakeWordDetected = { onWakeWordDetected() },
            onModelReady = { model ->
                // Build SttEngine from the SAME loaded model WakeWordEngine just
                // unpacked — avoids a second, slower StorageService.unpack() call
                // every time a command is captured.
                sttEngine = SttEngine(
                    model = model,
                    onResult = { text ->
                        sttRetried = false
                        onCommandTextReady(text)
                    },
                    onError = { message ->
                        if (!sttRetried) {
                            sttRetried = true
                            updateNotification("Retrying...")
                            serviceScope.launch {
                                delay(400)
                                sttEngine?.startListening()
                            }
                        } else {
                            sttRetried = false
                            updateNotification("STT error: $message")
                            state = State.SPEAKING
                            ttsEngine?.speak("Sorry, I didn't catch that.")
                        }
                    }
                )
            },
            onError = { message -> updateNotification("Wake word engine error: $message") }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startWakeWordListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopWakeWordListening()
        wakeWordEngine?.release()
        sttEngine?.release()
        ttsEngine?.release()
        serviceScope.cancel()
    }

    private fun startWakeWordListening() {
        state = State.LISTENING_FOR_WAKE_WORD
        updateNotification("Listening for wake word...")
        wakeWordEngine?.start()
    }

    private fun stopWakeWordListening() {
        wakeWordEngine?.pause()
    }

    private fun onWakeWordDetected() {
        state = State.CAPTURING_COMMAND
        updateNotification("Yes? Listening for your command...")
        wakeWordEngine?.pause()
        serviceScope.launch {
            delay(700)
            ttsEngine?.speak("Yes?") {
                serviceScope.launch {
                    delay(1000)
                    sttEngine?.startListening()
                }
            }
        }
    }

    private fun onCommandTextReady(commandText: String) {
        state = State.CLASSIFYING
        val result = IntentClassifier.classify(commandText)
        executeCommand(result.category, result.remainder)
    }

    private fun executeCommand(category: CommandCategory, remainder: String) {
        state = State.EXECUTING_COMMAND
        updateNotification("Working on it...")

        if (category == CommandCategory.NEWS) {
            NewsHandler.handle(this) { reply ->
                serviceScope.launch {
                    state = State.SPEAKING
                    ttsEngine?.speak(reply)
                }
            }
            return
        }

        val reply = when (category) {
            CommandCategory.TIME -> TimeHandler.handle(remainder)
            CommandCategory.MUSIC -> MusicHandler.handle(this, remainder)
            CommandCategory.OPEN_APP -> OpenAppHandler.handle(this, remainder)
            CommandCategory.ALARM -> AlarmHandler.handle(this, remainder)
            CommandCategory.MESSAGE -> MessageHandler.handle(this, remainder)
            CommandCategory.CALL -> CallHandler.handle(this, remainder)
            CommandCategory.CALCULATION -> CalculationHandler.handle(remainder)
            CommandCategory.LOCK_SCREEN -> LockScreenHandler.handle(this)
            CommandCategory.NEWS -> ""
            CommandCategory.UNKNOWN -> "Sorry, I didn't understand that command."
        }
        state = State.SPEAKING
        ttsEngine?.speak(reply)
    }

    private fun onCommandHandled() {
        state = State.LISTENING_FOR_WAKE_WORD
        updateNotification("Listening for wake word...")
        wakeWordEngine?.resume()
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, VoiceAssistantApp.CHANNEL_ID)
            .setContentTitle("Voice Assistant")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
