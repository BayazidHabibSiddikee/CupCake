package com.cupcake.service

import android.app.*
import android.content.*
import android.media.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cupcake.R
import com.cupcake.ai.SoundClassifier
import com.cupcake.ime.PersonaEngine
import kotlinx.coroutines.*

class SoundReactionService : Service() {
    private var audioRecord: AudioRecord? = null
    private var classifier: SoundClassifier? = null
    private var isListening = false
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    companion object {
        const val ACTION_TOGGLE_LISTEN = "com.cupcake.action.TOGGLE_LISTEN"
        var isListening = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        classifier = SoundClassifier(this)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE_LISTEN) {
            toggleListening()
        }
        return START_STICKY
    }

    private fun toggleListening() {
        isListening = !isListening
        SoundReactionService.isListening = isListening
        
        if (isListening) {
            startCapturing()
            startForegroundNotification("CupCake is listening for sounds...")
        } else {
            stopCapturing()
            stopForeground(true)
        }
    }

    private fun startCapturing() {
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            bufferSize
        )

        audioRecord?.startRecording()

        serviceScope.launch {
            val buffer = FloatArray(15600) // YAMNet frame size approx
            while (isListening) {
                // AudioRecord.read(FloatArray, ...) is not available in all Android versions.
                // Using FloatArray with a wrapper or converting from ShortArray.
                val shortBuffer = ShortArray(buffer.size)
                val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                if (read > 0) {
                    // Convert Short to Float normalized to [-1.0, 1.0]
                    for (i in 0 until read) {
                        buffer[i] = shortBuffer[i] / 32768.0f
                    }
                    val category = classifier?.classify(buffer)
                    if (category != null) {
                        handleSoundEvent(category)
                    }
                }
                delay(500) // Avoid CPU thrashing
            }
        }
    }

    private fun stopCapturing() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun handleSoundEvent(category: String) {
        Log.i("SoundReaction", "Detected sound: $category")
        val (emotion, quip) = when (category) {
            "music" -> "excited" to "I love this beat! 💃"
            "crying" -> "sad" to "Oh no! Don't cry, I'm here for you... 🥺"
            "gunshot" -> "surprised" to "Whoa! What was that loud noise?! 😱"
            "laughter" -> "happy" to "Hehehe! That's so funny!"
            else -> "neutral" to null
        }

        if (quip != null) {
            // Forward to the brain
            com.cupcake.ime.PersonaEngine.triggerReaction(emotion, quip)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "sound_reaction", "Sound Reactions",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification(text: String) {
        val notification = NotificationCompat.Builder(this, "sound_reaction")
            .setContentTitle("CupCake Ear")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapturing()
        classifier?.close()
        serviceScope.cancel()
    }
}
