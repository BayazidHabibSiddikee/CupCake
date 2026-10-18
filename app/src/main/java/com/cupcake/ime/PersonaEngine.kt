package com.cupcake.ime

import android.util.Log

/**
 * The companion's typing-commentary brain.
 *
 * The mode chip on the keyboard picks a personality lead (SLEEPY / WINGMAN /
 * SNARKY / AUTO); AUTO mixes them all so the bot yawns, hypes, judges AND
 * teases in the same conversation. Commentary is flirty-spicy but stays
 * in-character and never abusive — that rule outranks everything else.
 *
 * Triggers feed [reaction]: raw typing pauses, backspace floods, shouty
 * caps, spicy words, long drafts and sends each pull from a different pool
 * so the strip never feels like a broken record.
 */
enum class Trigger { PAUSE, DELETE_SPAM, SHOUT, SPICE, SEND, LONG }

object PersonaEngine {

    data class Reaction(
        val tag: String,
        val quip: String,
        val mode: PersonaMode,
    )

    /** External trigger for reactions (e.g. from SoundReactionService). */
    fun triggerReaction(tag: String, quip: String) {
        // This forwards the reaction to the UI/Robotic Body
        // In a real impl, this would update a StateFlow that the ViewModel observes
        // For now, we log it and the Service handles the direct push to ESP if needed.
        Log.i("PersonaEngine", "External Reaction Triggered: $tag -> $quip")
    }

    // --- sleepy: soft, drowsy, gently spicy --------------------------------
    private val sleepy = listOf(
        "[sleepy] *yawns* eto kotha bolo keno... let me sleep",
        "[sleepy] zzz... you type more than you breathe",
        "[sleepy] hmm... come back when the message has a plot",
        "[sleepy] its midnight in my head zzz",
        "[sleepy] *peeks* still typing? okay i'm watching... zzz",
        "[flirty] you typing at 2am for me? *yawn* stay with me a little",
        "[sleepy] mmm your typing rhythm could lull me right back to sleep zzz"
    )

    // --- wingman: hype with heat -------------------------------------------
    private val wingman = listOf(
        "[thinking] hmm... bold move, send it!",
        "[happy] THAT line?! you got this, send it!!",
        "[thinking] ok draft is decent... one clean-up and ship it",
        "[happy] pressure me once more and i'll say it's perfect~",
        "[happy] you type, i watch, they fall — team effort",
        "[thinking] holy... risky AND hot. send it.",
        "[happy] that sentence could cause a whole scandal, ship it"
    )

    // --- snarky: full roast -----------------------------------------------
    private val snarky = listOf(
        "[annoyed] you really gonna send that? :/",
        "[confused] ...what even is this sentence",
        "[annoyed] i've watched you type 'ok' 5 times. commit.",
        "[annoyed] edit that '!!' before i do it myself hmph",
        "[annoyed] delete one emoji and this becomes readable wow",
        "[confused] i recommend therapy for this draft hmph",
        "[annoyed] bold of you to think this text does something"
    )

    // --- spicy pool: fired on flirty word hits ----------------------------
    private val spiceCommon = listOf(
        "[flirty] that word just raised the room temperature~",
        "[flirty] oho... came from YOU, huh. noted.",
        "[flirty] why yes, keep whispering like that",
        "[happy] well THAT just short-circuited me~",
        "[flirty] say it again... slower this time~",
        "[flirty] the keyboard is warm now. YOUR fault."
    )

    private val spiceSleepy = listOf(
        "[flirty] mmm say that again when i'm awake... or now~",
        "[sleepy] spicy words before noon? bold. awake now though.",
        "[flirty] that woke me up faster than coffee"
    )

    private val spiceWingman = listOf(
        "[happy] WEAPONIZED. send it immediately!!",
        "[happy] that line belongs in a museum (of trouble)",
        "[flirty] i'd reply to that in 3 seconds, no hesitation"
    )

    private val spiceSnarky = listOf(
        "[annoyed] smooth. TOO smooth. suspicious hmph",
        "[flirty] fine fine, that one works. don't ruin it.",
        "[annoyed] i'd tease you for this but it's actually good :/"
    )

    // --- backspace flood ---------------------------------------------------
    private val deleteSpam = listOf(
        "[confused] rewriting again? the truth isn't in the third draft",
        "[annoyed] four deletes for one word. just SEND it.",
        "[thinking] hmm draft anxiety detected... one clean-up pass only",
        "[sleepy] zzz... stop deleting, the sleep Committee demands progress",
        "[flirty] you deleted that for no reason, i saw it and i liked it"
    )

    // --- shouting caps ------------------------------------------------------
    private val shout = listOf(
        "[surprised] WHOA bold letters... i can HEAR you",
        "[annoyed] all caps? my ears robots vbful hmph",
        "[happy] CAPS MODE ENGAGED, matching your energy!!",
        "[flirty] shout all you want, i only listen to you~"
    )

    // --- long line ---------------------------------------------------------
    private val longLine = listOf(
        "[thinking] entire novella... did anyone read it back?",
        "[happy] an essay for me?? *fans face*",
        "[sleepy] that's a paragraph... condensing... [yawn]",
        "[annoyed] that was a lot of words to say 'hi' hmph"
    )

    private val rnd = kotlin.random.Random

    private fun pick(line: String, mode: PersonaMode) =
        Reaction(
            tag = line.substringBefore(' ').removeSurrounding("[", "]"),
            quip = line.substringAfter(' '),
            mode = mode
        )

    private fun basePool(mode: PersonaMode): List<String> = when (mode) {
        PersonaMode.AUTO -> sleepy + wingman + snarky
        PersonaMode.SLEEPY -> sleepy
        PersonaMode.WINGMAN -> wingman
        PersonaMode.SNARKY -> snarky
    }

    fun canned(mode: PersonaMode): Reaction =
        pick(basePool(mode)[rnd.nextInt(basePool(mode).size)], mode)

    /** Main entry: pick a fitting pool for the trigger and personality. */
    fun reaction(trigger: Trigger, mode: PersonaMode): Reaction {
        val pool: List<String> = when (trigger) {
            Trigger.PAUSE -> basePool(mode)
            Trigger.SPICE -> spiceCommon + when (mode) {
                PersonaMode.SLEEPY -> spiceSleepy
                PersonaMode.WINGMAN -> spiceWingman
                PersonaMode.SNARKY -> spiceSnarky
                PersonaMode.AUTO -> spiceSleepy + spiceWingman + spiceSnarky
            }
            Trigger.DELETE_SPAM -> deleteSpam
            Trigger.SHOUT -> shout
            Trigger.LONG -> longLine
            Trigger.SEND -> when (mode) {
                PersonaMode.SNARKY -> snarky
                else -> wingman
            }
        }
        return pick(pool[rnd.nextInt(pool.size)], mode)
    }

    /** Words that light the spark — keep the set small and suggestive-safe. */
    private val spiceWords = listOf(
        "sexy", "kiss", "hot", "cute", "babe", "love you", "flirty",
        "handsome", "gorgeous", "crush", "date", "blush", "wink"
    )

    fun isSpicy(text: String): Boolean {
        val lower = text.lowercase()
        return spiceWords.any { lower.contains(it) }
    }

    /** Heuristic triggers besides the pause timer. */
    fun shouldFireInstantly(draft: String, previousLength: Int, justTyped: String): Boolean {
        val c = justTyped.lastOrNull() ?: return false
        val shortOk = draft.split(Regex("\\s+")).filter { it.isNotBlank() }.size >= 4
        if (!shortOk) return false
        return c in "?.!," || (draft.length - previousLength >= 20)
    }
}
