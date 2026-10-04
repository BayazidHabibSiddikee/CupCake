package com.cupcake.tts

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.util.Locale

class TtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentLocale = Locale.ENGLISH
    private val banglaLocales = listOf(
        Locale("bn", "BD"),
        Locale("bn", "IN"),
        Locale.forLanguageTag("bn-BD"),
        Locale.forLanguageTag("bn-IN")
    )
    private val englishLocales = listOf(
        Locale.US,
        Locale.UK,
        Locale.forLanguageTag("en-US"),
        Locale.forLanguageTag("en-GB"),
        Locale.forLanguageTag("en-IN")
    )
    private var availableVoices: List<TextToSpeech.Voice> = emptyList()

    interface TtsCallback {
        fun onStart(utteranceId: String)
        fun onDone(utteranceId: String)
        fun onError(utteranceId: String, errorCode: Int)
        fun onAudioChunk(utteranceId: String, audioData: ByteArray, sampleRate: Int)
    }

    private var callback: TtsCallback? = null

    fun initialize(callback: TtsCallback? = null): Boolean {
        this.callback = callback
        return try {
            tts = TextToSpeech(context, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    loadVoices()
                    setLanguage(Locale.ENGLISH)
                    Log.i("TtsManager", "TTS initialized successfully")
                } else {
                    Log.e("TtsManager", "TTS initialization failed: $status")
                }
            }, "com.google.android.tts") // Prefer Google TTS
            true
        } catch (e: Exception) {
            Log.e("TtsManager", "TTS init error", e)
            false
        }
    }

    private fun loadVoices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            availableVoices = tts?.voices ?: emptyList()
            Log.i("TtsManager", "Available voices: ${availableVoices.size}")
            availableVoices.forEach { v ->
                Log.d("TtsManager", "Voice: ${v.name}, Locale: ${v.locale}, Quality: ${v.quality}")
            }
        }
    }

    fun setLanguage(locale: Locale): Int {
        return tts?.setLanguage(locale) ?: TextToSpeech.LANG_MISSING_DATA
    }

    fun autoDetectLanguage(text: String): Locale {
        val hasBangla = text.any { c -> c >= '\u0980' && c <= '\u09FF' } // Bengali Unicode block
        val hasDevanagari = text.any { c -> c >= '\u0900' && c <= '\u097F' } // Devanagari (Hindi)
        
        if (hasBangla) {
            // Try Bangla locales
            for (loc in banglaLocales) {
                if (isLanguageAvailable(loc)) {
                    currentLocale = loc
                    return loc
                }
            }
        }
        
        // Default to English
        for (loc in englishLocales) {
            if (isLanguageAvailable(loc)) {
                currentLocale = loc
                return loc
            }
        }
        
        return Locale.ENGLISH
    }

    private fun isLanguageAvailable(locale: Locale): Boolean {
        return tts?.isLanguageAvailable(locale) == TextToSpeech.LANG_AVAILABLE ||
               tts?.isLanguageAvailable(locale) == TextToSpeech.LANG_COUNTRY_AVAILABLE
    }

    fun speak(text: String): ReceiveChannel<ByteArray> = Channel<ByteArray>().apply {
        CoroutineScope(Dispatchers.IO).launch {
            if (!isInitialized || tts == null) {
                close(Exception("TTS not initialized"))
                return@launch
            }

            val utteranceId = "utt_${System.currentTimeMillis()}"
            
            // Auto-detect language
            val locale = autoDetectLanguage(text)
            tts?.setLanguage(locale)
            
            // Set up synthesis to file for streaming
            val params = android.os.Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts?.synthesizeToFile(text, params, null, utteranceId)
            } else {
                // Fallback for older API
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            }
            
            callback?.onStart(utteranceId)
        }
    }

    fun speakStreaming(text: String, onChunk: (ByteArray) -> Unit): ReceiveChannel<Unit> = Channel<Unit>().apply {
        CoroutineScope(Dispatchers.IO).launch {
            if (!isInitialized || tts == null) {
                close(Exception("TTS not initialized"))
                return@launch
            }

            val utteranceId = "utt_${System.currentTimeMillis()}"
            val locale = autoDetectLanguage(text)
            tts?.setLanguage(locale)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Use synthesis callback for streaming
                val synthesisCallback = object : TextToSpeech.SynthesisCallback {
                    override fun onAudioAvailable(audioData: ByteArray, offset: Int, length: Int) {
                        val chunk = audioData.copyOfRange(offset, offset + length)
                        onChunk(chunk)
                    }

                    override fun onDone() {
                        close()
                    }

                    override fun onError(errorCode: Int) {
                        close(Exception("TTS Error: $errorCode"))
                    }

                    override fun onStart() {
                        callback?.onStart(utteranceId)
                    }
                }

                tts?.synthesize(text, synthesisCallback, utteranceId)
            } else {
                // Legacy
                val params = android.os.Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                close()
            }
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    fun getAvailableLanguages(): List<String> {
        return availableVoices.map { it.locale.toLanguageTag() }.distinct()
    }

    fun getVoicesForLanguage(languageTag: String): List<TextToSpeech.Voice> {
        return availableVoices.filter { it.locale.toLanguageTag().startsWith(languageTag) }
    }

    fun setVoice(voice: TextToSpeech.Voice): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return tts?.setVoice(voice) == TextToSpeech.SUCCESS
        }
        return false
    }

    companion object {
        // Detect if text contains Bangla
        fun containsBangla(text: String): Boolean {
            return text.any { c -> c >= '\u0980' && c <= '\u09FF' }
        }

        fun containsDevanagari(text: String): Boolean {
            return text.any { c -> c >= '\u0900' && c <= '\u097F' }
        }
    }
}