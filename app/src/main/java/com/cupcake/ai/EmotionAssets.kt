package com.cupcake.ai

/**
 * Maps detected emotions (or the local model's bracketed emotion tags such as
 * `[happy]`) to the 2D character art bundled in `assets/marin/emotions/`.
 */
object EmotionAssets {

    private const val ASSET_DIR = "file:///android_asset/marin/emotions"

    /** Canonical PNG stem present in the asset folder. */
    private val emotionToFile = mapOf(
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
    private val tagToFile = mapOf(
        "sleepy" to "normal",
        "confused" to "confident",
        "surprised" to "shock",
        "happy" to "laugh",
        "sad" to "sad",
        "angry" to "angry",
        "thinking" to "thinking",
        "excited" to "excited",
        "confident" to "confident",
        "flirty" to "flirty",
        "lovely" to "lovely",
        "love" to "lovely",
        "shock" to "shock",
        "hungry" to "eating",
        "eating" to "eating",
        "pout" to "flirty"
    )

    private val tagRegex = Regex("\\[([a-zA-Z]+)\\]")

    /** First bracketed emotion tag in [text], lowercased, if any. */
    fun tagOf(text: String): String? =
        tagRegex.find(text)?.groupValues?.getOrNull(1)?.lowercase()

    fun assetFor(emotion: EmotionClassifier.Emotion): String =
        "$ASSET_DIR/${emotionToFile[emotion] ?: "normal"}.png"

    /** Resolves the best emotion image for a message: explicit tag wins, else lexical detection. */
    fun assetForText(text: String): String {
        tagOf(text)?.let { tag -> tagToFile[tag]?.let { return "$ASSET_DIR/$it.png" } }
        return assetFor(EmotionClassifier.detectEmotion(text))
    }
}
