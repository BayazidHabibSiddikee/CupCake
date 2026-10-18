package com.cupcake.tts

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import java.io.File
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
    private var availableVoices: List<Voice> = emptyList()

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
            var triedFallback = false
            tts = TextToSpeech(context, { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    loadVoices()
                    setLanguage(Locale.ENGLISH)
                    applyChildishRobotVoice()
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) {
                            this@TtsManager.callback?.onStart(utteranceId)
                        }
                        override fun onDone(utteranceId: String) {
                            this@TtsManager.callback?.onDone(utteranceId)
                            activeStreams[utteranceId]?.invoke(true) // Signal done
                        }
                        override fun onError(utteranceId: String) {
                            this@TtsManager.callback?.onError(utteranceId, -1)
                            activeStreams[utteranceId]?.invoke(false) // Signal error
                        }
                    })
                    
                    Log.i("TtsManager", "TTS initialized successfully")
                } else if (!triedFallback) {
                    // Preferred engine ("com.google.android.tts") failed to bind — fall
                    // back to whatever the system default TTS is instead of staying mute.
                    triedFallback = true
                    Log.w("TtsManager", "Google TTS unavailable (status=$status); retrying with system default engine")
                    tts = TextToSpeech(context) { st ->
                        if (st == TextToSpeech.SUCCESS) {
                            isInitialized = true
                            loadVoices()
                            setLanguage(Locale.ENGLISH)
                            applyChildishRobotVoice()
                            Log.i("TtsManager", "TTS initialized via system default engine")
                        } else {
                            Log.e("TtsManager", "TTS initialization failed on fallback too: $st")
                        }
                    }
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

    private val activeStreams = java.util.concurrent.ConcurrentHashMap<String, (Boolean) -> Unit>()

    private fun loadVoices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            availableVoices = tts?.voices?.toList() ?: emptyList()
            Log.i("TtsManager", "Available voices: ${availableVoices.size}")
            availableVoices.forEach { v ->
                Log.d("TtsManager", "Voice: ${v.name}, Locale: ${v.locale}, Quality: ${v.quality}")
            }
        }
    }

    fun setLanguage(locale: Locale): Int {
        return tts?.setLanguage(locale) ?: TextToSpeech.LANG_MISSING_DATA
    }

    /**
     * The companion's signature tone: high-pitched childish + robotic beeps.
     * Baked in once at init so every synthesis (file + stream) gets it.
     */
    fun applyChildishRobotVoice() {
        tts?.setPitch(1.65f)   // high pitch — child-like
        tts?.setSpeechRate(1.15f) // slightly faster — snappy, robotic energy
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

    /**
     * TextToSpeech binds asynchronously — [initialize] returns before the engine is
     * really up. Callers should wait for [isInitialized] instead of hearing silence.
     */
    private suspend fun awaitInitialized(timeoutMs: Long = 5_000L): Boolean {
        var waited = 0L
        while ((!isInitialized || tts == null) && waited < timeoutMs) {
            kotlinx.coroutines.delay(50)
            waited += 50
        }
        return isInitialized && tts != null
    }

    fun speak(text: String): ReceiveChannel<ByteArray> = Channel<ByteArray>().apply {
        CoroutineScope(Dispatchers.IO).launch {
            if (!awaitInitialized()) {
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
            if (!awaitInitialized()) {
                close(Exception("TTS not initialized"))
                return@launch
            }

            val utteranceId = "utt_${System.currentTimeMillis()}"
            Log.i("TtsManager", "speakStreaming: ${text.take(40)}")
            val locale = autoDetectLanguage(text)
            tts?.setLanguage(locale)

            var isDone = false
            var hasError = false
            
            activeStreams[utteranceId] = { success ->
                if (success) isDone = true else hasError = true
            }

            try {
                val outFile = File.createTempFile(utteranceId, ".wav", context.cacheDir)
                try {
                    val params = android.os.Bundle().apply {
                        putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                    }
                    val status = tts?.synthesizeToFile(text, params, outFile, utteranceId)
                    if (status == TextToSpeech.SUCCESS) {
                        // Wait until the file is created by the TTS engine
                        while (!outFile.exists() && !isDone && !hasError) {
                            kotlinx.coroutines.delay(10)
                        }
                        
                        outFile.inputStream().use { input ->
                            val buffer = ByteArray(8192)
                            while (!isDone || input.available() > 0) {
                                val available = input.available()
                                if (available > 0) {
                                    val read = input.read(buffer, 0, minOf(buffer.size, available))
                                    if (read > 0) {
                                        onChunk(buffer.copyOf(read))
                                    }
                                } else {
                                    if (hasError) break
                                    kotlinx.coroutines.delay(10) // Wait for more data
                                }
                            }
                        }
                        close()
                    } else {
                        close(Exception("TTS synthesis failed: $status"))
                    }
                } finally {
                    activeStreams.remove(utteranceId)
                    outFile.delete()
                }
            } catch (e: Exception) {
                activeStreams.remove(utteranceId)
                close(e)
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

    fun getVoicesForLanguage(languageTag: String): List<Voice> {
        return availableVoices.filter { it.locale.toLanguageTag().startsWith(languageTag) }
    }

    fun setVoice(voice: Voice): Boolean {
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