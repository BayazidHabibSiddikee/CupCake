package com.cupcake.ai

/**
 * Small helpers for expanding the well-known roleplay template macros used across
 * character prompts and greetings.
 */
object PromptTemplates {

    /** Replaces {{user}}/{{char}}/<USER>/<CHAR>/<BOT> with the live names. */
    fun replaceTemplateMacros(text: String, charName: String, userName: String = "User"): String {
        return text
            .replace("{{user}}", userName, ignoreCase = true)
            .replace("{{char}}", charName, ignoreCase = true)
            .replace("<USER>", userName, ignoreCase = true)
            .replace("<CHAR>", charName, ignoreCase = true)
            .replace("<BOT>", charName, ignoreCase = true)
    }
}
