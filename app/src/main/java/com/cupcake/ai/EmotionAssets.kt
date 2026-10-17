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

    /** Canonical PNG stems present in `assets/marin/emotions/` (from the sprite sheet). */
    val ALL_STEMS: List<String> = listOf(
        "happy", "sleepy", "surprised", "sad", "angry", "thinking", "confused", "annoyed"
    )

    private val emotionToStem = mapOf(
        EmotionClassifier.Emotion.JOY to "happy",
        EmotionClassifier.Emotion.SURPRISE to "surprised",
        EmotionClassifier.Emotion.AFFECTION to "happy",
        EmotionClassifier.Emotion.JEALOUSY to "annoyed",
        EmotionClassifier.Emotion.THINKING to "thinking",
        EmotionClassifier.Emotion.SADNESS to "sad",
        EmotionClassifier.Emotion.ANGER to "angry",
        EmotionClassifier.Emotion.NEUTRAL to "happy"
    )

    /** Bracket tags emitted by the local model -> asset stem. */
    private val tagToStem = mapOf(
        "sleepy" to "sleepy",
        "confused" to "confused",
        "surprised" to "surprised",
        "shock" to "surprised",
        "happy" to "happy",
        "laugh" to "happy",
        "sad" to "sad",
        "angry" to "angry",
        "annoyed" to "annoyed",
        "thinking" to "thinking",
        "excited" to "surprised",
        "confident" to "annoyed",
        "flirty" to "annoyed",
        "lovely" to "happy",
        "love" to "happy",
        "hungry" to "happy",
        "eating" to "happy",
        "pout" to "annoyed",
        "normal" to "happy"
    )

    /** Asset stem -> ESP32 face expression (see EspWebSocketServer.FaceExpression). */
    private val stemToEspFace = mapOf(
        "happy" to "happy",
        "sleepy" to "sleepy",
        "surprised" to "surprised",
        "sad" to "sad",
        "angry" to "angry",
        "thinking" to "thinking",
        "confused" to "confused",
        "annoyed" to "annoyed",
        // Legacy stems (older PNGs still shipped) keep mapping to nothing new.
        "normal" to "neutral",
        "confident" to "neutral",
        "eating" to "neutral",
        "laugh" to "happy",
        "excited" to "surprised",
        "lovely" to "heart_eyes",
        "flirty" to "wink",
        "shock" to "surprised"
    )

    private val tagRegex = Regex("\\[([a-zA-Z]+)\\]")

    /** First bracketed emotion tag in [text], lowercased, if any. */
    fun tagOf(text: String): String? =
        tagRegex.find(text)?.groupValues?.getOrNull(1)?.lowercase()

    /** Text suitable for speaking: drops [emotion] tags and *stage directions*. */
    fun speechText(text: String): String =
        text.replace(Regex("\\s*\\[[a-zA-Z]+]"), "")
            .replace(Regex("\\s*\\*[^*]{0,120}\\*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

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
