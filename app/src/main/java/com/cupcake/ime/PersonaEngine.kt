package com.cupcake.ime

/**
 * The companion's typing-commentary brain.
 *
 * Three personalities are COMBINED: the mode chip on the keyboard picks which
 * one leads (SLEEPY / WINGMAN / SNARKY / AUTO), and AUTO mixes all three so
 * the bot yawns, hypes and judges in the same conversation.
 *
 * Fast path = canned quips (instant, zero CPU). When the on-device liltii
 * model is free we upgrade to a live one-liner generation (upgrade path planned
 * in the [tag] + quip style).
 */
object PersonaEngine {

    data class Reaction(
        val tag: String,
        val quip: String,
        val mode: PersonaMode,
    )

    private val sleepy = listOf(
        "[sleepy] *yawns* eto kotha bolo keno... let me sleep",
        "[sleepy] zzz... you type more than you breathe",
        "[sleepy] hmm... come back when the message has a plot",
        "[sleepy] its midnight in my head zzz"
    )

    private val wingman = listOf(
        "[thinking] hmm... bold move, send it!",
        "[happy] THAT line?! you got this, send it!!",
        "[thinking] ok draft is decent... one clean-up and ship it",
        "[happy] pressure me once more and i'll say it's perfect~"
    )

    private val snarky = listOf(
        "[annoyed] you really gonna send that? :/",
        "[confused] ...what even is this sentence",
        "[annoyed] i've watched you type 'ok' 5 times. commit.",
        "[annoyed] edit that '!!' before i do it myself hmph"
    )

    private val rnd = kotlin.random.Random

    fun canned(mode: PersonaMode): Reaction {
        val pool = when (mode) {
            PersonaMode.AUTO -> sleepy + wingman + snarky
            PersonaMode.SLEEPY -> sleepy
            PersonaMode.WINGMAN -> wingman
            PersonaMode.SNARKY -> snarky
        }
        val line = pool[rnd.nextInt(pool.size)]
        return Reaction(
            tag = line.substringBefore(' ').removeSurrounding("[", "]"),
            quip = line.substringAfter(' '),
            mode = mode
        )
    }

    /** Heuristic triggers besides the pause timer. */
    fun shouldFireInstantly(draft: String, previousLength: Int, justTyped: String): Boolean {
        val c = justTyped.lastOrNull() ?: return false
        val shortOk = draft.split(Regex("\\s+")).filter { it.isNotBlank() }.size >= 4
        if (!shortOk) return false
        return c in "?.!," || (draft.length - previousLength >= 20)
    }
}
