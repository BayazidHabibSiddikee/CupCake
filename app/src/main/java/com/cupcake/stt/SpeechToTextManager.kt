package com.cupcake.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
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
                    attempts++
                    languageIndex = (languageIndex + 1) % languagePreference.size
                    Log.i(TAG, "STT: transient error $error; retrying (attempt ${attempts + 1})")
                    startListening()
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
                    attempts++
                    languageIndex = (languageIndex + 1) % languagePreference.size
                    Log.w(TAG, "STT: Empty result — retrying with ${languagePreference[languageIndex]} (attempt $attempts/$maxAttempts)")
                    startListening()
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
        // Check RECORD_AUDIO permission before starting STT
        val context = context as? android.content.Context ?: return
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return
        }

        val lang = languagePreference[languageIndex]
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        _transcript.value = null
        attempts++
        recognizer?.startListening(intent)
            ?: Log.w(TAG, "STT: SpeechRecognizer unavailable on this device")
        Log.i(TAG, "STT: Listening (language=$lang, attempt=$attempts/$maxAttempts)")
    }

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
        recognizer?.destroy()
    }
}
