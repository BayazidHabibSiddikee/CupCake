package com.cupcake.stt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge between Android's SpeechRecognizer and CupCake's chat engine.
 * Handles the lifecycle of a single transcription burst.
 *
 * Reliability notes:
 * - [EXTRA_PARTIAL_RESULTS] is enabled so live transcripts are captured.
 * - [onPartialResults] updates the transcript as speech is heard.
 * - [onResults] prefers the best match, falls back to the live partial transcript
 *   when the engine returns no matches, and never wipes a good transcript.
 * - [onError] preserves the transcript so a recognition failure does not lose text.
 * - Languages are tried in [languagePreference] order (Bangla → Bn → English)
 *   with a bounded auto-retry when a burst yields nothing.
 */
class SpeechToTextManager(
    private val context: Context,
    private val recognizer: SpeechRecognizer? = SpeechRecognizer.createSpeechRecognizer(context),
) {
    private val TAG = "CupCakeSTT"

    /** Preferred recognition languages, tried in order: Bangla (BD) → Bangla → English. */
    val languagePreference: List<String> = listOf("bn-BD", "bn", "en")

    /** Index into [languagePreference] used by the next [startListening] burst. */
    private var languageIndex = 0
    /** Recognition bursts attempted for the current utterance (bounds the auto-retry). */
    private var attempts = 0
    private val maxAttempts: Int get() = languagePreference.size

    private val _transcript = MutableStateFlow<String?>(null)
    val transcript: StateFlow<String?> = _transcript.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    /** Main-looper handler used to space out recognition retries. */
    private val mainHandler = Handler(Looper.getMainLooper())

    private companion object {
        const val RETRY_DELAY_MS = 600L
    }

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "STT: Ready for speech")
                _isListening.value = true
            }
            override fun onBeginningOfSpeech() { Log.d(TAG, "STT: Beginning of speech") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.d(TAG, "STT: End of speech")
                _isListening.value = false
            }
            override fun onError(error: Int) {
                Log.e(TAG, "STT: Error occurred: $error")
                _isListening.value = false
                // Never wipe the transcript on error — preserve whatever was captured.
                val transient = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                if (transient && _transcript.value.isNullOrBlank() && attempts < maxAttempts - 1) {
                    Log.i(TAG, "STT: transient error $error; scheduling retry")
                    scheduleRetry()
                    return
                }
                attempts = 0
                languageIndex = 0
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                val text = matches.firstOrNull()?.takeIf { it.isNotBlank() }
                _isListening.value = false

                if (text != null) {
                    _transcript.value = text
                    attempts = 0
                    languageIndex = 0
                    Log.i(TAG, "STT: Result: $text")
                    return
                }

                // Empty-fallback: a live partial transcript is better than nothing — keep it.
                if (_transcript.value?.isNotBlank() == true) {
                    Log.i(TAG, "STT: No final match; preserving partial transcript '${_transcript.value}'")
                    attempts = 0
                    languageIndex = 0
                    return
                }

                // Nothing captured: bounded auto-retry with the next preferred language.
                if (attempts < maxAttempts - 1) {
                    Log.w(TAG, "STT: Empty result — scheduling retry")
                    scheduleRetry()
                    return
                }

                attempts = 0
                languageIndex = 0
                Log.w(TAG, "STT: No recognition results after $maxAttempts attempts; transcript preserved")
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                val text = matches.firstOrNull()?.takeIf { it.isNotBlank() }
                if (text != null) {
                    Log.d(TAG, "STT: Partial: $text")
                    _transcript.value = text
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d(TAG, "STT: Event $eventType")
            }
        })
    }

    fun startListening() {
        if (!hasRecordAudioPermission()) {
            Log.e(TAG, "RECORD_AUDIO permission not granted; cannot start recognition")
            return
        }
        // New utterance: restart the language/attempt ladder from the top.
        attempts = 0
        startBurst()
    }

    /** Fires one recognition burst using the current [languageIndex]; never mutates attempt state. */
    private fun startBurst() {
        val lang = languagePreference[languageIndex]
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        // Only a fresh utterance clears stale text; retries keep whatever was captured.
        if (attempts == 0) _transcript.value = null
        val engine = recognizer
        if (engine == null) {
            Log.w(TAG, "STT: SpeechRecognizer unavailable on this device")
            return
        }
        _isListening.value = true
        engine.startListening(intent)
        Log.i(TAG, "STT: Listening (language=$lang, attempt=${attempts + 1}/$maxAttempts)")
    }

    /**
     * Restarts recognition on the next preferred language after a short delay.
     * Posting through the main looper gives the engine time to leave a busy/error
     * state; calling startListening() from inside onError is what produces
     * repeat ERROR_RECOGNIZER_BUSY on device.
     */
    private fun scheduleRetry() {
        if (attempts >= maxAttempts - 1) {
            attempts = 0
            languageIndex = 0
            Log.w(TAG, "STT: Retry budget exhausted after $maxAttempts attempts")
            return
        }
        attempts++
        languageIndex = (languageIndex + 1) % languagePreference.size
        val lang = languagePreference[languageIndex]
        Log.i(TAG, "STT: Retrying in ${RETRY_DELAY_MS}ms with $lang (attempt ${attempts + 1}/$maxAttempts)")
        mainHandler.postDelayed({
            if (hasRecordAudioPermission()) startBurst()
        }, RETRY_DELAY_MS)
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun stopListening() {
        recognizer?.stopListening()
        _isListening.value = false
    }

    fun clearTranscript() {
        _transcript.value = null
        attempts = 0
        languageIndex = 0
    }

    fun cycleLanguage() {
        languageIndex = (languageIndex + 1) % languagePreference.size
    }

    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
    }
}
