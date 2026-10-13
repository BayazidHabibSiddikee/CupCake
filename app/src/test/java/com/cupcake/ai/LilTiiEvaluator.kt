package com.cupcake.ai

import android.content.Context
import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.ModelConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class LilTiiEvaluator(
    private val llamaEngine: LlamaEngine,
    private val ollamaEngine: OllamaEngine,
    private val appContext: Context
) {
    data class IterationInput(
        val index: Int,
        val userMessage: String,
        val expectedEmotion: String? = null
    )

    suspend fun evaluate(
        config: ModelConfig,
        iterations: Int = 20,
        timeoutMs: Long = 60_000
    ): LilTiiEvalReport {
        val inputs = generateIterations(iterations)
        val results = withContext(Dispatchers.IO) {
            inputs.map { input ->
                async {
                    runIteration(config, input, timeoutMs)
                }
            }.awaitAll()
        }

        return buildReport(results)
    }

    private suspend fun runIteration(
        config: ModelConfig,
        input: IterationInput,
        timeoutMs: Long
    ): LilTiiEvalResult {
        val prompt = LilTiiPromptTemplate.buildPrompt(input.userMessage)
        val rawOutput = try {
            withTimeout(timeoutMs) {
                if (config.isLocal()) {
                    collectLocalOutput(prompt)
                } else {
                    collectOllamaOutput(config, prompt)
                }
            }
        } catch (e: Exception) {
            return LilTiiEvalResult(
                iteration = input.index,
                prompt = prompt,
                rawOutput = "",
                hasEmotionTag = false,
                emotionTag = null,
                validEmotionTag = false,
                hasAction = false,
                action = null,
                hasBangla = false,
                tagAtStart = false,
                score = 0,
                notes = "Error: ${e.message}"
            )
        }

        return scoreOutput(input, prompt, rawOutput)
    }

    private suspend fun collectLocalOutput(prompt: String): String {
        val channel = llamaEngine.generateStream(prompt)
        val sb = StringBuilder()
        channel.consumeEach { chunk: String ->
            sb.append(chunk)
        }
        return sb.toString()
    }

    private suspend fun collectOllamaOutput(
        config: ModelConfig,
        prompt: String
    ): String {
        val messages = listOf(
            OllamaEngine.OllamaMessage(role = "user", content = prompt)
        )
        val resultChannel = Channel<String>(1)
        ollamaEngine.generateStream(
            baseUrl = config.effectiveEndpoint(),
            config = config,
            messages = messages,
            callback = object : OllamaEngine.GenerateCallback {
                private val sb = StringBuilder()
                override fun onToken(token: String) {
                    sb.append(token)
                }
                override fun onComplete() {
                    resultChannel.trySend(sb.toString())
                }
                override fun onError(error: String) {
                    resultChannel.close(Exception(error))
                }
            }
        )
        return resultChannel.receive()
    }

    private fun scoreOutput(
        input: IterationInput,
        prompt: String,
        rawOutput: String
    ): LilTiiEvalResult {
        val trimmed = rawOutput.trim()
        val emotionTagRegex = Regex("""^\[(sleepy|confused|surprised|happy|angry|sad|thinking|lovely)\]\s*(.*)""", RegexOption.IGNORE_CASE)
        val actionRegex = Regex("""^\*(.+?)\*\s*""")

        val tagMatch = emotionTagRegex.find(trimmed)
        val hasEmotionTag = tagMatch != null
        val emotionTag = tagMatch?.groupValues?.getOrNull(1)?.lowercase()
        val validEmotionTag = emotionTag in setOf(
            "sleepy", "confused", "surprised", "happy", "angry", "sad", "thinking", "lovely"
        )
        val tagAtStart = trimmed.startsWith("[") && hasEmotionTag

        val afterTag = tagMatch?.groupValues?.getOrNull(2) ?: trimmed
        val actionMatch = actionRegex.find(afterTag)
        val hasAction = actionMatch != null
        val action = actionMatch?.groupValues?.getOrNull(1)

        val hasBangla = trimmed.any { it in 'ঀ'..'৿' }

        var score = 0
        val notes = mutableListOf<String>()

        if (hasEmotionTag) score++
        else notes.add("missing_emotion_tag")
        if (validEmotionTag) score++
        else notes.add("invalid_emotion_tag=$emotionTag")
        if (tagAtStart) score++
        else notes.add("tag_not_at_start")
        if (hasAction) score++
        else notes.add("missing_action")
        if (hasBangla) score++
        else notes.add("no_bangla")

        if (input.expectedEmotion != null && emotionTag == input.expectedEmotion.lowercase()) {
            score++
            notes.add("expected_emotion_match")
        } else if (input.expectedEmotion != null) {
            notes.add("expected_emotion_mismatch: expected=${input.expectedEmotion}, actual=$emotionTag")
        }

        return LilTiiEvalResult(
            iteration = input.index,
            prompt = prompt,
            rawOutput = rawOutput,
            hasEmotionTag = hasEmotionTag,
            emotionTag = emotionTag,
            validEmotionTag = validEmotionTag,
            hasAction = hasAction,
            action = action,
            hasBangla = hasBangla,
            tagAtStart = tagAtStart,
            score = score,
            notes = notes.joinToString("; ")
        )
    }

    private fun generateIterations(count: Int): List<IterationInput> {
        val templates = listOf(
            "hey what are you doing?" to "sleepy",
            "what is the capital of bangladesh?" to "confused",
            "wake up!" to "surprised",
            "i love you" to "happy",
            "you're so annoying" to "angry",
            "i'm sad today" to "sad",
            "what do you think about this?" to "thinking",
            "haha that's funny" to "happy",
            "wow really?!" to "surprised",
            "i'm so lonely" to "sad",
            "stop it right now!" to "angry",
            "do you like me?" to "thinking",
            "good morning!" to "happy",
            "i had a bad day" to "sad",
            "you're the best!" to "happy",
            "keno amake chere dile?" to "sad",
            "play with me" to "happy",
            "i'm bored" to "sleepy",
            "surprise!" to "surprised",
            "i hate this" to "angry"
        )

        return (0 until count).map { index ->
            val templateIndex = index % templates.size
            val (message, expected) = templates[templateIndex]
            IterationInput(
                index = index + 1,
                userMessage = message,
                expectedEmotion = expected
            )
        }
    }

    private fun buildReport(results: List<LilTiiEvalResult>): LilTiiEvalReport {
        val passCount = results.count { it.passed }
        val failCount = results.size - passCount
        val emotionTagDistribution = results.mapNotNull { it.emotionTag }.groupingBy { it }.eachCount()
        val avgScore = if (results.isNotEmpty()) results.map { it.score }.average() else 0.0
        val overallPassRate = if (results.isNotEmpty()) passCount.toDouble() / results.size else 0.0

        return LilTiiEvalReport(
            iterations = results.size,
            results = results,
            passCount = passCount,
            failCount = failCount,
            emotionTagDistribution = emotionTagDistribution,
            avgScore = avgScore,
            overallPassRate = overallPassRate
        )
    }
}
