package com.cupcake.data.source.local.converters

import androidx.room.TypeConverter
import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.ModelConfig
import com.cupcake.data.model.PromptImage
import com.cupcake.data.model.SystemPrompt
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.time.Instant

private val json = Json { ignoreUnknownKeys = true }

class Converters {

    @TypeConverter
    fun fromInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun toInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun fromMessageRole(value: String): ChatMessage.MessageRole =
        ChatMessage.MessageRole.valueOf(value.uppercase())

    @TypeConverter
    fun toMessageRole(role: ChatMessage.MessageRole): String = role.name

    @TypeConverter
    fun fromModelProvider(value: String): ModelConfig.ModelProvider =
        ModelConfig.ModelProvider.valueOf(value.uppercase())

    @TypeConverter
    fun toModelProvider(provider: ModelConfig.ModelProvider): String = provider.name

    @TypeConverter
    fun fromSystemPrompt(value: String?): SystemPrompt? =
        value?.let { json.decodeFromString(SystemPromptSerializer, it) }

    @TypeConverter
    fun toSystemPrompt(prompt: SystemPrompt?): String? =
        prompt?.let { json.encodeToString(SystemPromptSerializer, it) }

    @TypeConverter
    fun fromPromptImages(value: String?): List<PromptImage> =
        value?.let { json.decodeFromString(ListSerializer(PromptImageSerializer), it) }
            ?: emptyList()

    @TypeConverter
    fun toPromptImages(images: List<PromptImage>): String =
        json.encodeToString(ListSerializer(PromptImageSerializer), images)

    @TypeConverter
    fun fromModelConfig(value: String?): ModelConfig? =
        value?.let { json.decodeFromString(ModelConfigSerializer, it) }

    @TypeConverter
    fun toModelConfig(config: ModelConfig?): String? =
        config?.let { json.encodeToString(ModelConfigSerializer, it) }

    @TypeConverter
    fun fromMetadata(value: String?): Map<String, Any> =
        value?.let { json.decodeFromString(AnyMapSerializer, it) } ?: emptyMap()

    @TypeConverter
    fun toMetadata(map: Map<String, Any>): String =
        json.encodeToString(AnyMapSerializer, map)

    @TypeConverter
    fun fromStringList(value: String?): List<String> =
        value?.let { json.decodeFromString(ListSerializer(String.serializer()), it) }
            ?: emptyList()

    @TypeConverter
    fun toStringList(list: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), list)

    @TypeConverter
    fun fromStringMap(value: String?): Map<String, String> =
        value?.let {
            json.decodeFromString(
                MapSerializer(String.serializer(), String.serializer()), it
            )
        } ?: emptyMap()

    @TypeConverter
    fun toStringMap(map: Map<String, String>): String =
        json.encodeToString(
            MapSerializer(String.serializer(), String.serializer()), map
        )
}

// Map<String, Any> with all values coerced to String.
private object AnyMapSerializer : KSerializer<Map<String, Any>> {
    private val delegate = MapSerializer(String.serializer(), AnySerializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, Any>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): Map<String, Any> {
        return delegate.deserialize(decoder)
    }
}

private object AnySerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Any", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Any) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Any {
        return decoder.decodeString()
    }
}

// Custom serializer for SystemPrompt
private object SystemPromptSerializer : KSerializer<SystemPrompt> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SystemPrompt") {
        element<String>("id")
        element<String>("name")
        element<String>("text")
        element("images", ListSerializer(PromptImageSerializer).descriptor)
        element("createdAt", PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG))
        element<Int>("version")
    }

    override fun serialize(encoder: Encoder, value: SystemPrompt) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.id)
            encodeStringElement(descriptor, 1, value.name)
            encodeStringElement(descriptor, 2, value.text)
            encodeSerializableElement(descriptor, 3, ListSerializer(PromptImageSerializer), value.images)
            encodeLongElement(descriptor, 4, value.createdAt.toEpochMilli())
            encodeIntElement(descriptor, 5, value.version)
        }
    }

    override fun deserialize(decoder: Decoder): SystemPrompt {
        return decoder.decodeStructure(descriptor) {
            var id = ""
            var name = ""
            var text = ""
            var images = emptyList<PromptImage>()
            var createdAt = Instant.now()
            var version = 1

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break
                    0 -> id = decodeStringElement(descriptor, 0)
                    1 -> name = decodeStringElement(descriptor, 1)
                    2 -> text = decodeStringElement(descriptor, 2)
                    3 -> images = decodeSerializableElement(descriptor, 3, ListSerializer(PromptImageSerializer))
                    4 -> createdAt = Instant.ofEpochMilli(decodeLongElement(descriptor, 4))
                    5 -> version = decodeIntElement(descriptor, 5)
                    else -> error("Unexpected index: $index")
                }
            }
            SystemPrompt(id, name, text, images, createdAt, version)
        }
    }
}

