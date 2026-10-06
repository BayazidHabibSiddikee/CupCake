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
        "love" to "bhalobasha"
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
        "focus" to "monojog dao"
    )

    fun translate(text: String, personality: String): String {
        var translated = text
        val dict = when {
            personality.contains("sarcastic") || personality.contains("rage") -> sarcasticDict
            personality.contains("teacher") || personality.contains("tutor") -> teacherDict
            else -> emptyMap()
        }
        
        if (dict.isEmpty()) return text
        
        // Simple word replacement (case insensitive)
        for ((eng, bangla) in dict) {
            val regex = Regex("(?i)\\b$eng\\b")
            translated = translated.replace(regex, bangla)
        }
        return translated
    }
}
