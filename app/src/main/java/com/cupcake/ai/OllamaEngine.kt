package com.cupcake.ai

import android.content.Context
import android.util.Log
import com.cupcake.data.model.ModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.Result

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

    @Serializable
    data class OllamaChatRequest(
        val model: String,
        val messages: List<OllamaMessage>,
        val stream: Boolean = true,
        val options: OllamaOptions? = null
    )

    @Serializable
    data class OllamaMessage(
        val role: String,
        val content: String
    )

    @Serializable
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

    @Serializable
    data class OllamaChatResponse(
        val model: String? = null,
        val created_at: String? = null,
        val message: OllamaMessage? = null,
        val done: Boolean = false
    )

    @Serializable
    data class OllamaModelsResponse(
        val models: List<OllamaModelInfo> = emptyList()
    )

    @Serializable
    data class OllamaModelInfo(
        val name: String,
        val model: String? = null,
        val modified_at: String? = null,
        val size: Long = 0L,
        val digest: String? = null,
        val details: OllamaModelDetails? = null
    )

    @Serializable
    data class OllamaModelDetails(
        val parent_model: String? = null,
        val format: String? = null,
        val family: String? = null,
        val families: List<String>? = null,
        val parameter_size: String? = null,
        val quantization_level: String? = null
    )

    interface GenerateCallback {
        fun onToken(token: String)
        fun onComplete()
        fun onError(error: String)
    }

    private val generateMutex = Mutex()
    private val activeCalls = Collections.synchronizedSet(HashSet<Call>())

    fun checkConnection(baseUrl: String): ReceiveChannel<Result<Boolean>> = Channel<Result<Boolean>>(1).apply {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cleanBase = baseUrl.removeSuffix("/v1").removeSuffix("/")
                val url = "$cleanBase/api/tags"
                val request = Request.Builder().url(url).get().build()
                okHttpClient.newCall(request).execute().use { response ->
                    send(Result.success(response.isSuccessful))
                }
            } catch (e: Exception) {
                send(Result.success(false))
            } finally {
                close()
            }
        }
    }

    fun listModels(baseUrl: String): ReceiveChannel<Result<List<OllamaModelInfo>>> = Channel<Result<List<OllamaModelInfo>>>(1).apply {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cleanBase = baseUrl.removeSuffix("/v1").removeSuffix("/")
                val url = "$cleanBase/api/tags"
                val request = Request.Builder().url(url).get().build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val result = json.decodeFromString<OllamaModelsResponse>(body)
                        send(Result.success(result.models))
                    } else {
                        send(Result.failure(IOException("HTTP ${response.code}")))
                    }
                }
            } catch (e: Exception) {
                send(Result.failure(e))
            } finally {
                close()
            }
        }
    }

    fun pullModel(baseUrl: String, modelName: String, onProgress: (String) -> Unit): ReceiveChannel<Result<Unit>> = Channel<Result<Unit>>(1).apply {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cleanBase = baseUrl.removeSuffix("/v1").removeSuffix("/")
                val url = "$cleanBase/api/pull"
                val jsonBody = json.encodeToString(mapOf("name" to modelName, "stream" to "true"))
                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val call = okHttpClient.newCall(request)
                activeCalls.add(call)
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        send(Result.failure(IOException("HTTP ${response.code}: ${response.message}")))
                        return@use
                    }
                    val body = response.body ?: throw IOException("Empty response body")
                    val reader = BufferedReader(InputStreamReader(body.byteStream()))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { raw ->
                            if (raw.isNotBlank()) {
                                try {
                                    val obj = json.decodeFromString<JsonObject>(raw)
                                    val status = obj["status"]?.jsonPrimitive?.content ?: ""
                                    val completed = obj["completed"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                                    val total = obj["total"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                                    if (total > 0) {
                                        onProgress("$status: ${(completed * 100 / total)}%")
                                    } else {
                                        onProgress(status)
                                    }
                                } catch (e: Exception) {
                                    onProgress(raw)
                                }
                            }
                        }
                    }
                    send(Result.success(Unit))
                }
            } catch (e: Exception) {
                send(Result.failure(e))
            } finally {
                close()
            }
        }
    }

    suspend fun generateStream(
        baseUrl: String,
        config: ModelConfig,
        messages: List<OllamaMessage>,
        callback: GenerateCallback
    ) {
        if (!generateMutex.tryLock()) {
            Log.w(TAG, "Engine busy, dropping generation request.")
            callback.onError("Engine busy")
            return
        }
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                // Two distinct wire protocols live behind "remote":
                //  - Ollama servers: /api/chat with {"options": {...}}
                //  - OpenAI-compatible hosts (also served by vLLM, LM Studio,
                //    llama.cpp server, OpenRouter...): /chat/completions with
                //    flat max_tokens/temperature. Routing every provider into
                //    /api/chat 404s the moment the base URL carries /v1.
                val isOllamaApi = config.provider == ModelConfig.ModelProvider.CUSTOM_OLLAMA
                val cleanedBase = baseUrl.removeSuffix("/")
                    .removeSuffix("/v1")
                val url: String
                val jsonBody: String
                if (isOllamaApi) {
                    url = "$cleanedBase/api/chat"
                    jsonBody = json.encodeToString(
                        OllamaChatRequest(
                            model = config.modelName,
                            messages = messages,
                            stream = config.useStreaming,
                            options = OllamaOptions(
                                temperature = config.temperature,
                                top_p = config.topP,
                                top_k = config.topK,
                                num_predict = config.maxTokens,
                                num_ctx = 4096,
                                repeat_penalty = 1.1f,
                                repeat_last_n = 64,
                                seed = -1
                            )
                        )
                    )
                } else {
                    url = "${baseUrl.removeSuffix("/")}/chat/completions"
                    val messagesJson = buildJsonArray {
                        messages.forEach { msg ->
                            addJsonObject {
                                put("role", msg.role)
                                put("content", msg.content)
                            }
                        }
                    }
                    jsonBody = json.encodeToString(
                        buildJsonObject {
                            put("model", config.modelName)
                            put("stream", config.useStreaming)
                            put("max_tokens", config.maxTokens)
                            put("temperature", config.temperature)
                            put("top_p", config.topP)
                            put("messages", messagesJson)
                        }
                    )
                }

                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .apply {
                        // Authenticated remotes (OpenAI, OpenRouter, gated vLLM)
                        // 401 without the bearer header.
                        if (config.apiKey.isNotBlank()) {
                            addHeader("Authorization", "Bearer ${config.apiKey}")
                        }
                    }
                    .build()

                val call = okHttpClient.newCall(request)
                activeCalls.add(call)

                call.execute().use { response ->
                    activeCalls.remove(call)
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "HTTP ${response.code}"
                        callback.onError("HTTP ${response.code}: $errorBody")
                        return@use
                    }

                    val body = response.body
                    if (body == null) {
                        callback.onError("Response body was null")
                        return@use
                    }

                    val reader = BufferedReader(InputStreamReader(body.byteStream()))
                    var line: String?
                    // Guard against double onComplete(): the [DONE] sentinel fires it,
                    // then the post-loop call would fire it again (double translate
                    // flash + double fan-out downstream).
                    var completed = false
                    val finish: () -> Unit = {
                        if (!completed) {
                            completed = true
                            callback.onComplete()
                        }
                    }
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line?.trim() ?: continue
                        if (trimmed.isEmpty()) continue

                        // Check for OpenAI format (SSE "data: ...") or raw Ollama NDJSON
                        val jsonString = if (trimmed.startsWith("data:")) {
                            val dataPayload = trimmed.removePrefix("data:").trim()
                            if (dataPayload == "[DONE]") {
                                finish()
                                break
                            }
                            dataPayload
                        } else {
                            trimmed
                        }

                        try {
                            val jsonObj = json.decodeFromString<JsonObject>(jsonString)

                            // Try Ollama native format: {"message": {"content": "..."}, "done": boolean}
                            val messageObj = jsonObj["message"]?.jsonObject
                            val content = messageObj?.get("content")?.jsonPrimitive?.content
                            if (!content.isNullOrEmpty()) {
                                callback.onToken(content)
                            }

                            // Try OpenAI format: {"choices": [{"delta": {"content": "..."}}]}
                            // or non-stream {"choices": [{"message": {"content": "..."}}]} (H5).
                            val choices = jsonObj["choices"]?.jsonArray
                            if (choices != null && choices.isNotEmpty()) {
                                val first = choices[0].jsonObject
                                val delta = first["delta"]?.jsonObject
                                val openAiContent = delta?.get("content")?.jsonPrimitive?.content
                                    ?: first["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
                                if (!openAiContent.isNullOrEmpty()) {
                                    callback.onToken(openAiContent)
                                }
                                // Server-side refusal / error payload: surface it instead of
                                // falling through to the empty-response fallback (H5/M5).
                                val errObj = first["error"]?.jsonObject ?: jsonObj["error"]?.jsonObject
                                val errMsg = errObj?.get("message")?.jsonPrimitive?.content
                                if (!errMsg.isNullOrEmpty()) {
                                    callback.onError(errMsg)
                                    return@use
                                }
                                // finish_reason present ⇒ last chunk of this response
                                val finishReason =
                                    choices[0].jsonObject["finish_reason"]?.jsonPrimitive?.content
                                if (!finishReason.isNullOrEmpty()) {
                                    finish()
                                    break
                                }
                            }

                            val isDone = jsonObj["done"]?.jsonPrimitive?.booleanOrNull == true
                            if (isDone) {
                                finish()
                                break
                            }

                            // Ollama /api/generate shape: {"response": "...", "done": false} (H5).
                            val generateText = jsonObj["response"]?.jsonPrimitive?.content
                            if (!generateText.isNullOrEmpty()) {
                                callback.onToken(generateText)
                            }
                            // Top-level error object (both APIs): don't swallow it (H5/M5).
                            if (jsonObj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content?.isNotBlank() == true &&
                                jsonObj["choices"] == null && jsonObj["message"] == null && jsonObj["response"] == null
                            ) {
                                callback.onError(jsonObj["error"]!!.jsonObject["message"]!!.jsonPrimitive.content)
                                return@use
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed parsing chunk: $jsonString", e)
                        }
                    }
                    finish()
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
        synchronized(activeCalls) {
            activeCalls.forEach { it.cancel() }
            activeCalls.clear()
        }
    }
}
