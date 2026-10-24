package com.cupcake.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cupcake.R
import com.cupcake.ai.SoundClassifier
import com.cupcake.chat.MessagingBus
import com.cupcake.stt.SpeechToTextManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * CupCake's "ear": captures microphone audio, runs YAMNet locally and turns
 * recognised sounds (music, crying, gunfire, laughter…) into companion
 * reactions. Long-running work lives in a foreground service.
 */
class SoundReactionService : Service() {

    private var audioRecord: AudioRecord? = null
    private var classifier: SoundClassifier? = null
    private var sttManager: SpeechToTextManager? = null
    @Volatile private var listening = false
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    @Volatile private var micPaused = false

    /** Per-category throttle so the companion doesn't repeat the same quip. */
    private val lastReactionAt = HashMap<String, Long>()
    private var lastLabel: String? = null
    /** Temporal smoothing: the same category must appear in N consecutive frames. */
    private var candidateCategory: String? = null
    private var candidateLabel: String? = null
    private var candidateHits = 0

    /**
     * Usually 2, but a single very-confident hit reacts immediately — one-off sounds
     * (a meow, a scream) often never repeat within a burst, and they were exactly the
     * categories that previously never fired.
     */
    private val requiredHits = 2
    private val strongHitConfidence = 0.6f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        classifier = SoundClassifier(this)
        sttManager = SpeechToTextManager(this)
        ensureChannel()

