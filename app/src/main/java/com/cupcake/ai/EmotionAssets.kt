package com.cupcake.ai

/**
 * Single source of truth for the companion's 2D emotion art.
 *
 * The asset stem (e.g. `laugh`) is the shared vocabulary used by:
 *  - the in-app live face + chat bubbles (via [assetForStem]),
 *  - the floating overlay pet,
 *  - the ESP32 board, which can fetch the exact same PNG over HTTP at
 *    [httpPathForStem] and drive its own face from [espFaceForStem].
 *
 * The bracket tags accepted here are a tolerant superset: the canonical set lives in
 * [EmotionTags] and is what the prompt, grammar and scorer enforce, while the extra
 * aliases below keep older/off-script model output mapping to *some* art instead of
 * falling through to neutral.
 */
object EmotionAssets {

    private const val DIR = "marin/emotions"
    const val URL_PREFIX = "file:///android_asset/$DIR"
    const val HTTP_PREFIX = "/assets/$DIR"

    /** Canonical PNG stems present in `assets/marin/emotions/`. */
    val ALL_STEMS: List<String> = listOf(
        "normal", "laugh", "lovely", "flirty", "excited",
        "confident", "thinking", "shock", "sad", "angry", "eating"
    )

    private val emotionToStem = mapOf(
        EmotionClassifier.Emotion.JOY to "laugh",
        EmotionClassifier.Emotion.SURPRISE to "shock",
        EmotionClassifier.Emotion.AFFECTION to "lovely",
        EmotionClassifier.Emotion.JEALOUSY to "flirty",
        EmotionClassifier.Emotion.THINKING to "thinking",
        EmotionClassifier.Emotion.SADNESS to "sad",
        EmotionClassifier.Emotion.ANGER to "angry",
        EmotionClassifier.Emotion.NEUTRAL to "normal"
    )

    /** Bracket tags emitted by the local model -> asset stem. */
    private val tagToStem = mapOf(
        "sleepy" to "normal",
        "confused" to "confident",
        "surprised" to "shock",
        "shock" to "shock",
        "happy" to "laugh",
        "laugh" to "laugh",
        "sad" to "sad",
        "angry" to "angry",
        "thinking" to "thinking",
        "excited" to "excited",
        "confident" to "confident",
        "flirty" to "flirty",
        "lovely" to "lovely",
        "love" to "lovely",
        "hungry" to "eating",
        "eating" to "eating",
        "pout" to "flirty"
    )

    /** Asset stem -> ESP32 face expression (see EspWebSocketServer.FaceExpression). */
    private val stemToEspFace = mapOf(
        "normal" to "neutral",
        "confident" to "neutral",
        "eating" to "neutral",
        "laugh" to "happy",
        "excited" to "excited",
        "lovely" to "heart_eyes",
        "flirty" to "wink",
        "angry" to "angry",
        "sad" to "sad",
        "shock" to "excited",
        "thinking" to "thinking"
    )

    private val tagRegex = Regex("\\[([a-zA-Z]+)\\]")

    /** First bracketed emotion tag in [text], lowercased, if any. */
    fun tagOf(text: String): String? =
        tagRegex.find(text)?.groupValues?.getOrNull(1)?.lowercase()

    fun stemForEmotion(emotion: EmotionClassifier.Emotion): String =
        emotionToStem[emotion] ?: "normal"

    /** Resolves the best face stem for a message: explicit tag wins, else lexical detection. */
    fun stemForText(text: String): String {
        tagToStem[tagOf(text)]?.let { return it }
        return stemForEmotion(EmotionClassifier.detectEmotion(text))
    }

    fun assetForStem(stem: String): String = "$URL_PREFIX/$stem.png"

    fun assetForText(text: String): String = assetForStem(stemForText(text))

    fun assetFor(emotion: EmotionClassifier.Emotion): String = assetForStem(stemForEmotion(emotion))

    fun httpPathForStem(stem: String): String = "$HTTP_PREFIX/$stem.png"

    fun espFaceForStem(stem: String): String = stemToEspFace[stem] ?: "neutral"

    fun espFaceForText(text: String): String = espFaceForStem(stemForText(text))
}
