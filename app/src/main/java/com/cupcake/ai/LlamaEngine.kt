package com.cupcake.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileOutputStream
import java.io.InputStream

class LlamaEngine private constructor() {

    companion object {
        private const val TAG = "LlamaEngine"
        /** Max time a queued generation waits for the previous turn to finish. */
        private const val BUSY_WAIT_MS = 45_000L
        /** Hard ceiling for a *running* generation before we cancel & fail it. */
        private const val GEN_WATCHDOG_MS = 90_000L
        private const val LIB_NAME = "llama_jni"
        private var _instance: LlamaEngine? = null
        private val lock = Any()

        @Volatile
        var isInitialized = false

        fun getInstance(): LlamaEngine {
            synchronized(lock) {
                if (_instance == null) {
                    _instance = LlamaEngine()
                }
                return _instance!!
            }
        }

        fun initialize(context: Context) {
            if (isInitialized) return
            System.loadLibrary(LIB_NAME)
            isInitialized = true
            Log.i(TAG, "Llama JNI loaded")
        }

        fun copyModelFromAssets(
            context: Context,
            fileName: String,
            onProgressBytes: ((copiedBytes: Long, totalBytes: Long) -> Unit)? = null
        ): File {
            val modelDir = File(context.filesDir, "models")
            modelDir.mkdirs()
            val modelFile = File(modelDir, File(fileName).name)

            if (modelFile.exists() && modelFile.length() > 0) {
                Log.i(TAG, "Model already exists: ${modelFile.length()} bytes")
                onProgressBytes?.invoke(modelFile.length(), modelFile.length())
                return modelFile
            }

            val totalBytes = try {
                context.assets.openFd(fileName).use { it.length }
            } catch (e: Exception) {
                -1L
            }
            Log.i(TAG, "Copying model from assets... (${totalBytes} bytes)")
            var copiedBytes = 0L
            var lastReport = 0L
            context.assets.open(fileName).use { input ->
                FileOutputStream(modelFile).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copiedBytes += read
                        if (copiedBytes - lastReport >= 8L * 1024 * 1024) {
                            lastReport = copiedBytes
                            onProgressBytes?.invoke(copiedBytes, totalBytes)
                        }
                    }
                }
            }
            onProgressBytes?.invoke(copiedBytes, totalBytes)
            Log.i(TAG, "Model copied: ${modelFile.length()} bytes")
            return modelFile
        }

        /** True when [file] begins with the 4-byte "GGUF" magic. */
        fun isValidGguf(file: File): Boolean {
            if (!file.exists() || file.length() < 8L) return false
            return try {
                file.inputStream().use { input ->
                    val magic = ByteArray(4)
                    input.read(magic) == 4 &&
                        magic[0] == 'G'.code.toByte() &&
                        magic[1] == 'G'.code.toByte() &&
                        magic[2] == 'U'.code.toByte() &&
                        magic[3] == 'F'.code.toByte()
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    // Native methods
    external fun initModel(modelPath: String, nCtx: Int, nThreads: Int, nBatch: Int): Int
    external fun setCallback(callback: GenerateCallback)
    external fun setSamplingParams(
        temp: Float, topP: Float, topK: Int,
        repeatPenalty: Float, repeatLastN: Int, seed: Int, maxTokens: Int
    )
    private external fun nativeSetGrammar(grammar: String?)

    external fun generate(prompt: String): Int
    external fun cancel()
    external fun release()
    external fun isLoaded(): Boolean
    external fun getModelInfo(): String

    interface GenerateCallback {
        fun onToken(token: String)
        fun onComplete(status: Int)
        fun onError(error: String)
    }

    data class Config(
        val modelFileName: String = "models/liltii-v0.2-v3-q4_0.gguf",
        val nCtx: Int = 2048,
        val nThreads: Int = recommendedThreadCount(),
        val nBatch: Int = 512,
        val maxTokens: Int = 128,
        val temperature: Float = 0.7f,
        val topP: Float = 0.9f,
        val topK: Int = 40,
        val repeatPenalty: Float = 1.1f,
        val repeatLastN: Int = 64,
        val seed: Int = -1,
        val grammar: String? = null
    )

    private var _isModelLoaded = false
    @Volatile
    private var _loadedModelName: String? = null

    /**
     * GBNF grammar applied to subsequent generations, or null for unconstrained decoding.
     * Kept as plain Kotlin state (not a native call itself) so tests and mocks never touch
     * the JNI: the value is pushed into the sampler in [generateStream] and [loadModel].
     */
    @Volatile
    var grammar: String? = null
        private set

    /**
     * Constrain decoding to a GBNF grammar (see [EmotionGrammar]). This is what turns the
     * companion's `[emotion] *action* reply` format from "usually" into "always": the
     * sampler masks every token that would break the grammar. Pass `null` to clear.
     */
    fun setGrammar(grammar: String?) {
        this.grammar = grammar
    }

    fun getLoadedModelName(): String? = _loadedModelName

    @Synchronized
    fun loadModel(
        context: Context,
        config: Config = Config(),
        onProgressBytes: ((copiedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): Result<Unit> {
        val modelFile = copyModelFromAssets(context, config.modelFileName, onProgressBytes)
        val targetName = modelFile.name

        // Guard against truncated/placeholder files: a non-GGUF file makes the
        // native loader fail with an opaque code (-1). Surface a clear message.
        if (!isValidGguf(modelFile)) {
            Log.e(TAG, "Model file is not a valid GGUF: ${modelFile.absolutePath} (${modelFile.length()} bytes)")
            modelFile.delete()
            return Result.failure(Exception("Model file is invalid"))
        }

        if (isReady() && _loadedModelName == targetName) {
            return Result.success(Unit)
        }

        if (isReady()) {
            unload()
        }

        return try {
            val result = initModel(
                modelFile.absolutePath,
                config.nCtx,
                config.nThreads,
                config.nBatch
            )
            if (result == 0) {
                _isModelLoaded = true
                _loadedModelName = targetName
                setSamplingParams(
                    config.temperature,
                    config.topP,
                    config.topK,
                    config.repeatPenalty,
                    config.repeatLastN,
                    config.seed,
                    config.maxTokens
                )
                setGrammar(config.grammar)
                nativeSetGrammar(config.grammar)
                Result.success(Unit)
            } else {
                Log.e(TAG, "initModel returned $result for $targetName")
                _loadedModelName = null
                Result.failure(Exception("Init failed"))
            }
        } catch (e: Exception) {
            _loadedModelName = null
            Result.failure(e)
        }
    }

    private val generateMutex = Mutex()

    /**
     * Streams a generation. When the engine is mid-turn (chat vs games vs warm-up)
     * we no longer drop the request with "Engine busy" — instead we [preempt]:
     * politely cancel the running native generation and wait for the slot
     * (bounded, so a wedged engine still surfaces an error instead of hanging).
     */
    fun generateStream(prompt: String, preempt: Boolean = false): ReceiveChannel<String> = Channel<String>(Channel.UNLIMITED).apply {
        CoroutineScope(Dispatchers.IO).launch {
            val locked = if (generateMutex.tryLock()) {
                true
            } else {
                if (preempt) runCatching { cancel() }
                try {
                    withTimeoutOrNull(BUSY_WAIT_MS) { generateMutex.lock(); true }
                } catch (_: IllegalStateException) {
                    false // mutex died with the owning scope
                } ?: false
            }
            if (!locked) {
                Log.w(TAG, "Engine still busy after ${BUSY_WAIT_MS}ms, dropping generation request.")
                close(Exception("Engine busy"))
                return@launch
            }
            // Native generate() returns immediately (it spawns a detached worker
            // thread) — the ONLY reliable completion signal is the callback, so we
            // await a deferred it completes, and release the mutex in finally so a
            // wedged/stopped native loop can never lock the engine forever.
            val done = CompletableDeferred<Unit>()
            try {
                val callback = object : GenerateCallback {
                    override fun onToken(token: String) {
                        trySend(token)
                    }

                    override fun onComplete(status: Int) {
                        done.complete(Unit)
                    }

                    override fun onError(error: String) {
                        done.complete(Unit)
                        close(Exception(error))
                    }
                }

                setCallback(callback)
                // Apply the current grammar right before decoding so callers can change it
                // per generation without reloading the model.
                nativeSetGrammar(grammar)
                val started = generate(prompt)
                if (started != 0) {
                    close(Exception("Generate failed: $started"))
                    return@launch
                }
                // Hard ceiling slightly above token budget: with maxTokens<=256 the
                // whole decode finishes in a few seconds on any phone; > this means
                // the native loop is wedged — cancel it and surface the error.
                val finished = withTimeoutOrNull(GEN_WATCHDOG_MS) { done.await() }
                if (finished == null) {
                    Log.e(TAG, "Generation watchdog: no onComplete in ${GEN_WATCHDOG_MS}ms, cancelling.")
                    runCatching { cancel() }
                    close(Exception("Generation timed out"))
                } else {
                    close()
                }
            } catch (e: Exception) {
                close(e)
            } finally {
                runCatching { if (generateMutex.isLocked) generateMutex.unlock() }
            }
        }
    }

    fun unload() {
        if (_isModelLoaded) {
            release()
            _isModelLoaded = false
            _loadedModelName = null
        }
    }

    fun isReady(): Boolean = isLoaded()

    fun getInfo(): String = getModelInfo()
}

/**
 * Picks a sensible CPU thread count for on-device inference.
 *
 * big.LITTLE phones report all cores, but only the "big" cluster actually helps a
 * tiny (135M-0.5B) model — scheduling onto slow LITTLE cores can *hurt* throughput.
 * We count the cores that share the highest max-frequency and clamp to [2, 6].
 */
fun recommendedThreadCount(): Int {
    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val big = performanceCoreCount()
    val threads = when {
        big in 1..cores -> big
        cores >= 8 -> cores / 2
        else -> cores
    }
    return threads.coerceIn(2, 6).coerceAtMost(cores)
}

private fun performanceCoreCount(): Int {
    val cores = Runtime.getRuntime().availableProcessors()
    val freqs = ArrayList<Long>()
    for (i in 0 until cores) {
        val f = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
        if (!f.exists()) continue
        val v = runCatching { f.readText().trim().toLong() }.getOrNull()
        if (v != null && v > 0) freqs.add(v)
    }
    if (freqs.isEmpty()) return -1
    val max = freqs.maxOrNull() ?: return -1
    return freqs.count { it >= max }
}
