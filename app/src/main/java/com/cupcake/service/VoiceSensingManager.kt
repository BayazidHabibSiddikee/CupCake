package com.cupcake.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.cupcake.chat.MessagingBus
import com.cupcake.stt.SpeechToTextManager
import com.cupcake.tts.LocalSpeaker
import androidx.lifecycle.DefaultLifecycleObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VoiceSensingManager implements the "Hearing" property (Folax-style).
 * It owns a single shared AudioRecord stream and coordinates between 
 * Voice Activity Detection (VAD) and Speech-to-Text (STT).
 * 
 * Pipeline: AudioRecord -> VAD -> buffer speech -> silence detected -> STT -> sendMessage().
 */
@Singleton
class VoiceSensingManager @Inject constructor(
    private val context: Context,
    private val sttManager: SpeechToTextManager,
    private val speaker: LocalSpeaker
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var audioRecord: AudioRecord? = null
    private val isListening = AtomicBoolean(false)
    
    companion object {
        private const val TAG = "VoiceSensingMgr"
        private const val SAMPLE_RATE = 16000
        private const val SILENCE_THRESHOLD_MS = 700L
        private const val VAD_ENERGY_THRESHOLD = 500 // Adjusted based on typical room noise
    }

    fun startHearing() {
        if (isListening.get()) return
        isListening.set(true)
        
        scope.launch {
            try {
                setupAudio()
                runSensingLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Hearing pipeline failed: ${e.message}", e)
                stopHearing()
            }
        }
    }

    fun stopHearing() {
        isListening.set(false)
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
    }

    private fun setupAudio() {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, 4096)
        ).apply {
            if (state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord failed to initialize")
            }
            startRecording()
        }
        Log.i(TAG, "Shared AudioRecord initialized and started")
    }

    private suspend fun runSensingLoop() {
        val buffer = ShortArray(1024)
        var isSpeaking = false
        var silenceStart = System.currentTimeMillis()

        while (isListening.get()) {
            // 1. Echo Muting: If CupCake is speaking, ignore the mic
            if (speaker.isSpeaking) {
                // Drain the buffer so we don't process old audio when she stops
                audioRecord?.read(buffer, 0, buffer.size)
                delay(100)
                continue
            }

            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (read <= 0) {
                delay(10)
                continue
            }

            // 2. Simple Energy-based VAD (Placeholder for Silero/WebRTC)
            var peak: Int = 0
            for (i in 0 until read) {
                val s = buffer[i].toInt()
                val absVal = if (s < 0) -s else s
                if (absVal > peak) peak = absVal
            }

            if (peak > VAD_ENERGY_THRESHOLD) {
                if (!isSpeaking) {
                    Log.d(TAG, "Voice detected (peak=$peak)")
                    isSpeaking = true
                }
                silenceStart = System.currentTimeMillis()
            } else {
                if (isSpeaking) {
                    if (System.currentTimeMillis() - silenceStart > SILENCE_THRESHOLD_MS) {
                        Log.i(TAG, "Silence detected. Triggering STT...")
                        triggerStt()
                        isSpeaking = false
                    }
                }
            }
        }
    }

    private fun triggerStt() {
        scope.launch(Dispatchers.Main) {
            try {
                // Note: In a full implementation, we'd feed the buffered PCM 
                // directly to the STT engine. For now, we trigger the bridge.
                sttManager.startListening()
                
                // Collect the resulting transcript and send it to the AI
                sttManager.transcript.collectLatest { text ->
                    if (!text.isNullOrBlank()) {
                        MessagingBus.sendUserMessage(text)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "STT trigger failed: ${e.message}")
            }
        }
    }
}
