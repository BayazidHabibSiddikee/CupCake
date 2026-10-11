package com.cupcake.data.source.local.converters

import androidx.room.TypeConverter
import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.ModelConfig
import com.cupcake.data.model.PromptImage
import com.cupcake.data.model.SystemPrompt
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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
        value?.let { json.decodeFromString(ListSerializer(serializer<String>()), it) }
            ?: emptyList()

    @TypeConverter
    fun toStringList(list: List<String>): String =
        json.encodeToString(ListSerializer(serializer<String>()), list)

    @TypeConverter
    fun fromStringMap(value: String?): Map<String, String> =
        value?.let { json.decodeFromString(MapSerializer(serializer<String>(), serializer<String>()), it) } ?: emptyMap()

    @TypeConverter
    fun toStringMap(map: Map<String, String>): String =
        json.encodeToString(MapSerializer(serializer<String>(), serializer<String>()), map)
}

// Map<String, Any> with all values coerced to String.
private object AnyMapSerializer : KSerializer<Map<String, Any>> {
    private val delegate = MapSerializer(serializer<String>(), AnySerializer)

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
        element<List<PromptImage>>("images")
        element<Instant>("createdAt")
        element<Int>("version")
    }

    override fun serialize(encoder: Encoder, value: SystemPrompt) {
        val output = encoder.encodeStructure(descriptor)
        output.encodeStringElement(descriptor, 0, value.id)
        output.encodeStringElement(descriptor, 1, value.name)
        output.encodeStringElement(descriptor, 2, value.text)
        output.encodeSerializableElement(descriptor, 3, PromptImageSerializer, value.images)
        output.encodeLongElement(descriptor, 4, value.createdAt.toEpochMilli())
        output.encodeIntElement(descriptor, 5, value.version)
        output.finishStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): SystemPrompt {
        val input = decoder.decodeStructure(descriptor)
        val id = input.decodeStringElement(descriptor, 0)
        val name = input.decodeStringElement(descriptor, 1)
        val text = input.decodeStringElement(descriptor, 2)
        val images = input.decodeSerializableElement(descriptor, 3, PromptImageSerializer)
        val createdAt = Instant.ofEpochMilli(input.decodeLongElement(descriptor, 4))
        val version = input.decodeIntElement(descriptor, 5)
        input.finishStructure(descriptor)
        return SystemPrompt(id, name, text, images, createdAt, version)
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
        element<Instant>("uploadedAt")
    }

    override fun serialize(encoder: Encoder, value: PromptImage) {
        val output = encoder.encodeStructure(descriptor)
        output.encodeStringElement(descriptor, 0, value.id)
        output.encodeStringElement(descriptor, 1, value.uri)
        output.encodeStringElement(descriptor, 2, value.mimeType)
        output.encodeStringElement(descriptor, 3, value.description)
        output.encodeStringElement(descriptor, 4, value.extractedText)
        output.encodeNullableSerializableElement(descriptor, 5, serializer<String>(), value.thumbnailUri)
        output.encodeLongElement(descriptor, 6, value.uploadedAt.toEpochMilli())
        output.finishStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): PromptImage {
        val input = decoder.decodeStructure(descriptor)
        val id = input.decodeStringElement(descriptor, 0)
        val uri = input.decodeStringElement(descriptor, 1)
        val mimeType = input.decodeStringElement(descriptor, 2)
        val description = input.decodeStringElement(descriptor, 3)
        val extractedText = input.decodeStringElement(descriptor, 4)
        val thumbnailUri = input.decodeNullableSerializableElement(descriptor, 5, serializer<String>())
        val uploadedAt = Instant.ofEpochMilli(input.decodeLongElement(descriptor, 6))
        input.finishStructure(descriptor)
        return PromptImage(id, uri, mimeType, description, extractedText, thumbnailUri, uploadedAt)
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
        val output = encoder.encodeStructure(descriptor)
        output.encodeStringElement(descriptor, 0, value.provider.name)
        output.encodeStringElement(descriptor, 1, value.modelName)
        output.encodeStringElement(descriptor, 2, value.customEndpoint)
        output.encodeStringElement(descriptor, 3, value.apiKey)
        output.encodeFloatElement(descriptor, 4, value.temperature)
        output.encodeFloatElement(descriptor, 5, value.topP)
        output.encodeIntElement(descriptor, 6, value.topK)
        output.encodeIntElement(descriptor, 7, value.maxTokens)
        output.encodeNullableSerializableElement(descriptor, 8, serializer<String>(), value.systemPromptId)
        output.encodeBooleanElement(descriptor, 9, value.useStreaming)
        output.encodeIntElement(descriptor, 10, value.timeoutSeconds)
        output.finishStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): ModelConfig {
        val input = decoder.decodeStructure(descriptor)
        val provider = ModelConfig.ModelProvider.valueOf(input.decodeStringElement(descriptor, 0).uppercase())
        val modelName = input.decodeStringElement(descriptor, 1)
        val customEndpoint = input.decodeStringElement(descriptor, 2)
        val apiKey = input.decodeStringElement(descriptor, 3)
        val temperature = input.decodeFloatElement(descriptor, 4)
        val topP = input.decodeFloatElement(descriptor, 5)
        val topK = input.decodeIntElement(descriptor, 6)
        val maxTokens = input.decodeIntElement(descriptor, 7)
        val systemPromptId = input.decodeNullableSerializableElement(descriptor, 8, serializer<String>())
        val useStreaming = input.decodeBooleanElement(descriptor, 9)
        val timeoutSeconds = input.decodeIntElement(descriptor, 10)
        input.finishStructure(descriptor)
        return ModelConfig(provider, modelName, customEndpoint, apiKey, temperature, topP, topK, maxTokens, systemPromptId, useStreaming, timeoutSeconds)
    }
}
