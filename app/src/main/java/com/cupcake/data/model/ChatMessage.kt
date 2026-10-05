package com.cupcake.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.cupcake.data.source.local.converters.Converters
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.toEpochMilli())
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.ofEpochMilli(decoder.decodeLong())
    }
}

@Serializable
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = Instant.now(),
    val modelUsed: String = "",
    val tokenCount: Int = 0,
    val latencyMs: Long = 0,
    val metadata: String = "{}" // JSON for images, tool calls, etc.
) {
    enum class MessageRole(val value: String) {
        USER("user"),
        ASSISTANT("assistant"),
        SYSTEM("system"),
        TOOL("tool")
    }

    companion object {
        fun system(content: String, conversationId: String): ChatMessage {
            return ChatMessage(
                conversationId = conversationId,
                role = MessageRole.SYSTEM,
                content = content
            )
        }

        fun user(content: String, conversationId: String): ChatMessage {
            return ChatMessage(
                conversationId = conversationId,
                role = MessageRole.USER,
                content = content
            )
        }

        fun assistant(content: String, conversationId: String, modelUsed: String = ""): ChatMessage {
            return ChatMessage(
                conversationId = conversationId,
                role = MessageRole.ASSISTANT,
                content = content,
                modelUsed = modelUsed
            )
        }
    }
}

@Entity(tableName = "conversations")
@Serializable
data class Conversation(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val systemPrompt: SystemPrompt? = null,
    val modelConfig: ModelConfig = ModelConfig.default(),
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant = Instant.now(),
    val messageCount: Int = 0
)

@Entity(tableName = "system_prompts")
@Serializable
data class SystemPrompt(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val text: String,
    val images: List<PromptImage> = emptyList(),
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now(),
    val version: Int = 1
) {
    fun withImage(image: PromptImage): SystemPrompt {
        return copy(images = images + image, version = version + 1)
    }

    fun fullPrompt(): String {
        val builder = StringBuilder(text)
        if (images.isNotEmpty()) {
            builder.append("\n\n[Attached Images: ${images.size}]")
            images.forEach { builder.append("\n- ${it.description}") }
        }
        return builder.toString()
    }
}

@Serializable
data class PromptImage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uri: String, // content:// or file://
    val mimeType: String,
    val description: String = "",
    val extractedText: String = "",
    val thumbnailUri: String? = null,
    @Serializable(with = InstantSerializer::class)
    val uploadedAt: Instant = Instant.now()
)

@Serializable
data class ModelConfig(
    val provider: ModelProvider = ModelProvider.LOCAL_QWEN,
    val modelName: String = "qwen2-0.5b-instruct-q4_k_m",
    val customEndpoint: String = "",
    val apiKey: String = "",
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val maxTokens: Int = 2048,
    val systemPromptId: String? = null,
    val useStreaming: Boolean = true,
    val timeoutSeconds: Int = 60
) {
    enum class ModelProvider(val value: String) {
        LOCAL_QWEN("local_qwen"),
        CUSTOM_OPENAI("custom_openai"),
        CUSTOM_OLLAMA("custom_ollama"),
        CUSTOM_VLLM("custom_vllm"),
        CUSTOM_OTHER("custom_other")
    }

    companion object {
        fun default(): ModelConfig = ModelConfig()
    }

    fun isLocal(): Boolean = provider == ModelProvider.LOCAL_QWEN

    fun isCustom(): Boolean = provider != ModelProvider.LOCAL_QWEN

    fun effectiveEndpoint(): String {
        return when (provider) {
            ModelProvider.CUSTOM_OPENAI -> if (customEndpoint.isNotBlank()) customEndpoint else "https://api.openai.com/v1"
            ModelProvider.CUSTOM_OLLAMA -> if (customEndpoint.isNotBlank()) customEndpoint else "http://localhost:11434/v1"
            ModelProvider.CUSTOM_VLLM -> if (customEndpoint.isNotBlank()) customEndpoint else "http://localhost:8000/v1"
            ModelProvider.CUSTOM_OTHER -> customEndpoint
            else -> ""
        }
    }
}

@Entity(tableName = "api_providers")
@Serializable
data class ApiProvider(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val providerType: ModelConfig.ModelProvider,
    val baseUrl: String,
    val apiKey: String = "",
    val models: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val isEnabled: Boolean = true,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now()
)

@Serializable
data class GenerationRequest(
    val messages: List<ChatMessage>,
    val config: ModelConfig,
    val systemPrompt: SystemPrompt? = null
)

@Serializable
data class GenerationResponse(
    val id: String,
    val content: String,
    val model: String,
    val usage: TokenUsage,
    val finishReason: String,
    val latencyMs: Long
)

@Serializable
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

@Serializable
data class StreamChunk(
    val id: String,
    val delta: String,
    val finishReason: String? = null,
    val usage: TokenUsage? = null
)

@Entity(tableName = "model_configs")
@Serializable
data class ModelConfigWithConversation(
    @PrimaryKey
    val conversationId: String,
    val provider: ModelConfig.ModelProvider,
    val modelName: String,
    val customEndpoint: String,
    val apiKey: String,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val maxTokens: Int,
    val systemPromptId: String?,
    val useStreaming: Boolean,
    val timeoutSeconds: Int
) {
    fun toModelConfig(): ModelConfig = ModelConfig(
        provider = provider,
        modelName = modelName,
        customEndpoint = customEndpoint,
        apiKey = apiKey,
        temperature = temperature,
        topP = topP,
        topK = topK,
        maxTokens = maxTokens,
        systemPromptId = systemPromptId,
        useStreaming = useStreaming,
        timeoutSeconds = timeoutSeconds
    )

    companion object {
        fun from(conversationId: String, config: ModelConfig): ModelConfigWithConversation {
            return ModelConfigWithConversation(
                conversationId = conversationId,
                provider = config.provider,
                modelName = config.modelName,
                customEndpoint = config.customEndpoint,
                apiKey = config.apiKey,
                temperature = config.temperature,
                topP = config.topP,
                topK = config.topK,
                maxTokens = config.maxTokens,
                systemPromptId = config.systemPromptId,
                useStreaming = config.useStreaming,
                timeoutSeconds = config.timeoutSeconds
            )
        }
    }
}