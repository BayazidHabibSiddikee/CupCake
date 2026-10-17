package com.cupcake.ai

import android.content.Context
import com.cupcake.data.model.ModelConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins the *answer contract* the on-device companion is supposed to honour:
 * `[emotion] *action* reply` with Banglish text, plus the prompt that asks for it
 * and the scorer that grades it. Runs against a stubbed engine so it is offline and
 * deterministic; the live model is graded by [LilTiiEvaluator] on a real device.
 */
class AnswerFormatTest {

    private lateinit var llamaEngine: LlamaEngine
    private lateinit var evaluator: LilTiiEvaluator

    @Before
    fun setUp() {
        llamaEngine = mockk(relaxed = true)
        val ollamaEngine = mockk<OllamaEngine>(relaxed = true)
        val appContext = mockk<Context>(relaxed = true)
        evaluator = LilTiiEvaluator(llamaEngine, ollamaEngine, appContext)
    }

    private fun engineReplies(text: String) {
        every { llamaEngine.generateStream(any()) } answers {
            Channel<String>(1).apply {
                trySend(text)
                close()
            }
        }
    }

    private suspend fun score(answer: String): LilTiiEvalResult {
        engineReplies(answer)
        return evaluator.evaluateWithLlama("test prompt", timeoutMs = 2_000)
    }

    // ---- Prompt contract ---------------------------------------------------------

    @Test
    fun `prompt carries persona, few-shot and the user turn`() {
        val prompt = LilTiiPromptTemplate.buildPrompt("keno amake chere dile?")

        assertTrue(prompt.contains("LilTii"))
        assertTrue(prompt.contains("User: keno amake chere dile?"))
        assertTrue("prompt must end with the assistant cue", prompt.trimEnd().endsWith("Bot:"))
        listOf("[sleepy]", "[confused]", "[surprised]", "[happy]", "[angry]", "[sad]", "[thinking]")
            .forEach { assertTrue("missing $it in system instructions", prompt.contains(it)) }
    }

    @Test
    fun `cute style advertises the app mood set and a parseable example`() {
        val style = LilTiiPromptTemplate.CUTE_STYLE
        listOf("[happy]", "[sleepy]", "[surprised]", "[sad]", "[angry]", "[thinking]")
            .forEach { assertTrue("missing $it in CUTE_STYLE", style.contains(it)) }
        assertTrue("style should ask for emoji", style.contains("emoji"))

        val example = style.lineSequence().firstOrNull { it.trimStart().startsWith("Example:") }
        assertNotNull("CUTE_STYLE must ship a worked example", example)
        val exampleAnswer = example!!.substringAfter("Example:").trim()
        assertEquals("happy", EmotionAssets.tagOf(exampleAnswer))
    }

    // ---- Scorer contract ---------------------------------------------------------

    @Test
    fun `well formed answer scores full marks`() = runBlocking {
        val result = score("[happy] *smiles brightly* আমি ভালো আছি! tumi kemon acho? 😊✨")

        assertTrue(result.hasEmotionTag)
        assertEquals("happy", result.emotionTag)
        assertTrue(result.validEmotionTag)
        assertTrue(result.tagAtStart)
        assertTrue(result.hasAction)
        assertTrue(result.hasBangla)
        assertEquals(5, result.score)
        assertTrue(result.passed)
        assertEquals("", result.notes)
    }

    @Test
    fun `answer without an emotion tag is flagged and fails`() = runBlocking {
        val result = score("just a normal sentence with no tag at all")

        assertFalse(result.hasEmotionTag)
        assertEquals(0, result.score)
        assertFalse(result.passed)
        assertTrue(result.notes.contains("missing_emotion_tag"))
        assertTrue(result.notes.contains("tag_not_at_start"))
    }

    @Test
    fun `unknown tag counts as present but invalid`() = runBlocking {
        val result = score("[excited] *waves* ami bhalo achi!")

        assertTrue(result.hasEmotionTag)
        assertFalse(result.validEmotionTag)
        assertTrue(result.notes.contains("invalid_emotion_tag=excited"))
    }

    @Test
    fun `tag after prose is not counted as leading`() = runBlocking {
        val result = score("hmm [happy] *smiles* bhalo")

        assertTrue(result.hasEmotionTag)
        assertFalse(result.tagAtStart)
        assertTrue(result.notes.contains("tag_not_at_start"))
    }

    @Test
    fun `long english ramble loses the bangla and action points`() = runBlocking {
        val result = score("[thinking] well, that is a very interesting question and here is my long English answer about it")

        assertTrue(result.hasEmotionTag)
        assertFalse(result.hasAction)
        assertFalse(result.hasBangla)
        assertTrue(result.notes.contains("missing_action"))
        assertTrue(result.notes.contains("no_bangla"))
        assertTrue(result.score < 5)
    }

    @Test
    fun `expected emotion match and mismatch are both reported`() = runBlocking {
        engineReplies("[happy] *bounces* hehe ami khushi! 😊")
        val report = evaluator.evaluate(
            config = ModelConfig(
                provider = ModelConfig.ModelProvider.LOCAL_LLAMA,
                modelName = LlamaEngine.Config().modelFileName
            ),
            iterations = 20,
            timeoutMs = 2_000
        )

        assertEquals(20, report.results.size)
        assertEquals(mapOf("happy" to 20), report.emotionTagDistribution)
        assertTrue(
            "prompt 'i love you' expects happy",
            report.results[3].notes.contains("expected_emotion_match")
        )
        assertTrue(
            "prompt 'hey what are you doing' expects sleepy",
            report.results[0].notes.contains("expected_emotion_mismatch")
        )
        assertTrue(report.overallPassRate >= 0.8)
    }
}
