package com.cupcake.jni

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class QwenNative private constructor() {

    companion object {
        private const val TAG = "QwenNative"
        private const val LIB_NAME = "qwen_jni"
        private var initialized = false

        @Volatile
        private var INSTANCE: QwenNative? = null

        fun getInstance(): QwenNative = INSTANCE ?: synchronized(this) {
            INSTANCE ?: QwenNative().also { INSTANCE = it }
        }

        fun initialize(context: Context) {
            if (initialized) return
            NativeLoader.loadLibrary(LIB_NAME)
            initialized = true
            Log.i(TAG, "Native library loaded: $LIB_NAME")
        }

        fun copyModelFromAssets(context: Context, modelFileName: String): File {
            val modelDir = File(context.filesDir, "models")
            modelDir.mkdirs()
            // modelFileName may include an assets-relative subdir; the destination
            // always uses just the base name to avoid doubled paths.
            val modelFile = File(modelDir, File(modelFileName).name)

            if (modelFile.exists() && modelFile.length() > 0) {
                return modelFile
            }

            context.assets.open(modelFileName).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Copied model to: ${modelFile.absolutePath}")
            return modelFile
        }

        fun copyTokenizerFromAssets(context: Context, tokenizerFileName: String): File {
            val tokenizerDir = File(context.filesDir, "tokenizer")
            tokenizerDir.mkdirs()
            val tokenizerFile = File(tokenizerDir, File(tokenizerFileName).name)

            if (tokenizerFile.exists() && tokenizerFile.length() > 0) {
                return tokenizerFile
            }

            context.assets.open("tokenizer/$tokenizerFileName").use { input ->
                FileOutputStream(tokenizerFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Copied tokenizer to: ${tokenizerFile.absolutePath}")
            return tokenizerFile
        }
    }

    // =========================================================================
    // NATIVE METHODS
    // =========================================================================

    external fun initModel(
        modelPath: String,
        tokenizerPath: String,
        nThreads: Int,
        nCtx: Int
    ): Int

    external fun generateStream(
        prompt: String,
        temperature: Float,
        topP: Float,
        topK: Int,
        maxTokens: Int,
        callback: GenerateCallback
    ): Int

    external fun releaseModel()

    external fun getModelInfo(): String

    external fun isModelLoaded(): Boolean

    // =========================================================================
    // CALLBACK INTERFACE
    // =========================================================================

    interface GenerateCallback {
        fun onToken(token: String)
        fun onComplete(status: Int)
        fun onError(error: String)
    }

    // =========================================================================
    // HIGH-LEVEL API
    // =========================================================================

    data class GenerateConfig(
        val temperature: Float = 0.7f,
        val topP: Float = 0.9f,
        val topK: Int = 40,
        val maxTokens: Int = 2048,
        val nThreads: Int = 4,
        val nCtx: Int = 4096
    )

    data class ModelPaths(
        val model: String,
        val tokenizer: String
    )

    private var _isInitialized = false

    fun initializeModel(
        context: Context,
        paths: ModelPaths,
        config: GenerateConfig = GenerateConfig()
    ): Result<Unit> {
        return try {
            val modelFile = copyModelFromAssets(context, paths.model)
            val tokenizerFile = copyTokenizerFromAssets(context, paths.tokenizer)

            val result = initModel(
                modelFile.absolutePath,
                tokenizerFile.absolutePath,
                config.nThreads,
                config.nCtx
            )

            if (result == 0) {
                _isInitialized = true
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to initialize model: $result"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateStream(
        prompt: String,
        config: GenerateConfig = GenerateConfig()
    ): ReceiveChannel<String> = Channel<String>().apply {
        CoroutineScope(Dispatchers.IO).launch {
            val deferred = CompletableDeferred<Int>()

            val callback = object : GenerateCallback {
                override fun onToken(token: String) {
                    trySend(token)
                }

                override fun onComplete(status: Int) {
                    deferred.complete(status)
                    close()
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Generation error: $error")
                    deferred.completeExceptionally(Exception(error))
                    close(Exception(error))
                }
            }

            generateStream(
                prompt,
                config.temperature,
                config.topP,
                config.topK,
                config.maxTokens,
                callback
            )
        }
    }

    fun release() {
        if (_isInitialized) {
            releaseModel()
            _isInitialized = false
        }
    }

    fun getInfo(): String = getModelInfo()

    fun isReady(): Boolean = isModelLoaded()
}