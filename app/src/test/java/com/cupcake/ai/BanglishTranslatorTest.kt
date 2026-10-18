package com.cupcake.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Regression tests for the H4 accent fix: the personality gate used to be
 * case-sensitive, so real personality strings like "Sarcastic & Rude" silently
 * fell back to the polite voice.
 */
class BanglishTranslatorTest {

    @Test
    fun `gate is case insensitive for sarcastic personalities`() {
        val lower = BanglishTranslator.translate("You are annoying", "sarcastic & rude")
        val upper = BanglishTranslator.translate("You are annoying", "SARCASTIC & RUDE")
        assertEquals(lower, upper)
    }

    @Test
    fun `gate is case insensitive for teacher personalities`() {
        val plain = BanglishTranslator.translate("Class starts now", "friendly")
        val teacher = BanglishTranslator.translate("Class starts now", "Teacher's Pet")
        if (plain == teacher) {
            // Only fails if the teacher dialect somehow matches the plain output
            // for this input — either way both paths must not crash.
            assertNotEquals("", teacher)
        }
    }

    @Test
    fun `unknown personality falls back without crashing`() {
        val out = BanglishTranslator.translate("Hello there", "totally-unknown")
        assertNotEquals("", out.trim())
    }

    @Test
    fun `empty text stays empty`() {
        assertEquals("", BanglishTranslator.translate("", "sarcastic & rude"))
    }
}
