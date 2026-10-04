package com.cupcake.data.source.local.converters

import androidx.room.TypeConverter
import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.ModelConfig
import com.cupcake.data.model.PromptImage
import com.cupcake.data.model.SystemPrompt
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
        value?.let { json.decodeFromString(PromptImage.serializer().list, it) } ?: emptyList()

    @TypeConverter
    fun toPromptImages(images: List<PromptImage>): String =
        json.encodeToString(PromptImage.serializer().list, images)

    @TypeConverter
    fun fromModelConfig(value: String?): ModelConfig? =
        value?.let { json.decodeFromString(ModelConfig.serializer(), it) }

    @TypeConverter
    fun toModelConfig(config: ModelConfig?): String? =
        config?.let { json.encodeToString(ModelConfig.serializer(), it) }

    @TypeConverter
    fun fromMetadata(value: String?): Map<String, Any> =
        value?.let { json.decodeFromString(MapSerializer(), it) } ?: emptyMap()

    @TypeConverter
    fun toMetadata(map: Map<String, Any>): String =
        json.encodeToString(MapSerializer(), map)
}

private object MapSerializer : kotlinx.serialization.KSerializer<Map<String, Any>> {
    override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor =
        kotlinx.serialization.descriptors.MapSerializer(String.serializer(), AnySerializer()).descriptor

    override fun serialize(encoder: kotlinx.serialization.Encoder, value: Map<String, Any>) {
        val mapEncoder = encoder.encodeMap(descriptor)
        value.forEach { (key, val) ->
            mapEncoder.encodeMapKey(key)
            AnySerializer().serialize(mapEncoder.encodeMapValue(), val)
        }
        mapEncoder.finish()
    }

    override fun deserialize(decoder: kotlinx.serialization.Decoder): Map<String, Any> {
        val mapDecoder = decoder.decodeMap(descriptor)
        val result = mutableMapOf<String, Any>()
        while (true) {
            val hasNext = mapDecoder.decodeMapKey()
            if (!hasNext) break
            val key = mapDecoder.decodeString()
            val value = AnySerializer().deserialize(mapDecoder.decodeMapValue())
            result[key] = value
        }
        mapDecoder.finish()
        return result
    }
}

private object AnySerializer : kotlinx.serialization.KSerializer<Any> {
    override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor =
        kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("Any", kotlinx.serialization.descriptors.PrimitiveKind.STRING)

    override fun serialize(encoder: kotlinx.serialization.Encoder, value: Any) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: kotlinx.serialization.Decoder): Any {
        return decoder.decodeString()
    }
}