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
                    attachProgressListener()
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
                            // The progress listener is what signals activeStreams and
                            // unblocks speak()/speakStreaming() — without it the
                            // fallback engine synthesizes into a void and every
                            // caller hangs forever on isDone.
                            attachProgressListener()
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

    /**
     * One listener for both the preferred and the fallback engine. Overrides BOTH
     * onError deprecations: API 23+ engines call onError(String, Int) directly and
     * a listener that only overrides onError(String) never sees the failure, so
     * waiting callers would hang on `isDone` forever.
     */
    private fun attachProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                this@TtsManager.callback?.onStart(utteranceId)
            }
            override fun onDone(utteranceId: String) {
                this@TtsManager.callback?.onDone(utteranceId)
                activeStreams[utteranceId]?.invoke(true) // Signal done
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                this@TtsManager.callback?.onError(utteranceId, -1)
                activeStreams[utteranceId]?.invoke(false) // Signal error
            }
            override fun onError(utteranceId: String, errorCode: Int) {
                this@TtsManager.callback?.onError(utteranceId, errorCode)
                activeStreams[utteranceId]?.invoke(false) // Signal error
            }
        })
    }

    private val activeStreams = java.util.concurrent.ConcurrentHashMap<String, (Boolean) -> Unit>()

    // Mills-based IDs collide when speak() + speakStreaming() fire inside the same
    // millisecond (double fan-out); a counter makes every utterance unique.
    private val uttCounter = java.util.concurrent.atomic.AtomicInteger(0)

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
        tts?.setPitch(2.0f)   // Even higher pitch for "child/robotic" feel
        tts?.setSpeechRate(1.25f) // Snappier delivery
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
        val availability = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_MISSING_DATA
        return availability in intArrayOf(
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            // LANG_COUNTRY_VAR_AVAILABLE is a real "installed" result from many
            // engines (including Google TTS for specific voice variants like the
            // Bangla bn-BD voices); rejecting it silently dropped every Bangla voice.
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
        )
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

            val utteranceId = "utt_${uttCounter.incrementAndGet()}_${System.currentTimeMillis()}"
            
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

            val utteranceId = "utt_${uttCounter.incrementAndGet()}_${System.currentTimeMillis()}"
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
                // Hard ceiling per phase. A wedged engine (or a listener that never
                // got attached) must not block the voice channel forever — degrade
                // to an error channel instead of an infinite wait.
                val BOOT_TIMEOUT_MS = 15_000L
                val CHUNK_TIMEOUT_MS = 20_000L
                try {
                    val params = android.os.Bundle().apply {
                        putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                    }
                    val status = tts?.synthesizeToFile(text, params, outFile, utteranceId)
                    if (status == TextToSpeech.SUCCESS) {
                        // Wait until the file is created by the TTS engine
                        var bootWait = 0L
                        while (!outFile.exists() && !isDone && !hasError && bootWait < BOOT_TIMEOUT_MS) {
                            kotlinx.coroutines.delay(10)
                            bootWait += 10
                        }
                        if (!outFile.exists()) {
                            close(Exception("TTS engine never started synthesis"))
                            return@launch
                        }

                        outFile.inputStream().use { input ->
                            val buffer = ByteArray(8192)
                            var idleMs = 0L
                            while (!isDone || input.available() > 0) {
                                val available = input.available()
                                if (available > 0) {
                                    val read = input.read(buffer, 0, minOf(buffer.size, available))
                                    if (read > 0) {
                                        onChunk(buffer.copyOf(read))
                                        idleMs = 0
                                    }
                                } else {
                                    if (hasError) break
                                    if (isDone) break // file complete, reader drained
                                    kotlinx.coroutines.delay(10) // Wait for more data
                                    idleMs += 10
                                    if (idleMs > CHUNK_TIMEOUT_MS) break
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