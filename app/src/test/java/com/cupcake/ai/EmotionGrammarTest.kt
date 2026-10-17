package com.cupcake.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the single-source-of-truth for the companion's emotion vocabulary. If someone
 * adds/renames a tag in [EmotionTags] without updating the prompt, the GBNF grammar, the
 * art map, or the scorer, one of these tests fails — which is exactly the drift that made
 * the old build advertise one tag set while the model was trained on another.
 */
class EmotionGrammarTest {

    private fun tagLiterals(grammar: String): List<String> {
        val tagLine = grammar.lineSequence().first { it.trimStart().startsWith("tag") }
        return Regex("""\[([a-z]+)]""").findAll(tagLine).map { it.groupValues[1] }.toList()
    }

    @Test
    fun `tag rule is exactly the canonical vocabulary`() {
        assertEquals(EmotionTags.ALL, tagLiterals(EmotionGrammar.TAG_ACTION))
        assertEquals(EmotionTags.ALL, tagLiterals(EmotionGrammar.TAG_ONLY))
    }

    @Test
    fun `grammar declares root and the reply shape`() {
        listOf(EmotionGrammar.TAG_ACTION, EmotionGrammar.TAG_ONLY).forEach { grammar ->
            assertTrue("needs a root rule", grammar.contains("root"))
            assertTrue("needs an action rule", grammar.contains("action"))
            assertTrue("needs a body rule", grammar.contains("body"))
            assertTrue(
                "body must exclude '<' so <|im_end|> cannot leak into visible text",
                grammar.contains("[^<\\n]")
            )
        }
    }

    @Test
    fun `tag action grammar requires the action beat`() {
        val root = EmotionGrammar.TAG_ACTION.lineSequence().first { it.trimStart().startsWith("root") }
        assertTrue(root.contains("action"))
    }

    @Test
    fun `every canonical tag resolves to a known art stem`() {
        EmotionTags.ALL.forEach { tag ->
            val stem = EmotionAssets.stemForText("[$tag] *waves* hello")
            assertTrue("tag '$tag' has no art stem", stem in EmotionAssets.ALL_STEMS)
        }
    }

    @Test
    fun `system instructions and cute style advertise the canonical set`() {
        val prompt = LilTiiPromptTemplate.buildPrompt("hi")
        EmotionTags.ALL.forEach { tag ->
            assertTrue("SYSTEM_INSTRUCTIONS missing [$tag]", prompt.contains("[$tag]"))
            assertTrue(
                "CUTE_STYLE missing [$tag]",
                LilTiiPromptTemplate.CUTE_STYLE.contains("[$tag]")
            )
        }
    }

    @Test
    fun `isKnown accepts canonical tags and rejects everything else`() {
        assertTrue(EmotionTags.isKnown("HAPPY"))
        assertTrue(EmotionTags.isKnown("confused"))
        assertFalse(EmotionTags.isKnown("excited"))
        assertFalse(EmotionTags.isKnown("lovely"))
        assertFalse(EmotionTags.isKnown(null))
    }

    @Test
    fun `firstTag reads the leading tag`() {
        assertEquals("sad", EmotionTags.firstTag("[sad] *sniffs* ami mon kharap"))
        assertEquals("happy", EmotionTags.firstTag("  [Happy] yay"))
        assertEquals(null, EmotionTags.firstTag("no tag here"))
    }
}
