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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
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
        value?.let { json.decodeFromString(SystemPrompt.serializer(), it) }

    @TypeConverter
    fun toSystemPrompt(prompt: SystemPrompt?): String? =
        prompt?.let { json.encodeToString(SystemPrompt.serializer(), it) }

    @TypeConverter
    fun fromPromptImages(value: String?): List<PromptImage> =
        value?.let { json.decodeFromString(ListSerializer(PromptImage.serializer()), it) }
            ?: emptyList()

    @TypeConverter
    fun toPromptImages(images: List<PromptImage>): String =
        json.encodeToString(ListSerializer(PromptImage.serializer()), images)

    @TypeConverter
    fun fromModelConfig(value: String?): ModelConfig? =
        value?.let { json.decodeFromString(ModelConfig.serializer(), it) }

    @TypeConverter
    fun toModelConfig(config: ModelConfig?): String? =
        config?.let { json.encodeToString(ModelConfig.serializer(), it) }

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
