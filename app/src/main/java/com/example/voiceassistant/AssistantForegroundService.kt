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

/**
 * Long-running foreground service. This is the skeleton for the v2 (full-feature)
 * build — the state machine already has slots for the intent classifier and command
 * execution steps that come later, so we don't have to restructure it each time.
 *
 * Wiring added in later steps:
 *  - Step 3: WakeWordEngine (Porcupine) in startWakeWordListening()/onWakeWordDetected()
 *  - Step 4: SpeechRecognizer + TextToSpeech in onWakeWordDetected()/onCommandTextReady()
 *  - Step 5: IntentClassifier in onCommandTextReady()
 *  - Step 6: individual command handlers in executeCommand()
 */
class AssistantForegroundService : Service() {

    enum class State {
        IDLE,
        LISTENING_FOR_WAKE_WORD,
        CAPTURING_COMMAND,     // SpeechRecognizer active
        CLASSIFYING,           // text -> intent category
        EXECUTING_COMMAND,     // running the matched handler
        SPEAKING                // TTS reply in progress
    }

    private var state: State = State.IDLE
    private var wakeWordEngine: WakeWordEngine? = null
    private var sttEngine: SttEngine? = null
    private var ttsEngine: TtsEngine? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("Assistant is starting..."))

        wakeWordEngine = WakeWordEngine(
            context = this,
            wakePhrase = "hello", // customize this to your chosen wake phrase, lowercase
            onWakeWordDetected = { onWakeWordDetected() },
            onError = { message -> updateNotification("Wake word engine error: $message") }
        )

        sttEngine = SttEngine(
            context = this,
            onResult = { text -> onCommandTextReady(text) },
            onError = { message ->
                // Couldn't capture a command — just go back to listening rather than
                // getting stuck in CAPTURING_COMMAND.
                updateNotification("Didn't catch that. Listening for wake word...")
                onCommandHandled()
            }
        )

        ttsEngine = TtsEngine(
            context = this,
            onReady = { /* engine warmed up, nothing to do yet */ },
            onSpeechFinished = { onCommandHandled() }
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

    // ---- State transitions ----

    private fun startWakeWordListening() {
        state = State.LISTENING_FOR_WAKE_WORD
        updateNotification("Listening for wake word...")
        wakeWordEngine?.start()
    }

    private fun stopWakeWordListening() {
        wakeWordEngine?.pause()
    }

    /** Fired by WakeWordEngine's callback (background thread) when the keyword is heard. */
    private fun onWakeWordDetected() {
        state = State.CAPTURING_COMMAND
        updateNotification("Yes? Listening for your command...")
        wakeWordEngine?.pause() // free the mic for TTS, then SpeechRecognizer
        // Vosk's stop()/shutdown() doesn't release the AudioRecord instantaneously —
        // starting anything mic-related immediately after can collide with it. The
        // delay handles that; speaking "Yes?" afterward gives an audible cue for when
        // to actually start talking, instead of silently starting to listen.
        serviceScope.launch {
            delay(700)
            ttsEngine?.speak("Yes?") {
                 serviceScope.launch {
                     sttEngine?.startListening()
                 }
            }
        }
    /** Called once SpeechRecognizer returns text. */
    private fun onCommandTextReady(commandText: String) {
        state = State.CLASSIFYING
        val result = IntentClassifier.classify(commandText)
        executeCommand(result.category, result.remainder)
    }

    /** Called once the classifier has picked a category. */
    private fun executeCommand(category: CommandCategory, remainder: String) {
        state = State.EXECUTING_COMMAND
        updateNotification("Working on it...")

        // NEWS needs a network call. NewsHandler is callback-based (OkHttp's own async
        // dispatch), and its callback fires on a background thread — hop back onto the
        // service's main-thread scope before touching TTS/state.
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
            CommandCategory.NEWS -> "" // unreachable, handled above
            CommandCategory.UNKNOWN -> "Sorry, I didn't understand that command."
        }
        state = State.SPEAKING
        ttsEngine?.speak(reply)
    }

    /** Called once the TTS reply finishes playing. */
    private fun onCommandHandled() {
        state = State.LISTENING_FOR_WAKE_WORD
        updateNotification("Listening for wake word...")
        wakeWordEngine?.resume()
    }

    // ---- Notification plumbing ----

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
            .setOngoing(true) // user cannot swipe it away while the service runs
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
