package com.cupcake.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.sync.Mutex
import java.io.FileOutputStream
import java.io.InputStream

class LlamaEngine private constructor() {

    companion object {
        private const val TAG = "LlamaEngine"
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
    }

    // Native methods
    external fun initModel(modelPath: String, nCtx: Int, nThreads: Int, nBatch: Int): Int
    external fun setCallback(callback: GenerateCallback)
    external fun setSamplingParams(
        temp: Float, topP: Float, topK: Int,
        repeatPenalty: Float, repeatLastN: Int, seed: Int, maxTokens: Int
    )
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
        val modelFileName: String = "models/liltii-v0.2-small-q2_k.gguf",
        val nCtx: Int = 2048,
        val nThreads: Int = recommendedThreadCount(),
        val nBatch: Int = 512,
        val maxTokens: Int = 256,
        val temperature: Float = 0.7f,
        val topP: Float = 0.9f,
        val topK: Int = 40,
        val repeatPenalty: Float = 1.1f,
        val repeatLastN: Int = 64,
        val seed: Int = -1
    )

    private var _isModelLoaded = false
    @Volatile
    private var _loadedModelName: String? = null

    fun getLoadedModelName(): String? = _loadedModelName

    @Synchronized
    fun loadModel(
        context: Context,
        config: Config = Config(),
        onProgressBytes: ((copiedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): Result<Unit> {
        val modelFile = copyModelFromAssets(context, config.modelFileName, onProgressBytes)
        val targetName = modelFile.name

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
                Result.success(Unit)
            } else {
                _loadedModelName = null
                Result.failure(Exception("Init failed: $result"))
            }
        } catch (e: Exception) {
            _loadedModelName = null
            Result.failure(e)
        }
    }

    private val generateMutex = Mutex()

    fun generateStream(prompt: String): ReceiveChannel<String> = Channel<String>(Channel.UNLIMITED).apply {
        CoroutineScope(Dispatchers.IO).launch {
            if (!generateMutex.tryLock()) {
                Log.w(TAG, "Engine busy, dropping generation request.")
                close(Exception("Engine busy"))
                return@launch
            }
            try {
                val callback = object : GenerateCallback {
                    override fun onToken(token: String) {
                        trySend(token)
                    }

                    override fun onComplete(status: Int) {
                        if (generateMutex.isLocked) generateMutex.unlock()
                        close()
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "Generation error: $error")
                        if (generateMutex.isLocked) generateMutex.unlock()
                        close(Exception(error))
                    }
                }

                setCallback(callback)
                val result = generate(prompt)
                if (result != 0) {
                    if (generateMutex.isLocked) generateMutex.unlock()
                    close(Exception("Generate failed: $result"))
                }
            } catch (e: Exception) {
                if (generateMutex.isLocked) generateMutex.unlock()
                close(e)
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
