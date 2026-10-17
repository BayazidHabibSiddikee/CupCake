package com.cupcake.game

import android.util.Log
import com.cupcake.ai.CharacterManager
import com.cupcake.ai.EmotionGrammar
import com.cupcake.ai.LilTiiPromptTemplate
import com.cupcake.ai.LlamaEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces the companion's in-game dialogue from the bundled on-device GGUF.
 *
 * Games always run on the local model: it is already warm (see [com.cupcake.ai.ModelPreloader]),
 * works offline, and keeps the playful LilTii voice. Remote providers are intentionally
 * ignored here so a half-configured cloud key can never turn the game bot into silence.
 *
 * Lines are cached in a small LRU because game prompts repeat heavily (same board,
 * same mood) and a warm hit returns instantly without touching the CPU sampler.
 */
@Singleton
class GameLLMRouter @Inject constructor(
    private val llamaEngine: LlamaEngine,
    private val characterManager: CharacterManager
) {

    private companion object {
        const val TAG = "GameLLMRouter"
        const val MAX_CACHED_LINES = 96
        const val MAX_SYSTEM_CHARS = 1400
    }

    private val lineCache = object : LinkedHashMap<String, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > MAX_CACHED_LINES
    }

    /**
     * Produce one in-game line of dialogue. Returns "" when the local model is not
     * ready, letting callers fall back to canned in-character lines.
     */
    suspend fun generate(
        characterId: String,
        gameContext: String,
        userPrompt: String,
        temperature: Float = 0.8f,
        maxTokens: Int = 96
    ): String {
        val cacheKey = "$characterId\u0000$gameContext\u0000$userPrompt"
        synchronized(lineCache) {
            lineCache[cacheKey]?.let { return it }
        }

        val systemPrompt = characterManager.getSystemPromptForCharacter(characterId, gameContext)
        val raw = try {
            generateLocal(systemPrompt, userPrompt)
        } catch (e: Exception) {
            Log.w(TAG, "Game generation failed: ${e.message}")
            ""
        }
        val line = ToneGuard.sanitize(raw)
        if (line.isNotBlank()) {
            synchronized(lineCache) { lineCache[cacheKey] = line }
        }
        return line
    }

    fun clearCache() {
        synchronized(lineCache) { lineCache.clear() }
    }

    private suspend fun generateLocal(systemPrompt: String, userPrompt: String): String {
        if (!llamaEngine.isReady()) return ""

        val styled = buildString {
            append(systemPrompt.take(MAX_SYSTEM_CHARS))
            append("\n")
            append(LilTiiPromptTemplate.CUTE_STYLE)
        }

        val prompt = buildString {
            append("<|im_start|>system\n")
            append(styled)
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append(userPrompt)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }

        val builder = StringBuilder()
        // Constrain the little model to the Persona's [tag] *action* reply shape.
        llamaEngine.setGrammar(EmotionGrammar.TAG_ACTION)
        for (token in llamaEngine.generateStream(prompt)) {
            builder.append(token)
        }
        return builder.toString()
    }
}
