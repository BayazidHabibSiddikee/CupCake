package com.cupcake.data.source.remote

import com.cupcake.data.model.ApiProvider
import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.GenerationRequest
import com.cupcake.data.model.ModelConfig
import com.cupcake.data.model.StreamChunk
import com.cupcake.native.QwenNative
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiClient @Inject constructor(
    private val qwenNative: QwenNative,
    private val okHttpClient: OkHttpClient
) {

    // Local generation via Qwen JNI
    fun generateLocal(
        messages: List<ChatMessage>,
        config: ModelConfig
    ) = channelFlow<StreamChunk> {
        val prompt = buildPrompt(messages)

        qwenNative.generateStream(
            prompt = prompt,
            config = QwenNative.GenerateConfig(
                temperature = config.temperature,
                topP = config.topP,
                topK = config.topK,
                maxTokens = config.maxTokens
            )
        ).consumeEach { chunk ->
            trySend(StreamChunk(
                id = java.util.UUID.randomUUID().toString(),
                delta = chunk,
                finishReason = null
            ))
        }

        // Final chunk
        trySend(StreamChunk(
            id = java.util.UUID.randomUUID().toString(),
            delta = "",
            finishReason = "stop"
        ))
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    // Remote generation via custom API
    fun generateRemote(
        messages: List<ChatMessage>,
        config: ModelConfig
    ) = channelFlow<StreamChunk> {
        val provider = config.provider
        val endpoint = config.effectiveEndpoint()

        when (provider) {
            ModelConfig.ModelProvider.CUSTOM_OPENAI,
            ModelConfig.ModelProvider.CUSTOM_VLLM,
            ModelConfig.ModelProvider.CUSTOM_OTHER -> {
                generateOpenAiCompatible(endpoint, config, messages)?.collect { trySend(it) }
            }
            ModelConfig.ModelProvider.CUSTOM_OLLAMA -> {
                generateOllama(endpoint, config, messages)?.collect { trySend(it) }
            }
            else -> {
                trySend(StreamChunk(
                    id = "",
                    delta = "",
                    finishReason = "error",
                    usage = null
                ))
            }
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    private fun generateOpenAiCompatible(
        baseUrl: String,
        config: ModelConfig,
        messages: List<ChatMessage>
    ): kotlinx.coroutines.flow.Flow<StreamChunk>? {
        val url = "$baseUrl/chat/completions"
        val requestBody = buildOpenAiRequest(config, messages)

        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val client = okHttpClient.newBuilder()
                .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()

            channelFlow {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val error = response.body?.string() ?: "Unknown error"
                        trySend(StreamChunk(
                            id = "",
                            delta = "API Error: $error",
                            finishReason = "error"
                        ))
                        return@channelFlow
                    }

                    val body = response.body?.byteStream()
                            ?: return@channelFlow

                    body.bufferedReader().readLines().forEach { line ->
                        if (line.startsWith("data: ")) {
                            val data = line.substring(6)
                            if (data == "[DONE]") {
                                trySend(StreamChunk(id = "", delta = "", finishReason = "stop"))
                            } else {
                                try {
                                    val json = JSONObject(data)
                                    val choices = json.getJSONArray("choices")
                                    if (choices.length() > 0) {
                                        val delta = choices.getJSONObject(0).getJSONObject("delta").optString("content", "")
                                        val finishReason = choices.getJSONObject(0).optString("finish_reason", "")
                                        if (delta.isNotEmpty()) {
                                            trySend(StreamChunk(
                                                id = json.optString("id", ""),
                                                delta = delta,
                                                finishReason = if (finishReason.isNotEmpty()) finishReason else null
                                            ))
                                        }
                                        if (finishReason.isNotEmpty()) {
                                            trySend(StreamChunk(
                                                id = json.optString("id", ""),
                                                delta = "",
                                                finishReason = finishReason
                                            ))
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Ignore parse errors
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            flow { emit(StreamChunk(id = "", delta = "Error: ${e.message}", finishReason = "error")) }
        }
    }

    private fun generateOllama(
        baseUrl: String,
        config: ModelConfig,
        messages: List<ChatMessage>
    ): kotlinx.coroutines.flow.Flow<StreamChunk>? {
        val url = "$baseUrl/api/chat"
        val requestBody = buildOllamaRequest(config, messages)

        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val client = okHttpClient.newBuilder()
                .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()

            channelFlow {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val error = response.body?.string() ?: "Unknown error"
                        trySend(StreamChunk(id = "", delta = "Ollama Error: $error", finishReason = "error"))
                        return@channelFlow
                    }

                    val body = response.body?.byteStream() ?: return@channelFlow
                    body.bufferedReader().readLines().forEach { line ->
                        try {
                            val json = JSONObject(line)
                            val message = json.getJSONObject("message")
                            val content = message.optString("content", "")
                            val done = json.optBoolean("done", false)
                            if (content.isNotEmpty()) {
                                trySend(StreamChunk(
                                    id = "",
                                    delta = content,
                                    finishReason = if (done) "stop" else null
                                ))
                            }
                            if (done) {
                                trySend(StreamChunk(id = "", delta = "", finishReason = "stop"))
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
            }
        } catch (e: Exception) {
            flow { emit(StreamChunk(id = "", delta = "Error: ${e.message}", finishReason = "error")) }
        }
    }

    private fun buildPrompt(messages: List<ChatMessage>): String {
        return messages.joinToString("\n\n") { msg ->
            when (msg.role) {
                ChatMessage.MessageRole.SYSTEM -> "System: ${msg.content}"
                ChatMessage.MessageRole.USER -> "User: ${msg.content}"
                ChatMessage.MessageRole.ASSISTANT -> "Assistant: ${msg.content}"
                else -> msg.content
            }
        }
    }

    private fun buildOpenAiRequest(config: ModelConfig, messages: List<ChatMessage>): String {
        val json = JSONObject()
        json.put("model", config.modelName)
        json.put("temperature", config.temperature)
        json.put("top_p", config.topP)
        json.put("top_k", config.topK)
        json.put("max_tokens", config.maxTokens)
        json.put("stream", config.useStreaming)

        val messagesArray = JSONArray()
        messages.forEach { msg ->
            val msgJson = JSONObject()
            msgJson.put("role", msg.role.value)
            msgJson.put("content", msg.content)
            messagesArray.put(msgJson)
        }
        json.put("messages", messagesArray)

        return json.toString()
    }

    private fun buildOllamaRequest(config: ModelConfig, messages: List<ChatMessage>): String {
        val json = JSONObject()
        json.put("model", config.modelName)
        json.put("stream", config.useStreaming)

        val options = JSONObject()
        options.put("temperature", config.temperature)
        options.put("top_p", config.topP)
        options.put("top_k", config.topK)
        options.put("num_predict", config.maxTokens)
        json.put("options", options)

        val messagesArray = JSONArray()
        messages.forEach { msg ->
            val msgJson = JSONObject()
            msgJson.put("role", msg.role.value.lowercase())
            msgJson.put("content", msg.content)
            messagesArray.put(msgJson)
        }
        json.put("messages", messagesArray)

        return json.toString()
    }

    suspend fun testConnection(provider: ApiProvider): Boolean {
        return try {
            val url = "${provider.baseUrl}/models"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${provider.apiKey}")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchModels(provider: ApiProvider): List<String> {
        return try {
            val url = "${provider.baseUrl}/models"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${provider.apiKey}")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val data = json.getJSONArray("data")
                    (0 until data.length()).mapNotNull { i ->
                        data.getJSONObject(i).optString("id")
                    }
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}