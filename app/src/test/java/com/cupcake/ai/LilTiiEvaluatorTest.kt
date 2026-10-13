package com.cupcake.ai

import android.content.Context
import com.cupcake.data.model.ModelConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * Evaluates LilTii-style few-shot prompting by running 20 iterations
 * against a stubbed backend and scoring emotion-tag compliance.
 */
class LilTiiEvaluatorTest {

    private lateinit var llamaEngine: LlamaEngine
    private lateinit var ollamaEngine: OllamaEngine
    private lateinit var evaluator: LilTiiEvaluator
    private lateinit var appContext: Context

    @Before
    fun setUp() {
        llamaEngine = mockk<LlamaEngine>(relaxed = true)
        ollamaEngine = mockk<OllamaEngine>(relaxed = false)
        appContext = mockk<Context>(relaxed = true)
        evaluator = LilTiiEvaluator(llamaEngine, ollamaEngine, appContext)
    }

    @Test
    fun `20 iterations produce structured outputs with emotion tags`() = runBlocking {
        val config = ModelConfig(
            provider = ModelConfig.ModelProvider.CUSTOM_OLLAMA,
            modelName = "liltii-v0.2-test",
            customEndpoint = "http://localhost:11434/v1"
        )

        val callCountSlot = slot<List<OllamaEngine.OllamaMessage>>()
        coEvery {
            ollamaEngine.generateStream(any(), any(), capture(callCountSlot), any())
        } coAnswers {
            val callback = it.invocation.args[3] as OllamaEngine.GenerateCallback
            val emotions = listOf("[sleepy]", "[confused]", "[surprised]", "[happy]", "[angry]", "[sad]", "[thinking]")
            val actions = listOf("*yawns*", "*tilts head*", "*jumps*", "*smiles*", "*frowns*", "*sighs*", "*ponders*")
            val banglaFragments = listOf(" ami", " keno", " bhalo", " kharap", " klanto", " dhaka")

            val rng = Random(System.currentTimeMillis())
            repeat(3) {
                val emotion = emotions.random(rng)
                val action = actions.random(rng)
                val bangla = banglaFragments.random(rng)
                callback.onToken("$emotion $action$bangla ache... ")
            }
            callback.onComplete()
        }

        val report = evaluator.evaluate(config, iterations = 20, timeoutMs = 5_000)

        assert(report.iterations == 20)
        assert(report.results.size == 20)
        assert(report.passCount >= 16) {
            "Expected >= 16/20 passes at 80%, got ${report.passCount}/20"
        }
        assert(report.overallPassRate >= 0.8) {
            "Expected pass rate >= 80%, got ${report.overallPassRate}"
        }

        val allHaveTags = report.results.all { it.hasEmotionTag }
        assert(allHaveTags) { "Not all iterations emitted an emotion tag" }

        val allHaveActions = report.results.all { it.hasAction }
        assert(allHaveActions) { "Not all iterations included an action" }

        report.results.forEach { result ->
            println("Iteration ${result.iteration}: tag=${result.emotionTag}, action=${result.action}, bangla=${result.hasBangla}, score=${result.score}")
        }
    }

    @Test
    fun `evaluator enforces 80 percent pass threshold`() = runBlocking {
        val config = ModelConfig(
            provider = ModelConfig.ModelProvider.CUSTOM_OLLAMA,
            modelName = "liltii-v0.2-test",
            customEndpoint = "http://localhost:11434/v1"
        )

        var callIndex = 0
        coEvery {
            ollamaEngine.generateStream(any(), any(), any(), any())
        } coAnswers {
            callIndex++
            val callback = it.invocation.args[3] as OllamaEngine.GenerateCallback
            if (callIndex % 5 == 0) {
                callback.onToken("just a normal reply without tags")
            } else {
                callback.onToken("[sleepy] *yawns* ami klanto... ")
            }
            callback.onComplete()
        }

        val report = evaluator.evaluate(config, iterations = 20, timeoutMs = 5_000)
        val expectedFails = callIndex / 5
        val expectedPasses = 20 - expectedFails
        assert(report.passCount == expectedPasses)
        assert(report.failCount == expectedFails)
    }
}
