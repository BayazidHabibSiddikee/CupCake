package com.cupcake.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToneGuardTest {

    @Test
    fun `benign line is passed through unchanged`() {
        val line = "Hehe, nice move~ try again! 😏"
        assertThat(ToneGuard.sanitize(line)).isEqualTo(line)
    }

    @Test
    fun `whitespace is collapsed`() {
        assertThat(ToneGuard.sanitize("  hello \n\n  there  ")).isEqualTo("hello there")
    }

    @Test
    fun `hostile line is replaced with a playful one`() {
        val out = ToneGuard.sanitize("You idiot, I will kill you!")
        assertThat(out).isNotEmpty()
        assertThat(out).doesNotContain("idiot")
        assertThat(out).doesNotContain("kill")
    }

    @Test
    fun `words containing banned substrings are not flagged`() {
        // "study"/"diet"/"classic" must survive whole-word matching.
        val line = "I study while on a diet in classic style."
        assertThat(ToneGuard.sanitize(line)).isEqualTo(line)
    }

    @Test
    fun `runaway response is clamped`() {
        val out = ToneGuard.sanitize("word ".repeat(200))
        assertThat(out.length).isAtMost(221)
        assertThat(out).endsWith("…")
    }
}