        // Forward STT transcripts to the chat engine
        scope.launch(Dispatchers.Main) {
            sttManager?.transcript?.collect { text ->
                if (!text.isNullOrBlank()) {
                    MessagingBus.sendUserMessage(text)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startListening()
            ACTION_STOP -> stopListening()
            else -> stopSelf()   // restarted without an action: don't linger
        }
        // User-driven service: never auto-restart from the background, where a
        // microphone FGS start is disallowed and would crash-loop.
        return START_NOT_STICKY
    }

    private fun startListening() {
        if (listening) return
        val clf = classifier
        if (clf == null || !clf.isReady) {
            Log.w(TAG, "YAMNet unavailable — cannot listen")
            stopSelf()
            return
        }

        val minBuf = AudioRecord.getMinBufferSize(
            SoundClassifier.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufBytes = maxOf(minBuf, SoundClassifier.SAMPLE_COUNT * 2)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SoundClassifier.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufBytes,
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission missing", e)
            stopSelf()
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialise")
            record.release()
            stopSelf()
            return
        }

        audioRecord = record
        record.startRecording()
        listening = true
        isListening = true
        try {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                buildNotification("Listening every 5 seconds…"),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        } catch (e: SecurityException) {
            // Started from the background — mic FGS isn't allowed there. Bail out
            // cleanly instead of crashing (the UI toggle starts us in foreground).
            Log.w(TAG, "Cannot start mic foreground service from background: ${e.message}")
            listening = false
            isListening = false
            runCatching { record.stop(); record.release() }
            audioRecord = null
            stopSelf()
            return
        }
        Log.i(TAG, "Listening started")

        scope.launch {
            val shorts = ShortArray(SoundClassifier.SAMPLE_COUNT)
            val floats = FloatArray(SoundClassifier.SAMPLE_COUNT)
            // Duty-cycled sampling: every [LISTEN_INTERVAL_MS] we take one short
            // listening window, classify it, then go quiet again — instead of
            // listening non-stop. Cheaper on battery and less "creepy always-on".
            var nextWindowAt = System.currentTimeMillis()
            while (listening) {
                val wait = nextWindowAt - System.currentTimeMillis()
                if (wait > 0) delay(wait)

                candidateCategory = null
                candidateHits = 0
                val windowEnd = System.currentTimeMillis() + LISTEN_WINDOW_MS
                var acted = false
                var frames = 0

                while (listening && !acted && System.currentTimeMillis() < windowEnd) {
                    if (micPaused) { delay(50); continue }
                    val read = record.read(shorts, 0, shorts.size)
                    if (read > 0) {
                        frames++
                        // Discard clipped silence so the classifier isn't fed pure noise.
                        // The threshold is low: quiet speech and meows still count as sound.
                        var peak = 0
                        for (i in 0 until read) {
                            val s = shorts[i].toInt()
                            if (s > peak) peak = s else if (-s > peak) peak = -s
                        }
                        if (peak > 480) {
                            for (i in 0 until read) floats[i] = shorts[i] / 32768f
                            val event = classifier?.classify(floats)
                            if (event != null) {
                                // Require the same category twice in a row before reacting —
                                // single-frame noise blips are ignored — except one very
                                // confident frame, which is acted on right away.
                                if (event.category == candidateCategory) {
                                    candidateHits++
                                } else {
                                    candidateCategory = event.category
                                    candidateLabel = event.label
                                    candidateHits = 1
                                }
                                if (candidateHits >= requiredHits || event.confidence >= strongHitConfidence) {
                                    handleEvent(event.category, event.label, event.confidence)
                                    acted = true
                                }
                            } else {
                                candidateCategory = null
                                candidateHits = 0
                            }
                        }
                    }
                    if (!acted) delay(150)
                }

                val now = System.currentTimeMillis()
                nextWindowAt += LISTEN_INTERVAL_MS
                // If we fell behind (device slept / long stall), snap forward so we
                // don't fire a burst of back-to-back windows.
                if (nextWindowAt <= now) nextWindowAt = now + LISTEN_INTERVAL_MS
                Log.i(TAG, "Listen window done (frames=$frames acted=$acted), next in ${nextWindowAt - now}ms")
            }
        }
    }

    private fun stopListening() {
        listening = false
        isListening = false
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
        stopForeground(true)
        stopSelf()
    }

    private fun handleEvent(category: String, label: String, confidence: Float) {
        val now = System.currentTimeMillis()
        // Same sound event within 8 s → ignore (debounce); different label → allow.
        if (label == lastLabel && now - (lastReactionAt[category] ?: 0L) < 8_000L) return
        if (now - (lastReactionAt[category] ?: 0L) < 8_000L) return

        val (emotion, quip) = when (category) {
            "music" -> "happy" to "Ooh, I like this beat! 💃"
            "crying" -> "sad" to "Aww… don't cry, I'm right here with you 🥺"
            "gunshot" -> "surprised" to "Whoa!! That was LOUD — you okay?! 😱"
            "laughter" -> "happy" to "Hehe, someone's having fun 😄"
            "scream" -> "surprised" to "Eep! What's happening?! 😱"
            "talk" -> {
                // Pick a talk quip by label so conversation, narration and singing differ.
                val line = when {
                    label.contains("sing") || label.contains("chant") -> "Sing more! I'm listening 🎤"
                    label.contains("speech") || label.contains("conversation") -> "Are you talking to me? 💬 Talk more, I'm listening!"
                    else -> "I heard your voice! 💬 Tell me more!"
                }
                // Trigger STT transcription for the next few seconds
                micPaused = true
                sttManager?.startListening()
                scope.launch { withTimeoutOrNull(45_000L) { while (sttManager?.isListening?.value == true) delay(100) } ?: Log.w(TAG, "STT timed out; resuming sound reactions"); micPaused = false }
                "happy" to line
            }
            "applause" -> "happy" to "Yay! Take a bow 🎉"
            "animal" -> "happy" to "Aww, a little friend! 🐾"
            "nature" -> "sleepy" to "Mmm, sounds cosy… 🌧️"
            "alert" -> "annoyed" to "That alarm again? hmph"
            else -> return
        }

        lastReactionAt[category] = now
        lastLabel = label
        Log.i(TAG, "Event $label ($category) ${"%.2f".format(confidence)} → $emotion")

        // Drive the companion: floating overlay + ESP face + spoken quip.
        com.cupcake.ime.PersonaEngine.triggerReaction(emotion, quip)
        reactOnCompanion(emotion, quip)
    }

    private fun reactOnCompanion(emotion: String, quip: String) {
        val ctx = applicationContext
        val asset = runCatching {
            val stem = com.cupcake.ai.EmotionAssets.espFaceForStem(emotion)
            com.cupcake.ai.EmotionAssets.assetForStem(stem)
        }.getOrNull()
        if (com.cupcake.service.ShimejiPetService.isRunning) {
            com.cupcake.service.ShimejiPetService.showSpeech(ctx, quip, asset)
        }
        // Speak the reaction out loud (gated by the voice toggle inside LocalSpeaker).
        runCatching { speaker.speak(quip) }
            .onFailure { Log.w(TAG, "voice reaction failed: ${it.message}") }
    }

    /**
     * The app-wide voice player from Hilt (same TTS singleton the chat/keyboard use).
     * Voice toggle gating lives inside LocalSpeaker.
     */
    private val speaker: com.cupcake.tts.LocalSpeaker by lazy {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            applicationContext,
            com.cupcake.ime.CompanionKeyboardService.CompanionKbEntryPoint::class.java
        ).localSpeaker()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Sound Reactions", NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CupCake is listening")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        super.onDestroy()
        listening = false
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        classifier?.close()
        sttManager?.destroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "SoundReactionService"
        private const val CHANNEL_ID = "sound_reaction"
        private const val NOTIF_ID = 42

        /** How long each listening burst stays open. */
        private const val LISTEN_WINDOW_MS = 2_500L

        /** Gap between listening bursts — she "checks in" every 5 seconds. */
        private const val LISTEN_INTERVAL_MS = 5_000L
        const val ACTION_START = "com.cupcake.action.START_LISTENING"
        const val ACTION_STOP = "com.cupcake.action.STOP_LISTENING"

        @Volatile var isListening = false
            private set
    }
}
