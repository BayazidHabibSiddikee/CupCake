package com.cupcake.ai

/**
 * Lightweight, zero-latency mobile emotion classifier.
 * Detects 6 core emotions (Joy, Surprise, Affection/Love, Thinking, Sadness, Anger)
 * based on contextual lexical and prompt analysis.
 */
object EmotionClassifier {

    enum class Emotion(val vrmExpression: String) {
        JOY("happy"),
        SURPRISE("surprised"),
        AFFECTION("happy"),
        THINKING("relaxed"),
        SADNESS("sad"),
        ANGER("angry"),
        NEUTRAL("neutral")
    }

    private val joyKeywords = setOf(
        "haha", "lol", "lmao", "rofl", "hehe", "funny", "hilarious",
        "yay", "happy", "great", "awesome", "cool", "wonderful", "win", "won", "good"
    )

    private val affectionKeywords = setOf(
        "love", "kyaa", "cute", "sweet", "darling", "miss", "hug", "kiss", "care", "heart", "❤", "💕"
    )

    private val surpriseKeywords = setOf(
        "wow", "whoa", "really", "omg", "no way", "what", "unbelievable", "shock", "impossible"
    )

    private val sadnessKeywords = setOf(
        "sad", "sorry", "cry", "crying", "lost", "hurt", "tear", "unfortunate", "broken", "bad", "pain"
    )

    private val angerKeywords = setOf(
        "angry", "hate", "mad", "annoying", "annoyed", "stop", "furious", "cheat", "rage", "shut"
    )

    private val thinkingKeywords = setOf(
        "hmm", "let me think", "perhaps", "maybe", "calculating", "processing", "analyzing", "ponder"
    )

    /**
     * Classifies a message into one of 6 core emotions.
     */
    fun detectEmotion(text: String): Emotion {
        val lower = text.lowercase()
        val tokens = lower.split(Regex("\\W+")).filter { it.isNotBlank() }

        var joyScore = 0
        var affectionScore = 0
        var surpriseScore = 0
        var sadnessScore = 0
        var angerScore = 0
        var thinkingScore = 0

        for (token in tokens) {
            if (joyKeywords.contains(token)) joyScore++
            if (affectionKeywords.contains(token)) affectionScore++
            if (surpriseKeywords.contains(token)) surpriseScore++
            if (sadnessKeywords.contains(token)) sadnessScore++
            if (angerKeywords.contains(token)) angerScore++
            if (thinkingKeywords.contains(token)) thinkingScore++
        }

        if (lower.contains("kyaa") || lower.contains("love you")) affectionScore += 2
        if (lower.contains("no way") || lower.contains("what?!")) surpriseScore += 2

        val max = maxOf(joyScore, affectionScore, surpriseScore, sadnessScore, angerScore, thinkingScore)
        if (max == 0) return Emotion.NEUTRAL

        return when (max) {
            affectionScore -> Emotion.AFFECTION
            joyScore -> Emotion.JOY
            surpriseScore -> Emotion.SURPRISE
            angerScore -> Emotion.ANGER
            sadnessScore -> Emotion.SADNESS
            thinkingScore -> Emotion.THINKING
            else -> Emotion.NEUTRAL
        }
    }
}
