package com.cupcake.ai

object BanglishTranslator {
    private val sarcasticDict = mapOf(
        "hello" to "ki obostha",
        "hi" to "ki obostha",
        "how are you" to "kemon acho",
        "what" to "ki",
        "why" to "keno",
        "good" to "valoi",
        "bad" to "baje",
        "yes" to "ha",
        "no" to "na",
        "stupid" to "gofur",
        "idiot" to "bolod",
        "crazy" to "pagol",
        "friend" to "dosto",
        "brother" to "bhai",
        "sorry" to "maf koro",
        "thanks" to "dhonnobad",
        "love" to "bhalobasha",
        "cute" to "mishti",
        "angry" to "rag korcha",
        "sad" to "kosto cha",
        "play" to "kheli",
        "game" to "khela",
        "okay" to "okay-ha",
        "wait" to "dhoro",
        "easy" to "sohaj"
    )

    private val teacherDict = mapOf(
        "hello" to "nomoshkar",
        "hi" to "shubho din",
        "student" to "chhatro",
        "study" to "porashona",
        "book" to "boi",
        "good" to "bhalo",
        "bad" to "kharap",
        "yes" to "hã",
        "no" to "na",
        "understand" to "bujhte perecho",
        "focus" to "monojog dao",
        "well done" to "valo korcho",
        "wrong" to "bhul",
        "correct" to "thik"
    )

    private val sarcasticRegexes = sarcasticDict.map { Regex("(?i)\\b${it.key}\\b") to it.value }
    private val teacherRegexes = teacherDict.map { Regex("(?i)\\b${it.key}\\b") to it.value }

    fun translate(text: String, personality: String): String {
        var translated = text
        // The gate was case-sensitive: personality strings like "Sarcastic &
        // Playful" never matched "sarcastic" and the dictionary silently no-oped.
        val personalityLower = personality.lowercase()
        val dictRegexes = when {
            personalityLower.contains("sarcastic") || personalityLower.contains("rage") -> sarcasticRegexes
            personalityLower.contains("teacher") || personalityLower.contains("tutor") -> teacherRegexes
            else -> emptyList()
        }
        
        if (dictRegexes.isEmpty()) return text
        
        for ((regex, bangla) in dictRegexes) {
            translated = translated.replace(regex, bangla)
        }
        return translated
    }
}
