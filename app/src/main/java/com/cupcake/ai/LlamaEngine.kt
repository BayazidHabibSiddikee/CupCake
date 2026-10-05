package com.cupcake.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import java.io.File
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

        fun copyModelFromAssets(context: Context, fileName: String): File {
            val modelDir = File(context.filesDir, "models")
            modelDir.mkdirs()
            // fileName may include an assets-relative subdir (e.g. "models/x.gguf");
            // the destination always uses just the base name to avoid doubled paths.
            val modelFile = File(modelDir, File(fileName).name)

            if (modelFile.exists() && modelFile.length() > 0) {
                Log.i(TAG, "Model already exists: ${modelFile.length()} bytes")
                return modelFile
            }

            Log.i(TAG, "Copying model from assets...")
            context.assets.open(fileName).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
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
        val modelFileName: String = "models/qwen2.5-0.5b-instruct-q4_k_m.gguf",
        val nCtx: Int = 4096,
        val nThreads: Int = 4,
        val nBatch: Int = 512,
        val temperature: Float = 0.7f,
        val topP: Float = 0.9f,
        val topK: Int = 40,
        val repeatPenalty: Float = 1.1f,
        val repeatLastN: Int = 64,
        val seed: Int = -1
    )

    private var _isModelLoaded = false

    fun loadModel(context: Context, config: Config = Config()): Result<Unit> {
        return try {
            val modelFile = copyModelFromAssets(context, config.modelFileName)
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

    fun generateStream(prompt: String): ReceiveChannel<String> = Channel<String>().apply {
        CoroutineScope(Dispatchers.IO).launch {
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