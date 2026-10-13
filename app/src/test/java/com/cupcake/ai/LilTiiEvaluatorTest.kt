package com.cupcake.ai

import android.content.Context
import com.cupcake.data.model.ModelConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.channels.Channel
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
    fun `20 iterations use local llama backend path when provider is local`() = runBlocking {
        val config = ModelConfig(
            provider = ModelConfig.ModelProvider.LOCAL_LLAMA,
            modelName = LlamaEngine.Config().modelFileName
        )

        every {
            llamaEngine.generateStream(any())
        } answers {
            val channel = Channel<String>(1)
            channel.trySend("[sleepy] *yawns* ami onek klanto... ")
            channel.close()
            channel
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

        report.results.forEach { result ->
            println("Iteration ${result.iteration}: tag=${result.emotionTag}, action=${result.action}, bangla=${result.hasBangla}, score=${result.score}")
        }
    }

    @Test
    fun `evaluator reports per-iteration breakdown for 20 local runs`() = runBlocking {
        val config = ModelConfig(
            provider = ModelConfig.ModelProvider.LOCAL_LLAMA,
            modelName = LlamaEngine.Config().modelFileName
        )

        every {
            llamaEngine.generateStream(any())
        } answers {
            val channel = Channel<String>(1)
            channel.trySend("[happy] *smiles* bhalo ache... ")
            channel.close()
            channel
        }

        val report = evaluator.evaluate(config, iterations = 20, timeoutMs = 5_000)

        assert(report.results.all { it.hasEmotionTag })
        assert(report.results.all { it.hasAction })
        assert(report.results.all { it.tagAtStart })
        assert(report.emotionTagDistribution["happy"] == 20)
    }
}
