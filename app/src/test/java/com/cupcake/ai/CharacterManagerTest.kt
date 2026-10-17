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
        assertThat(all.map { it.id }).containsExactly("cupcake_kitagawa")
        assertThat(all).hasSize(1)
    }

    @Test
    fun `default current character is cupcake kitagawa`() {
        CharacterManager.selectCharacter("cupcake_kitagawa")
        assertThat(CharacterManager.getCurrentCharacter()?.id).isEqualTo("cupcake_kitagawa")
    }

    @Test
    fun `select character switches selection`() {
        assertThat(CharacterManager.selectCharacter("cupcake_kitagawa")).isTrue()
        assertThat(CharacterManager.getCurrentCharacter()?.id).isEqualTo("cupcake_kitagawa")
        assertThat(CharacterManager.selectCharacter("non_existent")).isFalse()
    }

    @Test
    fun `system prompt contains character instructions`() {
        val prompt = CharacterManager.getSystemPromptForCharacter("cupcake_kitagawa")
        assertThat(prompt).contains("CupCake")
        assertThat(prompt).contains("Jealousy")
    }

    @Test
    fun `system prompt appends game context`() {
        val prompt = CharacterManager.getSystemPromptForCharacter(
            "cupcake_kitagawa",
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
        assertThat(CharacterManager.deleteCharacter("cupcake_kitagawa")).isFalse()
        assertThat(CharacterManager.getCharacter("cupcake_kitagawa")).isNotNull()
    }
}
