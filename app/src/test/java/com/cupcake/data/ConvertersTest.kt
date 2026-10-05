package com.cupcake.data

import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.ModelConfig
import com.cupcake.data.model.PromptImage
import com.cupcake.data.model.SystemPrompt
import com.cupcake.data.source.local.converters.Converters
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.util.UUID

private fun fixedInstant(millis: Long) = Instant.ofEpochMilli(millis)

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `instant round trips through epoch millis`() {
        val now = Instant.ofEpochMilli(1700000000000L)
        val stored = converters.toInstant(now)
        assertThat(stored).isEqualTo(1700000000000L)
        assertThat(converters.fromInstant(stored)).isEqualTo(now)
        assertThat(converters.fromInstant(null)).isNull()
    }

    @Test
    fun `message role round trips`() {
        for (role in ChatMessage.MessageRole.values()) {
            assertThat(converters.fromMessageRole(converters.toMessageRole(role)))
                .isEqualTo(role)
        }
    }

    @Test
    fun `model provider round trips`() {
        for (provider in ModelConfig.ModelProvider.values()) {
            assertThat(converters.fromModelProvider(converters.toModelProvider(provider)))
                .isEqualTo(provider)
        }
    }

    @Test
    fun `system prompt round trips as json`() {
        val prompt = SystemPrompt(
            id = "sp1",
            name = "Helper",
            text = "Be helpful",
            createdAt = fixedInstant(1700000000000L),
            images = listOf(
                PromptImage(
                    id = "img1",
                    uri = "content://img/1",
                    mimeType = "image/png",
                    description = "diagram",
                    extractedText = "a -> b",
                    uploadedAt = fixedInstant(1700000000000L)
                )
            )
        )
        val restored = converters.fromSystemPrompt(converters.toSystemPrompt(prompt))
        assertThat(restored).isEqualTo(prompt)
        assertThat(converters.fromSystemPrompt(null)).isNull()
    }

    @Test
    fun `prompt images list round trips`() {
        assertThat(converters.fromPromptImages(null)).isEmpty()
        val images = listOf(
            PromptImage(
                id = "a", uri = "u1", mimeType = "image/jpeg",
                uploadedAt = fixedInstant(1700000000001L)
            ),
            PromptImage(
                id = "b", uri = "u2", mimeType = "image/png",
                uploadedAt = fixedInstant(1700000000002L)
            )
        )
        assertThat(converters.fromPromptImages(converters.toPromptImages(images)))
            .isEqualTo(images)
    }

    @Test
    fun `model config round trips as json`() {
        val config = ModelConfig(
            provider = ModelConfig.ModelProvider.CUSTOM_OLLAMA,
            modelName = "llama3",
            customEndpoint = "http://localhost:11434/v1"
        )
        assertThat(converters.fromModelConfig(converters.toModelConfig(config)))
            .isEqualTo(config)
    }

    @Test
    fun `string list and map round trip`() {
        assertThat(converters.fromStringList(null)).isEmpty()
        assertThat(converters.fromStringList(converters.toStringList(listOf("a", "b"))))
            .containsExactly("a", "b")

        assertThat(converters.fromStringMap(null)).isEmpty()
        val map = mapOf("k1" to "v1", "k2" to "v2")
        assertThat(converters.fromStringMap(converters.toStringMap(map))).isEqualTo(map)
    }

    @Test
    fun `metadata map coerces values to string`() {
        val restored = converters.fromMetadata(converters.toMetadata(mapOf("n" to 1)))
        assertThat(restored["n"]).isEqualTo("1")
    }

    @Test
    fun `uuid default ids are unique`() {
        val a = SystemPrompt(id = UUID.randomUUID().toString(), name = "a", text = "t")
        val b = SystemPrompt(id = UUID.randomUUID().toString(), name = "b", text = "t")
        assertThat(a.id).isNotEqualTo(b.id)
    }
}
