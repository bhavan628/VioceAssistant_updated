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
import com.example.voiceassistant.handlers.SearchHandler
import com.example.voiceassistant.handlers.TimeHandler
import com.example.voiceassistant.handlers.TypeHandler

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
                sttEngine = SttEngine(
                    model = model,
                    onResult = { text -> onCommandTextReady(text) },
                    onError = {
                        // No spoken failure message anymore — just silently go back
                        // to listening for the wake word.
                        if (state == State.CAPTURING_COMMAND) {
                            state = State.LISTENING_FOR_WAKE_WORD
                            updateNotification("Listening for wake word...")
                            wakeWordEngine?.resume()
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

    /** No "Yes?" prompt anymore — goes straight from wake word to listening for the
     *  command. Waits up to 4 seconds for a command; if nothing comes in, silently
     *  stops listening and returns to waiting for the wake word — no spoken message. */
    private fun onWakeWordDetected() {
        state = State.CAPTURING_COMMAND
        updateNotification("Listening for your command...")
        wakeWordEngine?.pause()
        serviceScope.launch {
            delay(200) // mic handoff - faster now with direct AudioRecord capture
            sttEngine?.startListening()

            delay(4000) // 4-second window to give a command
            if (state == State.CAPTURING_COMMAND) {
                sttEngine?.stopListening()
                state = State.LISTENING_FOR_WAKE_WORD
                updateNotification("Listening for wake word...")
                wakeWordEngine?.resume()
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

        if (category == CommandCategory.OPEN_APP) {
            // Opens quietly — no spoken confirmation, just perform the action and go
            // straight back to listening for the wake word. Note: this means
            // failures (app not found) are also silent, not just successes.
            OpenAppHandler.handle(this, remainder)
            onCommandHandled()
            return
        }

        if (category == CommandCategory.UNKNOWN) {
            // Silent by request: the app can't distinguish "wake word falsely
            // triggered, then picked up unrelated noise/speech" from "wake word was
            // real, but the command genuinely wasn't recognized" — same code path
            // either way. Since a false trigger should never announce itself, both
            // cases now stay completely silent rather than speaking "Sorry".
            onCommandHandled()
            return
        }

        val reply = when (category) {
            CommandCategory.TIME -> TimeHandler.handle(remainder)
            CommandCategory.MUSIC -> MusicHandler.handle(this, remainder)
            CommandCategory.OPEN_APP -> "" // unreachable, handled above
            CommandCategory.ALARM -> AlarmHandler.handle(this, remainder)
            CommandCategory.MESSAGE -> MessageHandler.handle(this, remainder)
            CommandCategory.CALL -> CallHandler.handle(this, remainder)
            CommandCategory.CALCULATION -> CalculationHandler.handle(remainder)
            CommandCategory.LOCK_SCREEN -> LockScreenHandler.handle(this)
            CommandCategory.SEARCH -> SearchHandler.handle(this, remainder)
            CommandCategory.TYPE -> TypeHandler.handle(remainder)
            CommandCategory.NEWS -> "" // unreachable, handled above
            CommandCategory.UNKNOWN -> "" // unreachable, handled above
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