// Custom serializer for PromptImage
private object PromptImageSerializer : KSerializer<PromptImage> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PromptImage") {
        element<String>("id")
        element<String>("uri")
        element<String>("mimeType")
        element<String>("description")
        element<String>("extractedText")
        element<String?>("thumbnailUri")
        element("uploadedAt", PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG))
    }

    override fun serialize(encoder: Encoder, value: PromptImage) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.id)
            encodeStringElement(descriptor, 1, value.uri)
            encodeStringElement(descriptor, 2, value.mimeType)
            encodeStringElement(descriptor, 3, value.description)
            encodeStringElement(descriptor, 4, value.extractedText)
            encodeNullableSerializableElement(descriptor, 5, String.serializer(), value.thumbnailUri)
            encodeLongElement(descriptor, 6, value.uploadedAt.toEpochMilli())
        }
    }

    override fun deserialize(decoder: Decoder): PromptImage {
        return decoder.decodeStructure(descriptor) {
            var id = ""
            var uri = ""
            var mimeType = ""
            var description = ""
            var extractedText = ""
            var thumbnailUri: String? = null
            var uploadedAt = Instant.now()

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break
                    0 -> id = decodeStringElement(descriptor, 0)
                    1 -> uri = decodeStringElement(descriptor, 1)
                    2 -> mimeType = decodeStringElement(descriptor, 2)
                    3 -> description = decodeStringElement(descriptor, 3)
                    4 -> extractedText = decodeStringElement(descriptor, 4)
                    5 -> thumbnailUri = decodeNullableSerializableElement(descriptor, 5, String.serializer())
                    6 -> uploadedAt = Instant.ofEpochMilli(decodeLongElement(descriptor, 6))
                    else -> error("Unexpected index: $index")
                }
            }
            PromptImage(id, uri, mimeType, description, extractedText, thumbnailUri, uploadedAt)
        }
    }
}

// Custom serializer for ModelConfig
private object ModelConfigSerializer : KSerializer<ModelConfig> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ModelConfig") {
        element<String>("provider")
        element<String>("modelName")
        element<String>("customEndpoint")
        element<String>("apiKey")
        element<Float>("temperature")
        element<Float>("topP")
        element<Int>("topK")
        element<Int>("maxTokens")
        element<String?>("systemPromptId")
        element<Boolean>("useStreaming")
        element<Int>("timeoutSeconds")
    }

    override fun serialize(encoder: Encoder, value: ModelConfig) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.provider.name)
            encodeStringElement(descriptor, 1, value.modelName)
            encodeStringElement(descriptor, 2, value.customEndpoint)
            encodeStringElement(descriptor, 3, value.apiKey)
            encodeFloatElement(descriptor, 4, value.temperature)
            encodeFloatElement(descriptor, 5, value.topP)
            encodeIntElement(descriptor, 6, value.topK)
            encodeIntElement(descriptor, 7, value.maxTokens)
            encodeNullableSerializableElement(descriptor, 8, String.serializer(), value.systemPromptId)
            encodeBooleanElement(descriptor, 9, value.useStreaming)
            encodeIntElement(descriptor, 10, value.timeoutSeconds)
        }
    }

    override fun deserialize(decoder: Decoder): ModelConfig {
        return decoder.decodeStructure(descriptor) {
            var provider = ModelConfig.ModelProvider.CUSTOM_OLLAMA
            var modelName = "qwen2:0.5b"
            var customEndpoint = ""
            var apiKey = ""
            var temperature = 0.7f
            var topP = 0.9f
            var topK = 40
            var maxTokens = 2048
            var systemPromptId: String? = null
            var useStreaming = true
            var timeoutSeconds = 60

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break
                    0 -> {
                        val str = decodeStringElement(descriptor, 0)
                        provider = try { ModelConfig.ModelProvider.valueOf(str.uppercase()) } catch (e: Exception) { ModelConfig.ModelProvider.CUSTOM_OLLAMA }
                    }
                    1 -> modelName = decodeStringElement(descriptor, 1)
                    2 -> customEndpoint = decodeStringElement(descriptor, 2)
                    3 -> apiKey = decodeStringElement(descriptor, 3)
                    4 -> temperature = decodeFloatElement(descriptor, 4)
                    5 -> topP = decodeFloatElement(descriptor, 5)
                    6 -> topK = decodeIntElement(descriptor, 6)
                    7 -> maxTokens = decodeIntElement(descriptor, 7)
                    8 -> systemPromptId = decodeNullableSerializableElement(descriptor, 8, String.serializer())
                    9 -> useStreaming = decodeBooleanElement(descriptor, 9)
                    10 -> timeoutSeconds = decodeIntElement(descriptor, 10)
                    else -> error("Unexpected index: $index")
                }
            }
            ModelConfig(provider, modelName, customEndpoint, apiKey, temperature, topP, topK, maxTokens, systemPromptId, useStreaming, timeoutSeconds)
        }
    }
}
