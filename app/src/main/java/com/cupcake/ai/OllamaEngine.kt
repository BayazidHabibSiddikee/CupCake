package com.cupcake.ai

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.cupcake.data.model.ModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.Result
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

class OllamaEngine private constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {

    companion object {
        private const val TAG = "OllamaEngine"
        private var _instance: OllamaEngine? = null
        private var _providedClient: OkHttpClient? = null
        private val lock = Any()

        fun getInstance(): OllamaEngine {
            synchronized(lock) {
                if (_instance == null) {
                    val client = _providedClient ?: OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .writeTimeout(120, TimeUnit.SECONDS)
                        .build()
                    _instance = OllamaEngine(
                        okHttpClient = client,
                        json = Json { ignoreUnknownKeys = true }
                    )
                }
                return _instance!!
            }
        }

        fun initialize(context: Context, providedClient: OkHttpClient? = null) {
            _providedClient = providedClient
            getInstance()
            Log.i(TAG, "Ollama client initialized")
        }
    }

    fun isReady(): Boolean = true

    data class OllamaChatRequest(
        val model: String,
        val messages: List<OllamaMessage>,
        val stream: Boolean = true,
        val options: OllamaOptions? = null
    )

    data class OllamaMessage(
        val role: String,
        val content: String
    )

    data class OllamaOptions(
        val temperature: Float = 0.7f,
        val top_p: Float = 0.9f,
        val top_k: Int = 40,
        val num_predict: Int = 2048,
        val num_ctx: Int = 4096,
        val repeat_penalty: Float = 1.1f,
        val repeat_last_n: Int = 64,
        val seed: Int = -1
    )

    data class OllamaChatResponse(
        val model: String,
        val created_at: String,
        val message: OllamaMessage,
        val done: Boolean,
        val total_duration: Long? = null,
        val load_duration: Long? = null,
        val prompt_eval_count: Int? = null,
        val prompt_eval_duration: Long? = null,
        val eval_count: Int? = null,
        val eval_duration: Long? = null
    )

    data class OllamaModelsResponse(
        val models: List<OllamaModelInfo>
    )

    data class OllamaModelInfo(
        val name: String,
        val model: String,
        val modified_at: String,
        val size: Long,
        val digest: String,
        val details: OllamaModelDetails? = null
    )

    data class OllamaModelDetails(
        val parent_model: String,
        val format: String,
        val family: String,
        val families: List<String>,
        val parameter_size: String,
        val quantization_level: String
    )

    interface GenerateCallback {
        fun onToken(token: String)
        fun onComplete()
        fun onError(error: String)
    }

    private val generateMutex = kotlinx.coroutines.sync.Mutex()

