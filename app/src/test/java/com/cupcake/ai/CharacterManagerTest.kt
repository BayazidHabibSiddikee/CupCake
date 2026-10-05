package com.cupcake.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CharacterManagerTest {

    @Before
    fun setUp() {
        val tmp = Files.createTempDirectory("cupcake-chars").toFile()
        CharacterManager.initialize(tmp)
    }

    @Test
    fun `built in characters load`() {
        val all = CharacterManager.getAllCharacters()
        assertThat(all.map { it.id }).containsAtLeast(
            "cute_companion", "grumpy_bot", "tutor_bot", "sarcastic_bot", "rage_gamer"
        )
        assertThat(all).hasSize(5)
    }

    @Test
    fun `default current character is cute companion`() {
        // Reset selection state first
        CharacterManager.selectCharacter("cute_companion")
        assertThat(CharacterManager.getCurrentCharacter()?.id).isEqualTo("cute_companion")
    }

    @Test
    fun `select character switches selection`() {
        assertThat(CharacterManager.selectCharacter("grumpy_bot")).isTrue()
        assertThat(CharacterManager.getCurrentCharacter()?.id).isEqualTo("grumpy_bot")
        assertThat(CharacterManager.selectCharacter("nope")).isFalse()
    }

    @Test
    fun `system prompt contains character instructions`() {
        val prompt = CharacterManager.getSystemPromptForCharacter("tutor_bot")
        assertThat(prompt).contains("tutor")
    }

    @Test
    fun `system prompt appends game context`() {
        val prompt = CharacterManager.getSystemPromptForCharacter(
            "rage_gamer",
            "Game: TIC_TAC_TOE, Human won: true"
        )
        assertThat(prompt).contains("GAME CONTEXT")
    }

    @Test
    fun `custom character round trips through disk`() {
        val dir = Files.createTempDirectory("cupcake-custom").toFile()
        CharacterManager.initialize(dir)

        val saved = CharacterManager.saveCustomCharacter(
            com.cupcake.data.model.Character(
                id = "",
                name = "Unit",
                description = "d",
                systemPrompt = "be nice",
                avatar = "u",
                personality = "friendly"
            )
        )
        assertThat(saved.id).isNotEmpty()
        assertThat(File(dir, "characters/${saved.id}.json").exists()).isTrue()

        assertThat(CharacterManager.deleteCharacter(saved.id)).isTrue()
        assertThat(CharacterManager.getCharacter(saved.id)).isNull()
    }

    @Test
    fun `cannot delete built in character`() {
        assertThat(CharacterManager.deleteCharacter("cute_companion")).isFalse()
        assertThat(CharacterManager.getCharacter("cute_companion")).isNotNull()
    }
}
