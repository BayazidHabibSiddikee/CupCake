package com.cupcake.game

/**
 * Keeps the companion character's in-game lines friendly.
 *
 * The games deliberately make the character competitive, but an LLM can still
 * drift into insults, threats, or profanity — especially at high temperature or
 * when a custom (e.g. "rage") persona is active. Every line the bot says during
 * a game passes through here after Banglish translation and before it is shown
 * in the bubble, spoken via TTS, or persisted to the chat history.
 */
internal object ToneGuard {

    /** Hard cap so a runaway response still fits the bubble / TTS. */
    private const val MAX_CHARS = 220

    /**
     * Whole-word hostile terms. Word boundaries avoid false positives such as
     * "diet" (contains "die"), "study" (contains "stud") or "class" (contains "ass").
     */
    private val HOSTILE = Regex(
        "\\b(" +
            "kill|kys|die|death|murder|stab|shoot|" +
            "idiot|stupid|dumb|moron|imbecile|retard|loser|pathetic|worthless|" +
            "shut\\s*up|stfu|hate\\s*you|i\\s*hate\\s*you|kill\\s*yourself|" +
            "shit|fuck|fucking|bitch|bastard|asshole|damn\\s*you|screw\\s*you|" +
            "ugly|fat|disgusting" +
            ")\\b",
        RegexOption.IGNORE_CASE
    )

    /** Short, in-character lines used when the model produces something hostile. */
    private val PLAYFUL = listOf(
        "Hmph! You're actually kind of good~ rematch? 😤",
        "No way~! I'll get you next round, just watch! ✨",
        "Kyaa, you got me! But I'm still winning overall~ 😏",
        "Hey, no fair! I'm not pouting... okay maybe a little. 💢",
        "Hehe, lucky shot~ my turn to shine now! 🌟"
    )

    fun sanitize(text: String): String {
        var t = text.trim()
        if (t.isEmpty()) return t

        // Collapse the runaway newlines/spacing the model sometimes emits.
        t = t.replace(Regex("\\s+"), " ").trim()

        // If the model crossed into insults or threats, swap for a playful line.
        if (HOSTILE.containsMatchIn(t)) {
            return PLAYFUL.random()
        }

        if (t.length > MAX_CHARS) {
            t = t.take(MAX_CHARS).substringBeforeLast(' ').trimEnd() + "…"
        }
        return t
    }
}
