package com.cupcake.data

import com.cupcake.data.model.Character
import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.ModelConfig
import com.cupcake.data.model.ModelConfigWithConversation
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelsTest {

    @Test
    fun `chat message factories set roles`() {
        assertThat(ChatMessage.system("sys", "c1").role)
            .isEqualTo(ChatMessage.MessageRole.SYSTEM)
        assertThat(ChatMessage.user("hi", "c1").role)
            .isEqualTo(ChatMessage.MessageRole.USER)
        val assistant = ChatMessage.assistant("hello", "c1", "qwen")
        assertThat(assistant.role).isEqualTo(ChatMessage.MessageRole.ASSISTANT)
        assertThat(assistant.modelUsed).isEqualTo("qwen")
        assertThat(assistant.conversationId).isEqualTo("c1")
    }

    @Test
    fun `default model config is local qwen`() {
        val config = ModelConfig.default()
        assertThat(config.provider).isEqualTo(ModelConfig.ModelProvider.LOCAL_QWEN)
        assertThat(config.isLocal()).isTrue()
        assertThat(config.isCustom()).isFalse()
        assertThat(config.effectiveEndpoint()).isEmpty()
    }

    @Test
    fun `effective endpoints fall back per provider`() {
        val openai = ModelConfig(provider = ModelConfig.ModelProvider.CUSTOM_OPENAI)
        assertThat(openai.effectiveEndpoint()).isEqualTo("https://api.openai.com/v1")
        assertThat(openai.isCustom()).isTrue()

        val ollama = ModelConfig(
            provider = ModelConfig.ModelProvider.CUSTOM_OLLAMA,
            customEndpoint = "http://192.168.1.5:11434/v1"
        )
        assertThat(ollama.effectiveEndpoint()).isEqualTo("http://192.168.1.5:11434/v1")

        val other = ModelConfig(
            provider = ModelConfig.ModelProvider.CUSTOM_OTHER,
            customEndpoint = "https://example.com/v1"
        )
        assertThat(other.effectiveEndpoint()).isEqualTo("https://example.com/v1")
    }

    @Test
    fun `character prompt addition appends`() {
        val character = Character(
            id = "c",
            name = "Test",
            description = "d",
            systemPrompt = "base",
            avatar = "x",
            personality = "friendly"
        )
        val updated = character.withPromptAddition("extra")
        assertThat(updated.systemPrompt).isEqualTo("base\n\nextra")
        assertThat(updated.id).isEqualTo("c")
    }

    @Test
    fun `model config conversation round trip preserves fields`() {
        val config = ModelConfig(
            provider = ModelConfig.ModelProvider.CUSTOM_OPENAI,
            modelName = "gpt-4o-mini",
            customEndpoint = "https://api.openai.com/v1",
            apiKey = "sk-test",
            temperature = 0.5f,
            topP = 0.8f,
            topK = 20,
            maxTokens = 512,
            systemPromptId = "sp1",
            useStreaming = false,
            timeoutSeconds = 30
        )
        val entity = ModelConfigWithConversation.from("conv1", config)
        assertThat(entity.conversationId).isEqualTo("conv1")
        val restored = entity.toModelConfig()
        assertThat(restored).isEqualTo(config)
    }
}
