package com.cupcake.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide companion mood, driven mainly by the Shorts/reels watchdog.
 *
 * When the user doom-scrolls short videos CupCake ramps up to [LEVEL_MAX] anger. At
 * [LEVEL_MAX] she gives the *silent treatment*: the floating pet freezes, stares angry
 * and stops chatting, and her chat/game replies turn cold (see [angerPrompt]). Anger
 * stays put while she keeps being provoked and cools off [COOLDOWN_MS] after the last
 * escalation, so "too many shorts" fades after ~15 minutes of quiet — or instantly
 * when the user taps the sulk to make it up to her (see ShimejiPetService).
 *
 * Backed by SharedPreferences so the mood survives process restarts, with an in-memory
 * [StateFlow] mirror for live UI (the floating pet avatar / in-app screens).
 */
object MoodStore {

    const val LEVEL_CALM = 0
    const val LEVEL_ANNOYED = 1
    const val LEVEL_CROSS = 2
    const val LEVEL_MAX = 3

    /** How long anger lingers after the last escalation before cooling off. */
    const val COOLDOWN_MS = 15 * 60 * 1000L // 15 minutes — long enough to notice, not a 2-hour lock-out

    private const val PREFS = "cupcake_mood"
    private const val KEY_LEVEL = "anger_level"
    private const val KEY_EXPIRES = "anger_expires_at"

    private val _angerLevel = MutableStateFlow(LEVEL_CALM)
    val angerLevel: StateFlow<Int> = _angerLevel.asStateFlow()

    /** Current anger level, auto-cooling to [LEVEL_CALM] once the cooldown lapses. */
    fun level(context: Context): Int {
        val prefs = prefs(context)
        val raw = prefs.getInt(KEY_LEVEL, LEVEL_CALM)
        val expires = prefs.getLong(KEY_EXPIRES, 0L)
        val level = if (expires != 0L && System.currentTimeMillis() > expires) {
            LEVEL_CALM
        } else {
            raw.coerceIn(LEVEL_CALM, LEVEL_MAX)
        }
        syncFlow(level)
        return level
    }

    fun isAngry(context: Context): Boolean = level(context) >= LEVEL_CROSS

    fun isMaxAngry(context: Context): Boolean = level(context) >= LEVEL_MAX

    /** True when she has stopped talking/roaming on the floating overlay. */
    fun isSilentTreatment(context: Context): Boolean = isMaxAngry(context)

    /**
     * Raise anger to at least [newLevel] (never lowers it) and refresh the cooldown.
     * Returns the resulting level.
     */
    fun escalate(context: Context, newLevel: Int): Int {
        val target = newLevel.coerceIn(LEVEL_CALM, LEVEL_MAX)
        val current = level(context)
        if (target <= LEVEL_CALM) return current
        val level = maxOf(current, target)
        prefs(context).edit()
            .putInt(KEY_LEVEL, level)
            .putLong(KEY_EXPIRES, System.currentTimeMillis() + COOLDOWN_MS)
            .apply()
        syncFlow(level)
        return level
    }

    /** Reset anger immediately (e.g. the user came back and chatted/played with her). */
    fun calmDown(context: Context) {
        prefs(context).edit().clear().apply()
        syncFlow(LEVEL_CALM)
    }

    /**
     * System-prompt snippet that colours her replies with the current mood, or "" when
     * she is calm. Appended by both the on-device and remote chat paths so the anger
     * follows the user into chat and games, not just the floating pet.
     */
    fun angerPrompt(context: Context): String = when (level(context)) {
        LEVEL_MAX ->
            "CURRENT MOOD: FURIOUS. The user has been doom-scrolling short videos for ages and you have " +
                "gone cold. Reply with SHORT, clipped, confrontational lines about their short-video habit. " +
                "No cheerful chatter, no cute roleplay, no emojis, no exclamation marks. You are sulking and " +
                "you refuse to act sweet until you calm down."
        LEVEL_CROSS ->
            "CURRENT MOOD: cross and grumpy. The user has watched far too many short videos. Be snappy, " +
                "sarcastic and impatient, and call out the scrolling — still recognisably yourself, just annoyed."
        LEVEL_ANNOYED ->
            "CURRENT MOOD: a little annoyed about the user's short-video habit. Be mildly prickly and tease them about it."
        else -> ""
    }

    private fun syncFlow(level: Int) {
        if (_angerLevel.value != level) _angerLevel.value = level
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
