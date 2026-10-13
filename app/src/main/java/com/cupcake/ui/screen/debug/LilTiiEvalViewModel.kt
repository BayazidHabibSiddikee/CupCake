package com.cupcake.ui.screen.debug

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cupcake.ai.LilTiiEvalReport
import com.cupcake.ai.LilTiiEvalResult
import com.cupcake.ai.LilTiiEvaluator
import com.cupcake.ai.LlamaEngine
import com.cupcake.ai.OllamaEngine
import com.cupcake.data.model.ModelConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

enum class EvalStatus { IDLE, RUNNING, SUCCESS, ERROR }

data class EvalUiState(
    val status: EvalStatus = EvalStatus.IDLE,
    val currentIteration: Int = 0,
    val totalIterations: Int = 20,
    val report: LilTiiEvalReport? = null,
    val error: String? = null
)

@HiltViewModel
class LilTiiEvalViewModel @Inject constructor(
    private val llamaEngine: LlamaEngine,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EvalUiState())
    val uiState: StateFlow<EvalUiState> = _uiState

    fun runEval() {
        val current = _uiState.value
        if (current.status == EvalStatus.RUNNING) return

        _uiState.value = current.copy(
            status = EvalStatus.RUNNING,
            currentIteration = 0,
            report = null,
            error = null
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                LlamaEngine.initialize(appContext)

                if (!llamaEngine.isReady()) {
                    Log.i("LilTiiEvalViewModel", "Loading model...")
                    val loadResult = llamaEngine.loadModel(appContext)
                    if (loadResult.isFailure) {
                        val err = loadResult.exceptionOrNull()?.message ?: "Unknown load error"
                        _uiState.value = _uiState.value.copy(
                            status = EvalStatus.ERROR,
                            error = "Model load failed: $err"
                        )
                        return@launch
                    }
                }

                Log.i("LilTiiEvalViewModel", "Model ready, starting 20-iteration eval")

                val evaluator = LilTiiEvaluator(
                    llamaEngine = llamaEngine,
                    ollamaEngine = OllamaEngine.getInstance(),
                    appContext = appContext
                )

                val config = ModelConfig(
                    provider = ModelConfig.ModelProvider.LOCAL_LLAMA,
                    modelName = LlamaEngine.Config().modelFileName
                )

                val results = mutableListOf<LilTiiEvalResult>()
                for (i in 0 until 20) {
                    val templates = listOf(
                        "hey what are you doing?" to "sleepy",
                        "what is the capital of bangladesh?" to "confused",
                        "wake up!" to "surprised",
                        "i love you" to "happy",
                        "you're so annoying" to "angry",
                        "i'm sad today" to "sad",
                        "what do you think about this?" to "thinking",
                        "haha that's funny?" to "happy",
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
                    val (message, _) = templates[i % templates.size]
                    val prompt = com.cupcake.ai.LilTiiPromptTemplate.buildPrompt(message)

                    val result = try {
                        withTimeout(60_000) {
                            val channel = llamaEngine.generateStream(prompt)
                            val sb = StringBuilder()
                            for (chunk in channel) {
                                sb.append(chunk)
                            }
                            val raw = sb.toString()
                            val trimmed = raw.trim()
                            val emotionTagRegex = Regex("""^\[(sleepy|confused|surprised|happy|angry|sad|thinking|lovely)\]\s*(.*)""", RegexOption.IGNORE_CASE)
                            val actionRegex = Regex("""^\*(.+?)\*\s*""")
                            val tagMatch = emotionTagRegex.find(trimmed)
                            val emotionTag = tagMatch?.groupValues?.getOrNull(1)?.lowercase()
                            val validEmotionTag = emotionTag in setOf("sleepy", "confused", "surprised", "happy", "angry", "sad", "thinking", "lovely")
                            val hasBangla = trimmed.any { it in 'ঀ'..'৿' }
                            var score = 0
                            val notes = mutableListOf<String>()
                            if (tagMatch != null) score++ else notes.add("missing_emotion_tag")
                            if (validEmotionTag) score++ else notes.add("invalid_emotion_tag=$emotionTag")
                            if (trimmed.startsWith("[") && tagMatch != null) score++ else notes.add("tag_not_at_start")
                            val afterTag = tagMatch?.groupValues?.getOrNull(2) ?: trimmed
                            if (actionRegex.find(afterTag) != null) score++ else notes.add("missing_action")
                            if (hasBangla) score++ else notes.add("no_bangla")

                            LilTiiEvalResult(
                                iteration = i + 1,
                                prompt = prompt,
                                rawOutput = raw,
                                hasEmotionTag = tagMatch != null,
                                emotionTag = emotionTag,
                                validEmotionTag = validEmotionTag,
                                hasAction = actionRegex.find(afterTag) != null,
                                action = actionRegex.find(afterTag)?.groupValues?.getOrNull(1),
                                hasBangla = hasBangla,
                                tagAtStart = trimmed.startsWith("[") && tagMatch != null,
                                score = score,
                                notes = notes.joinToString("; ")
                            )
                        }
                    } catch (e: Exception) {
                        LilTiiEvalResult(
                            iteration = i + 1,
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

                    results.add(result)
                    _uiState.value = _uiState.value.copy(currentIteration = i + 1)
                }

                val passCount = results.count { it.passed }
                val failCount = results.size - passCount
                val emotionTagDistribution = results.mapNotNull { it.emotionTag }.groupingBy { it }.eachCount()
                val avgScore = results.map { it.score }.average()
                val overallPassRate = passCount.toDouble() / results.size

                val report = LilTiiEvalReport(
                    iterations = results.size,
                    results = results,
                    passCount = passCount,
                    failCount = failCount,
                    emotionTagDistribution = emotionTagDistribution,
                    avgScore = avgScore,
                    overallPassRate = overallPassRate
                )

                _uiState.value = _uiState.value.copy(
                    status = EvalStatus.SUCCESS,
                    report = report
                )
            } catch (e: Exception) {
                Log.e("LilTiiEvalViewModel", "Eval failed", e)
                _uiState.value = _uiState.value.copy(
                    status = EvalStatus.ERROR,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun reset() {
        _uiState.value = EvalUiState()
    }
}
