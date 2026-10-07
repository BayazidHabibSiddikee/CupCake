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
            // fileName may include an assets-relative subdir (e.g. "models/x.gguf");
            // the destination always uses just the base name to avoid doubled paths.
            val modelFile = File(modelDir, File(fileName).name)

            if (modelFile.exists() && modelFile.length() > 0) {
                Log.i(TAG, "Model already exists: ${modelFile.length()} bytes")
                onProgressBytes?.invoke(modelFile.length(), modelFile.length())
                return modelFile
            }

            // Declared asset length for progress (-1 when unknown/compressed).
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
                    val buffer = ByteArray(1024 * 1024) // 1MB chunks
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copiedBytes += read
                        // Report at most every 8MB to avoid spamming the UI thread.
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
        repeatPenalty: Float, repeatLastN: Int, seed: Int
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
        val modelFileName: String = "models/qwen2.5-0.5b-instruct-q2_k.gguf",
        // Ultra-fast 512 context for snappy, dumb-but-fast chat on phones.
        val nCtx: Int = 512,
        val nThreads: Int = 6,
        val nBatch: Int = 512,
        val temperature: Float = 0.7f,
        val topP: Float = 0.9f,
        val topK: Int = 40,
        val repeatPenalty: Float = 1.1f,
        val repeatLastN: Int = 64,
        val seed: Int = -1
    )

    private var _isModelLoaded = false

    fun loadModel(
        context: Context,
        config: Config = Config(),
        onProgressBytes: ((copiedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): Result<Unit> {
        // Already loaded (e.g. warmed at app start) - don't pay init cost again.
        if (isReady()) return Result.success(Unit)
        return try {
            val modelFile = copyModelFromAssets(context, config.modelFileName, onProgressBytes)
            val result = initModel(
                modelFile.absolutePath,
                config.nCtx,
                config.nThreads,
                config.nBatch
            )
            if (result == 0) {
                _isModelLoaded = true
                setSamplingParams(
                    config.temperature,
                    config.topP,
                    config.topK,
                    config.repeatPenalty,
                    config.repeatLastN,
                    config.seed
                )
                Result.success(Unit)
            } else {
                Result.failure(Exception("Init failed: $result"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // NOTE: UNLIMITED buffer is required. The native thread emits tokens via
    // trySend, which silently drops on a rendezvous channel when the consumer
    // isn't parked yet - on fast devices this drops the whole response.
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
                        close()
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "Generation error: $error")
                        close(Exception(error))
                    }
                }

                setCallback(callback)
                val result = generate(prompt)
                if (result != 0) {
                    close(Exception("Generate failed: $result"))
                }
            } finally {
                generateMutex.unlock()
            }
        }
    }

    fun unload() {
        if (_isModelLoaded) {
            release()
            _isModelLoaded = false
        }
    }

    fun isReady(): Boolean = isLoaded()

    fun getInfo(): String = getModelInfo()
}