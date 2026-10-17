package com.cupcake.ai

/**
 * The one and only emotion-tag vocabulary.
 *
 * These seven tags are exactly what the bundled `liltii-v0.2` GGUF was fine-tuned to emit,
 * so every consumer MUST read them from here instead of re-declaring its own list:
 *
 *  - the training-data generators (`training/build_dataset.py`, `augment_*.py`),
 *  - [LilTiiPromptTemplate] (`SYSTEM_INSTRUCTIONS` + `CUTE_STYLE`),
 *  - [EmotionAssets] (tag -> 2D art stem -> ESP32 face),
 *  - [LilTiiEvaluator] (scoring "is the tag in the allowed set"),
 *  - [EmotionGrammar] (constrained decoding).
 *
 * Tags outside this set (e.g. `excited`, `lovely`, `flirty`) are still *tolerated* by
 * [EmotionAssets.tagToStem] so older or off-script model output degrades gracefully, but
 * they are never requested in a prompt and never enforced by the grammar.
 */
object EmotionTags {

    /** Canonical tags in prompt order. Keep in sync with the training data distribution. */
    val ALL: List<String> = listOf(
        "happy", "sleepy", "surprised", "sad", "angry", "thinking", "confused", "annoyed"
    )

    /** Canonical tags as a lookup set, for the scorer and any runtime validation. */
    val SET: Set<String> = ALL.toSet()

    /** `[happy] [sleepy] ...` — ready to embed in a prompt instruction line. */
    val PROMPT_LIST: String = ALL.joinToString(" ") { "[$it]" }

    private val TAG_REGEX = Regex("""\[([a-zA-Z]+)]""")

    /** True when [tag] (any case) is one of the [ALL] canonical tags. */
    fun isKnown(tag: String?): Boolean = tag != null && tag.lowercase() in SET

    /** First bracketed tag in [text], lowercased, or null when absent. */
    fun firstTag(text: String): String? =
        TAG_REGEX.find(text)?.groupValues?.getOrNull(1)?.lowercase()
}