    fun checkConnection(baseUrl: String): ReceiveChannel<Result<Boolean>> = Channel<Result<Boolean>>(1).apply {
        CoroutineScope(Dispatchers.IO).launch {
            val channel = this@apply
            try {
                val url = "$baseUrl/api/tags"
                val request = Request.Builder().url(url).get().build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        channel.send(Result.success(true))
                    } else {
                        channel.send(Result.success(false))
                    }
                    channel.close()
                }
            } catch (e: Exception) {
                channel.send(Result.success(false))
                channel.close()
            }
        }
    }

    fun listModels(baseUrl: String): ReceiveChannel<Result<List<OllamaModelInfo>>> = Channel<Result<List<OllamaModelInfo>>>(1).apply {
        CoroutineScope(Dispatchers.IO).launch {
            val channel = this@apply
            try {
                val url = "$baseUrl/api/tags"
                val request = Request.Builder().url(url).get().build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val result = json.decodeFromString<OllamaModelsResponse>(body)
                        channel.send(Result.success(result.models))
                    } else {
                        channel.send(Result.failure(IOException("HTTP ${response.code}")))
                    }
                    channel.close()
                }
            } catch (e: Exception) {
                channel.send(Result.failure(e))
                channel.close()
            }
        }
    }

    fun pullModel(baseUrl: String, modelName: String, onProgress: (String) -> Unit): ReceiveChannel<Result<Unit>> = Channel<Result<Unit>>(1).apply {
        CoroutineScope(Dispatchers.IO).launch {
            val channel = this@apply
            try {
                val url = "$baseUrl/api/pull"
                val jsonBody = json.encodeToString(
                    mapOf("name" to modelName, "stream" to true as Any)
                )
                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val eventSource = EventSources.createFactory(okHttpClient).newEventSource(request, object : EventSourceListener {
                    override fun onOpen(eventSource: EventSource, response: Response) {
                        Log.d(TAG, "Pull started for $modelName")
                    }

                    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String?) {
                        data?.let {
                            try {
                                val progress = json.decodeFromString<Map<String, Any>>(it)
                                val status = progress["status"] as? String ?: ""
                                val completed = progress["completed"] as? Long ?: 0L
                                val total = progress["total"] as? Long ?: 0L
                                if (total > 0) {
                                    onProgress("$status: ${(completed * 100 / total)}%")
                                } else {
                                    onProgress(status)
                                }
                            } catch (e: Exception) {
                                onProgress(it)
                            }
                        }
                    }

                    override fun onClosed(eventSource: EventSource) {
                        Log.d(TAG, "Pull completed for $modelName")
                        channel.send(Result.success(Unit))
                        channel.close()
                    }

                    override fun onFailure(eventSource: EventSource?, t: Throwable, response: Response?) {
                        Log.e(TAG, "Pull failed for $modelName", t)
                        channel.send(Result.failure(t ?: IOException("Pull failed")))
                        channel.close()
                    }
                })

                // Keep reference to prevent GC
                getOrCreateEventSourceRegistry().add(eventSource)
            } catch (e: Exception) {
                channel.send(Result.failure(e))
                channel.close()
            }
        }
    }

    private val eventSourceRegistry = java.util.Collections.synchronizedSet(
        java.util.HashSet<EventSource>()
    )

    private fun getOrCreateEventSourceRegistry(): java.util.Set<EventSource> = eventSourceRegistry

    fun generateStream(
        baseUrl: String,
        config: ModelConfig,
        messages: List<OllamaMessage>,
        callback: GenerateCallback
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            if (!generateMutex.tryLock()) {
                Log.w(TAG, "Engine busy, dropping generation request.")
                callback.onError("Engine busy")
                return@launch
            }
            try {
                val endpoint = config.effectiveEndpoint()
                val url = "$endpoint/chat/completions"

                val requestBody = OllamaChatRequest(
                    model = config.modelName,
                    messages = messages,
                    stream = config.useStreaming,
                    options = OllamaOptions(
                        temperature = config.temperature,
                        top_p = config.topP,
                        top_k = config.topK,
                        num_predict = config.maxTokens,
                        num_ctx = 4096,
                        repeat_penalty = config.repeatPenalty,
                        repeat_last_n = 64,
                        seed = -1
                    )
                )

                val jsonBody = json.encodeToString(requestBody)
                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                if (config.useStreaming) {
                    val eventSource = EventSources.createFactory(okHttpClient).newEventSource(request, object : EventSourceListener {
                        override fun onOpen(eventSource: EventSource, response: Response) {
                            Log.d(TAG, "Stream opened")
                        }

                        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String?) {
                            data?.let {
                                if (it == "[DONE]") {
                                    callback.onComplete()
                                    return
                                }
                                try {
                                    val chunk = json.decodeFromString<OllamaChatResponse>(it)
                                    val content = chunk.message.content
                                    if (content.isNotBlank()) {
                                        callback.onToken(content)
                                    }
                                    if (chunk.done) {
                                        callback.onComplete()
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to parse stream chunk: $it", e)
                                }
                            }
                        }

                        override fun onClosed(eventSource: EventSource) {
                            Log.d(TAG, "Stream closed")
                        }

                        override fun onFailure(eventSource: EventSource?, t: Throwable, response: Response?) {
                            Log.e(TAG, "Stream failed", t)
                            callback.onError(t?.message ?: "Stream failed")
                        }
                    })

                    eventSourceRegistry.add(eventSource)
                } else {
                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val result = json.decodeFromString<OllamaChatResponse>(body)
                            val content = result.message.content
                            if (content.isNotBlank()) {
                                callback.onToken(content)
                            }
                            callback.onComplete()
                        } else {
                            val errorBody = response.body?.string() ?: "HTTP ${response.code}"
                            callback.onError("HTTP ${response.code}: $errorBody")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Generation error", e)
                callback.onError(e.message ?: "Unknown error")
            } finally {
                generateMutex.unlock()
            }
        }
    }

    fun cancel() {
        eventSourceRegistry.forEach { it.cancel() }
        eventSourceRegistry.clear()
    }
}