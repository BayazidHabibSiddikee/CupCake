package com.cupcake.ai

data class LilTiiEvalResult(
    val iteration: Int,
    val prompt: String,
    val rawOutput: String,
    val hasEmotionTag: Boolean,
    val emotionTag: String?,
    val validEmotionTag: Boolean,
    val hasAction: Boolean,
    val action: String?,
    val hasBangla: Boolean,
    val tagAtStart: Boolean,
    val score: Int,
    val notes: String = ""
) {
    val passed: Boolean get() = score >= 3
}

data class LilTiiEvalReport(
    val iterations: Int,
    val results: List<LilTiiEvalResult>,
    val passCount: Int,
    val failCount: Int,
    val emotionTagDistribution: Map<String, Int>,
    val avgScore: Double,
    val overallPassRate: Double
) {
    val passed: Boolean get() = overallPassRate >= 0.8
}
