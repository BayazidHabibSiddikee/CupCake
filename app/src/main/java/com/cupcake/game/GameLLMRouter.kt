package com.cupcake.game

import android.util.Log
import com.cupcake.ai.CharacterManager
import com.cupcake.ai.LlamaEngine
import com.cupcake.data.model.ApiProvider
import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.ModelConfig
import com.cupcake.data.source.remote.ApiClient
import com.cupcake.domain.repository.ModelRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves which model the in-game character speaks through, matching the chat
 * screen: an enabled remote provider when the user has configured one, otherwise
 * the bundled on-device GGUF model.
 *
 * Game code used to hardcode `http://localhost:11434/v1`, which on a phone points
 * at the phone itself and so silently failed — the "bot" was really only ever
 * emitting canned fallback lines. This router uses the same sources as chat.
 */
@Singleton
class GameLLMRouter @Inject constructor(
    private val apiClient: ApiClient,
    private val llamaEngine: LlamaEngine,
    private val modelRepository: ModelRepository,
    private val characterManager: CharacterManager
) {

    private companion object {
        const val TAG = "GameLLMRouter"
        const val GAME_CONVERSATION = "game"
    }

    /**
     * Produce one in-game line of dialogue. Returns "" when no model is
     * reachable, letting callers fall back to canned in-character lines.
     */
    suspend fun generate(
        characterId: String,
        gameContext: String,
        userPrompt: String,
        temperature: Float = 0.8f,
        maxTokens: Int = 96
    ): String {
        val systemPrompt = characterManager.getSystemPromptForCharacter(characterId, gameContext)
        val raw = try {
            val provider = modelRepository.getEnabledProviders().first().firstOrNull()
            if (provider != null && provider.providerType != ModelConfig.ModelProvider.LOCAL_LLAMA) {
                generateRemote(provider, systemPrompt, userPrompt, temperature, maxTokens)
            } else {
                generateLocal(systemPrompt, userPrompt)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Game generation failed: ${e.message}")
            ""
        }
        return ToneGuard.sanitize(raw)
    }

    private suspend fun generateRemote(
        provider: ApiProvider,
        systemPrompt: String,
        userPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): String {
        val config = ModelConfig(
            provider = provider.providerType,
            modelName = provider.models.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: defaultModelName(provider.providerType),
            customEndpoint = provider.baseUrl,
            apiKey = provider.apiKey,
            temperature = temperature,
            topP = 0.9f,
            topK = 40,
            maxTokens = maxTokens,
            useStreaming = true
        )
        val messages = listOf(
            ChatMessage.system(systemPrompt, GAME_CONVERSATION),
            ChatMessage.user(userPrompt, GAME_CONVERSATION)
        )
        val builder = StringBuilder()
        apiClient.generateRemote(messages, config).collect { chunk ->
            if (chunk.delta.isNotEmpty()) builder.append(chunk.delta)
        }
        return builder.toString()
    }

    private suspend fun generateLocal(systemPrompt: String, userPrompt: String): String {
        if (!llamaEngine.isReady()) return ""
        val prompt = "$systemPrompt\n\nUser: $userPrompt\nAssistant:"
        val builder = StringBuilder()
        for (token in llamaEngine.generateStream(prompt)) {
            builder.append(token)
        }
        return builder.toString()
    }

    private fun defaultModelName(provider: ModelConfig.ModelProvider): String = when (provider) {
        ModelConfig.ModelProvider.CUSTOM_OLLAMA -> "qwen2.5:0.5b"
        ModelConfig.ModelProvider.LOCAL_LLAMA -> "liltii-v0.2-small-q2_k.gguf"
        else -> "gpt-3.5-turbo"
    }
}
