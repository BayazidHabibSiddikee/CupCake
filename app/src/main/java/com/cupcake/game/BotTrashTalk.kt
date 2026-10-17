package com.cupcake.game

/**
 * CupCake's in-game trash talk, in Banglish.
 *
 * Deliberately deterministic (not model-generated): the whole point is that a small
 * local model cannot be trusted to reliably produce the "she gets angrier every time
 * you win" arc, and these lines are also spoken by TTS, so they must be clean and
 * short. Escalation is driven by the current human win streak.
 *
 * Every line must stay playful — no insults, threats or profanity — because it also
 * passes through [ToneGuard] on the way to the bubble, the voice and the ESP32.
 */
object BotTrashTalk {

    /** tier 1 (streak 1) .. 5 (streak >= 5). */
    fun angryLine(winStreak: Int): String = when (tier(winStreak)) {
        1 -> listOf(
            "hehe, lucky shot chilo~ ebar hobe na 😤",
            "oho! tumi jitso naki? ami ekhono warm up korchi 😏"
        ).random()

        2 -> listOf(
            "hehhh wait... ebar ami difficulty baray dicchi! 😤",
            "tomake diye hbe na eibar, dekho! 💢",
            "areee chill! ebar ami serious hocchi, ready thako 😠"
        ).random()

        3 -> listOf(
            "bas bas! ebar ami hard mode on korchi, bujhle? 😡",
            "ei bar ami haarbo na... ami ebar sob kichu use korbo 💢🔥",
            "huh! tumi jitso mane ami kii koshto pacchi bolo? ebar dekho 😠"
        ).random()

        4 -> listOf(
            "na na na!! ebar ami cheat korbo, dekho ki hoy! 😈🔥",
            "ami ebar full power e khelbo... tomake diye hbe na, kotha dicchi 😡💥",
            "tumi always jitbe naki? mon ta kharap hoye gelo! ebar ami rage mode 😤🔥"
        ).random()

        else -> listOf(
            "huh... tumi always jitbe... amar mon ta ekhon kharap 😭💢",
            "ami ebar sob kichu use korchi... kichu bhalo lagche na re 😤😢",
            "thak! ei bar ami jitbo... noile ami kandi boshe thakbo 😭🔥"
        ).random()
    }

    /** When the bot is winning, it gloats; when it is behind, it sulks. */
    fun sadLine(winStreak: Int): String = when (tier(winStreak)) {
        1 -> "hmph... whatever, ami ekhono strong 😌"
        2 -> "tumi jitso... amar mon ta bhalo lagche na 😔"
        3 -> "aree ami ebar koshto pacchi... please ektu easy thako 🥺"
        else -> "ami pari na... ami ebar sotti kharap feel korchi 😭💔"
    }

    fun welcomeLine(difficultyName: String): String = listOf(
        "hehe, ready to lose? aj ami $difficultyName mode e khelbo~ 😏",
        "asho! aj ami $difficultyName level e, tumi parbe na~ 😼",
        "game shuru! ami ebar $difficultyName, bujhe nao 😤"
    ).random()

    /** Short in-play line (bubble only, not spoken) as the score swings. */
    fun rallyLine(humanScore: Int, botScore: Int, humanScored: Boolean): String = when {
        humanScored && humanScore >= botScore + 2 -> listOf(
            "areeee ebar ami lagche na... focus! 😤",
            "hehhh ektu ektu kore agaccho naki? dekho! 💢"
        ).random()
        !humanScored && botScore >= humanScore + 2 -> listOf(
            "hehe! dekhle? ami boss 😼",
            "too easy~ aj amar din ✨"
        ).random()
        humanScored -> "oho! thik ache... ebar amar serve 😤"
        else -> "hihi, dhoro na~ 😏"
    }

    fun gloatLine(): String = listOf(        "hehe! dekhle? ami boss! 😏",
        "nyahaha, too easy~ ebar amar turn! 😼",
        "yayyy! ami jitlam, tumi next time try koro~ ✨"
    ).random()

    private fun tier(winStreak: Int): Int = winStreak.coerceIn(1, 5)

    /** Human-readable rank shown in the game banner as she gets angrier. */
    fun rankLabel(anger: Double): String = when {
        anger > 0.85 -> "FURIOUS"
        anger > 0.65 -> "RAGING"
        anger > 0.4 -> "ANGRY"
        anger > 0.2 -> "ANNOYED"
        else -> "CALM"
    }
}
