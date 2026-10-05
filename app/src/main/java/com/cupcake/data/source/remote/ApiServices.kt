package com.cupcake.data.source.remote

import com.cupcake.data.model.StreamChunk
import kotlinx.coroutines.flow.Flow
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Retrofit service definitions for remote inference providers.
 * Usage: retrofit.create(ApiServices.OpenAiApi::class.java)
 */
object ApiServices {

    interface OpenAiApi {
        @POST("chat/completions")
        suspend fun chatCompletion(@Body request: ChatCompletionRequest): Response<ChatCompletionResponse>

        @Streaming
        @POST("chat/completions")
        fun chatCompletionStream(@Body request: ChatCompletionRequest): Flow<StreamChunk>

        @GET("models")
        suspend fun listModels(): Response<ModelsResponse>
    }

    interface OllamaApi {
        @POST("api/chat")
        suspend fun chat(@Body request: OllamaChatRequest): Response<OllamaChatResponse>

        @POST("api/chat")
        fun chatStream(@Body request: OllamaChatRequest): Flow<OllamaStreamChunk>

        @GET("api/tags")
        suspend fun listModels(): Response<OllamaModelsResponse>
    }

    interface CustomApi {
        @POST
        suspend fun chatCompletion(@Url url: String, @Body request: Any): Response<Any>

        @POST
        fun chatCompletionStream(@Url url: String, @Body request: Any): Flow<Any>
    }

    // =============================================================================
    // REQUEST/RESPONSE MODELS
    // =============================================================================

    data class ChatCompletionRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Float = 0.7f,
        val top_p: Float = 0.9f,
        val top_k: Int = 40,
        val max_tokens: Int = 2048,
        val stream: Boolean = false,
        val stop: List<String>? = null
    )

    data class ChatMessage(
        val role: String,
        val content: String,
        val name: String? = null
    )

    data class ChatCompletionResponse(
        val id: String,
        val `object`: String,
        val created: Long,
        val model: String,
        val choices: List<Choice>,
        val usage: TokenUsage
    )

    data class Choice(
        val index: Int,
        val message: ChatMessage,
        val finish_reason: String
    )

    data class TokenUsage(
        val prompt_tokens: Int,
        val completion_tokens: Int,
        val total_tokens: Int
    )

    data class ModelsResponse(
        val `object`: String,
        val data: List<ModelInfo>
    )

    data class ModelInfo(
        val id: String,
        val `object`: String,
        val created: Long,
        val owned_by: String
    )

    // Ollama
    data class OllamaChatRequest(
        val model: String,
        val messages: List<OllamaMessage>,
        val stream: Boolean = false,
        val options: Map<String, Any>? = null
    )

    data class OllamaMessage(
        val role: String,
        val content: String,
        val images: List<String>? = null // base64 encoded
    )

    data class OllamaChatResponse(
        val model: String,
        val created_at: String,
        val message: OllamaMessage,
        val done: Boolean,
        val total_duration: Long? = null,
        val load_duration: Long? = null,
        val prompt_eval_count: Int? = null,
        val eval_count: Int? = null
    )

    data class OllamaStreamChunk(
        val model: String,
        val created_at: String,
        val message: OllamaMessage,
        val done: Boolean
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
        val details: OllamaModelDetails
    )

    data class OllamaModelDetails(
        val parent_model: String,
        val format: String,
        val family: String,
        val families: List<String>,
        val parameter_size: String,
        val quantization_level: String
    )
}
