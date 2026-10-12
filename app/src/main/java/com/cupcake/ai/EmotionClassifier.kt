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
        JEALOUSY("angry"),
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

    private val jealousyKeywords = setOf(
        "jealous", "pout", "hmph", "cheater", "cheating", "cheat", "ignoring", "baka",
        "rival", "envy", "flirt", "flirting", "unfaithful", "other", "another"
    )

    private val surpriseKeywords = setOf(
        "wow", "whoa", "really", "omg", "no way", "what", "unbelievable", "shock", "impossible"
    )

    private val sadnessKeywords = setOf(
        "sad", "sorry", "cry", "crying", "lost", "hurt", "tear", "unfortunate", "broken", "bad", "pain"
    )

    private val angerKeywords = setOf(
        "angry", "hate", "mad", "annoying", "annoyed", "stop", "furious", "rage", "shut"
    )

    private val thinkingKeywords = setOf(
        "hmm", "let me think", "perhaps", "maybe", "calculating", "processing", "analyzing", "ponder"
    )

    /**
     * Classifies a message into core emotions including jealousy and teasing.
     */
    fun detectEmotion(text: String): Emotion {
        val lower = text.lowercase()
        val tokens = lower.split(Regex("\\W+")).filter { it.isNotBlank() }

        var joyScore = 0
        var affectionScore = 0
        var jealousyScore = 0
        var surpriseScore = 0
        var sadnessScore = 0
        var angerScore = 0
        var thinkingScore = 0

        for (token in tokens) {
            if (joyKeywords.contains(token)) joyScore++
            if (affectionKeywords.contains(token)) affectionScore++
            if (jealousyKeywords.contains(token)) jealousyScore++
            if (surpriseKeywords.contains(token)) surpriseScore++
            if (sadnessKeywords.contains(token)) sadnessScore++
            if (angerKeywords.contains(token)) angerScore++
            if (thinkingKeywords.contains(token)) thinkingScore++
        }

        // Multi-word phrase triggers for jealousy and affection
        if (lower.contains("other girl") || lower.contains("another girl") || 
            lower.contains("who is she") || lower.contains("ignoring me") || 
            lower.contains("another ai") || lower.contains("talking to someone else")) {
            jealousyScore += 4
        }
        if (lower.contains("hmph") || lower.contains("pouting") || lower.contains("baka!")) {
            jealousyScore += 2
        }
        if (lower.contains("kyaa") || lower.contains("love you") || lower.contains("my marin")) {
            affectionScore += 2
        }
        if (lower.contains("no way") || lower.contains("what?!")) {
            surpriseScore += 2
        }

        val max = maxOf(joyScore, affectionScore, jealousyScore, surpriseScore, sadnessScore, angerScore, thinkingScore)
        if (max == 0) return Emotion.NEUTRAL

        return when (max) {
            jealousyScore -> Emotion.JEALOUSY
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
