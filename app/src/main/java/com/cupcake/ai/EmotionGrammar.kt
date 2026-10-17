package com.cupcake.ai

/**
 * GBNF grammars for constrained decoding, used to make the companion's
 * `[emotion] *action* reply` format deterministic instead of hop
 * (a 0.6B model only emits a valid leading tag ~half the time when prompted alone).
 *
 * The grammar is fed to llama.cpp via `llama_sampler_init_grammar`, which masks the logits
 * so the model *cannot* produce an out-of-format prefix. The tag alternation is generated
 * from [EmotionTags.ALL], so the grammar can never drift from the prompt/art/scorer vocab.
 *
 * Notes on the byte-level rules:
 *  - `[^<\n]` excludes `<` (so a stray `<|im_end|>` can never leak into visible text) and
 *    newline; every UTF-8 continuation byte of Bangla/emoji text satisfies it, since those
 *    bytes are all >= 0x80 and never collide with the excluded ASCII bytes.
 *  - `[^*<\n]` additionally excludes `*`, which is the action terminator.
 */
object EmotionGrammar {

    /** Grammar start symbol passed to `llama_sampler_init_grammar`. */
    const val ROOT: String = "root"

    private val TAGS: String = EmotionTags.ALL.joinToString(" | ") { "\"[$it]\"" }

    /** `[tag] *action* reply` — tag, action and non-empty reply are all required. */
    val TAG_ACTION: String = """
        root   ::= tag " " action " " body
        tag    ::= $TAGS
        action ::= "*" [^*<\n]+ "*"
        body   ::= [^<\n]+
    """.trimIndent()

    /** `[tag] reply` or `[tag] *action* reply` — tag required, action optional. */
    val TAG_ONLY: String = """
        root   ::= tag " " action " " body | tag " " body
        tag    ::= $TAGS
        action ::= "*" [^*<\n]+ "*"
        body   ::= [^<\n]+
    """.trimIndent()
}
