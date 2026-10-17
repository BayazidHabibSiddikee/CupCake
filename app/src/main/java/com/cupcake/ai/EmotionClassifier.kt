package com.cupcake.ai

/**
 * Lightweight, zero-latency mobile emotion classifier.
 * Detects 6 core emotions (Joy, Surprise, Affection/Love, Thinking, Sadness, Anger)
 * based on contextual lexical and prompt analysis.
 */
object EmotionClassifier {

    enum class Emotion {
        JOY,
        SURPRISE,
        AFFECTION,
        JEALOUSY,
        THINKING,
        SADNESS,
        ANGER,
        NEUTRAL
    }

    private val joyKeywords = setOf(
        "haha", "lol", "lmao", "rofl", "hehe", "funny", "hilarious",
        "yay", "happy", "great", "awesome", "cool", "wonderful", "win", "won", "good",
        "smile", "smiles", "smiling", "giggle", "giggles", "giggling", "laugh", "laughs", "laughing",
        "grin", "grins", "glad", "delighted", "cheer", "cheers", "excited", "fun"
    )

    private val affectionKeywords = setOf(
        "love", "kyaa", "cute", "sweet", "darling", "miss", "hug", "kiss", "care", "heart", "❤", "💕",
        "blush", "blushes", "blushing", "flirt", "flirting", "wink", "winks", "cuddle", "cuddles",
        "sweetheart", "honey", "babe", "adore", "dearest", "cutie", "handsome", "fond", "hold", "holding"
    )

    private val jealousyKeywords = setOf(
        "jealous", "pout", "pouts", "pouting", "hmph", "baka", "rival", "envy",
        "cheater", "cheating", "cheat", "ignoring", "unfaithful"
    )

    private val surpriseKeywords = setOf(
        "wow", "whoa", "really", "omg", "no way", "shock", "impossible",
        "gasp", "gasps", "astonished", "unbelievable", "woah"
    )

    private val sadnessKeywords = setOf(
        "sad", "sorry", "cry", "crying", "lost", "hurt", "tear", "tears", "unfortunate",
        "broken", "bad", "pain", "sigh", "sighs", "lonely", "depressed", "heartbroken", "sniff"
    )

    private val angerKeywords = setOf(
        "angry", "hate", "mad", "annoying", "annoyed", "stop", "furious", "rage",
        "glare", "glares", "scowl", "scowls", "stomp", "stomps", "shut"
    )

    private val thinkingKeywords = setOf(
        "hmm", "let me think", "perhaps", "maybe", "calculating", "processing", "analyzing", "ponder",
        "wonder", "wondering", "curious"
    )

    /**
     * Classifies a message into core emotions including jealousy, affection, and teasing.
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

        // Action tags in asterisks (e.g. *blushes and looks away*, *pouts*, *giggles*)
        val actionMatches = Regex("\\*([^*]+)\\*").findAll(lower)
        for (match in actionMatches) {
            val act = match.groupValues[1]
            if (act.contains("blush") || act.contains("wink") || act.contains("hug") || act.contains("kiss") || act.contains("cuddle") || act.contains("shy")) {
                affectionScore += 4
            }
            if (act.contains("pout") || act.contains("crosses arms") || act.contains("hmph") || act.contains("looks away")) {
                jealousyScore += 4
            }
            if (act.contains("giggle") || act.contains("smile") || act.contains("laugh") || act.contains("grin")) {
                joyScore += 4
            }
            if (act.contains("gasp") || act.contains("eyes widen") || act.contains("surprised")) {
                surpriseScore += 4
            }
            if (act.contains("sigh") || act.contains("cry") || act.contains("tear") || act.contains("sniff")) {
                sadnessScore += 4
            }
            if (act.contains("glare") || act.contains("stomp") || act.contains("angry")) {
                angerScore += 4
            }
        }

        // Multi-word phrase triggers
        if (lower.contains("other girl") || lower.contains("another girl") || 
            lower.contains("who is she") || lower.contains("who's she") || 
            lower.contains("ignoring me") || lower.contains("another ai") || 
            lower.contains("talking to someone else") || lower.contains("you like her")) {
            jealousyScore += 5
        }
        if (lower.contains("hmph") || lower.contains("pouting") || lower.contains("baka!")) {
            jealousyScore += 3
        }
        if (lower.contains("kyaa") || lower.contains("love you") || lower.contains("i love") || lower.contains("my marin") || lower.contains("so cute")) {
            affectionScore += 3
        }
        if (lower.contains("no way") || lower.contains("what?!") || lower.contains("really?!")) {
            surpriseScore += 3
        }

        val max = maxOf(joyScore, affectionScore, jealousyScore, surpriseScore, sadnessScore, angerScore, thinkingScore)
        if (max == 0) return Emotion.NEUTRAL

        return when {
            max == jealousyScore -> Emotion.JEALOUSY
            max == affectionScore -> Emotion.AFFECTION
            max == joyScore -> Emotion.JOY
            max == surpriseScore -> Emotion.SURPRISE
            max == angerScore -> Emotion.ANGER
            max == sadnessScore -> Emotion.SADNESS
            max == thinkingScore -> Emotion.THINKING
            else -> Emotion.NEUTRAL
        }
    }
}
