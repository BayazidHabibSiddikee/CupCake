package com.cupcake.ai

import com.cupcake.data.model.Character
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SillyTavernCardParserTest {

    // Minimal valid PNG header + IHDR + IDAT + IEND bytes for testing PNG chunk manipulation
    private val minimalPngBytes: ByteArray = byteArrayOf(
        // PNG Signature (8 bytes)
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
        0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
        // IHDR chunk (13 bytes data)
        0x00, 0x00, 0x00, 0x0D,
        'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
        0x00, 0x00, 0x00, 0x01, // width: 1
        0x00, 0x00, 0x00, 0x01, // height: 1
        0x08, 0x02, 0x00, 0x00, 0x00, // 8-bit truecolor
        0x90.toByte(), 0x77.toByte(), 0x53.toByte(), 0xDE.toByte(), // CRC
        // IDAT chunk (10 bytes)
        0x00, 0x00, 0x00, 0x0A,
        'I'.code.toByte(), 'D'.code.toByte(), 'A'.code.toByte(), 'T'.code.toByte(),
        0x78.toByte(), 0x9C.toByte(), 0x63.toByte(), 0x60.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x02.toByte(), 0x00.toByte(), 0x01.toByte(),
        0xE5.toByte(), 0x27.toByte(), 0xDE.toByte(), 0xFC.toByte(), // CRC
        // IEND chunk
        0x00, 0x00, 0x00, 0x00,
        'I'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'D'.code.toByte(),
        0xAE.toByte(), 0x42.toByte(), 0x60.toByte(), 0x82.toByte() // CRC
    )

    @Before
    fun setUp() {
        val tmp = Files.createTempDirectory("cupcake-chars-tavern").toFile()
        CharacterManager.initialize(tmp)
    }

    @Test
    fun `parseCardJson extracts V2 card specification`() {
        val v2Json = """
            {
              "spec": "chara_card_v2",
              "spec_version": "2.0",
              "data": {
                "name": "Rem",
                "description": "Demon maid at Roswaal mansion",
                "personality": "loyal, polite, fiercely protective",
                "scenario": "Talking in the study room",
                "first_mes": "Good day, Subaru-kun. How may Rem assist you?",
                "mes_example": "<START>\n{{user}}: Rem!\n{{char}}: Rem is always here for you.",
                "system_prompt": "You are Rem from Re:Zero."
              }
            }
        """.trimIndent()

        val parsed = SillyTavernCardParser.parseCardJson(v2Json)
        assertThat(parsed).isNotNull()
        assertThat(parsed?.name).isEqualTo("Rem")
        assertThat(parsed?.description).isEqualTo("Demon maid at Roswaal mansion")
        assertThat(parsed?.personality).isEqualTo("loyal, polite, fiercely protective")
        assertThat(parsed?.firstMes).isEqualTo("Good day, Subaru-kun. How may Rem assist you?")
        assertThat(parsed?.systemPrompt).isEqualTo("You are Rem from Re:Zero.")
    }

    @Test
    fun `parseCardJson extracts V1 card specification`() {
        val v1Json = """
            {
              "name": "Megumin",
              "description": "Arch-wizard of the Crimson Magic Clan",
              "personality": "chuunibyou, dramatic, explosion-obsessed",
              "first_mes": "Behold the greatest magic of all: EXPLOSION!"
            }
        """.trimIndent()

        val parsed = SillyTavernCardParser.parseCardJson(v1Json)
        assertThat(parsed).isNotNull()
        assertThat(parsed?.name).isEqualTo("Megumin")
        assertThat(parsed?.personality).isEqualTo("chuunibyou, dramatic, explosion-obsessed")
        assertThat(parsed?.firstMes).isEqualTo("Behold the greatest magic of all: EXPLOSION!")
    }

    @Test
    fun `buildSystemPrompt enforces CupCake length limits and character traits`() {
        val card = SillyTavernCardParser.SillyTavernCardData(
            name = "Marin",
            description = "High school gyaru who loves cosplay",
            personality = "energetic, teasing",
            firstMes = "Hey hey! What cosplay should we do next?"
        )

        val prompt = SillyTavernCardParser.buildSystemPrompt(card)
        assertThat(prompt).contains("Marin")
        assertThat(prompt).contains("High school gyaru")
        assertThat(prompt).contains("Stay 100% in character")
        assertThat(prompt).contains("Never give dry, cold, or one-word replies")
    }

    @Test
    fun `exportToPng and extractFromPng roundtrips character card`() {
        val character = Character(
            id = "custom_rem",
            name = "Rem",
            description = "Demon maid at Roswaal mansion",
            systemPrompt = "You are Rem.",
            avatar = "file:///android_asset/rem.png",
            personality = "loyal and caring",
            firstMessage = "Hello dear user!",
            scenario = "Meeting in the garden"
        )

        val exportedPngBytes = SillyTavernCardParser.exportToPng(character, minimalPngBytes)
        assertThat(SillyTavernCardParser.isPng(exportedPngBytes)).isTrue()
        assertThat(exportedPngBytes.size).isGreaterThan(minimalPngBytes.size)

        val extracted = SillyTavernCardParser.extractFromPng(exportedPngBytes)
        assertThat(extracted).isNotNull()
        assertThat(extracted?.name).isEqualTo("Rem")
        assertThat(extracted?.description).isEqualTo("Demon maid at Roswaal mansion")
        assertThat(extracted?.personality).isEqualTo("loyal and caring")
        assertThat(extracted?.firstMes).isEqualTo("Hello dear user!")
        assertThat(extracted?.scenario).isEqualTo("Meeting in the garden")
    }

    @Test
    fun `CharacterManager imports SillyTavern PNG card and activates character`() {
        val character = Character(
            id = "custom_aqua",
            name = "Aqua",
            description = "Goddess of Water",
            systemPrompt = "You are Aqua, water goddess.",
            avatar = "🎭",
            personality = "smug, dramatic, crying easily",
            firstMessage = "KAZUMAAA! Don't leave me behind!"
        )

        val pngCardBytes = SillyTavernCardParser.exportToPng(character, minimalPngBytes)

        val imported = CharacterManager.importSillyTavernCard(pngCardBytes, "aqua_card.png")
        assertThat(imported.name).isEqualTo("Aqua")
        assertThat(imported.firstMessage).isEqualTo("KAZUMAAA! Don't leave me behind!")
        assertThat(imported.personality).isEqualTo("smug, dramatic, crying easily")
        assertThat(imported.isSelected).isTrue()
        assertThat(imported.avatar).startsWith("file://")
        assertThat(imported.systemPrompt).contains("Stay 100% in character")

        // Check it's retrievable from CharacterManager
        val fetched = CharacterManager.getCharacter(imported.id)
        assertThat(fetched).isNotNull()
        assertThat(fetched?.name).isEqualTo("Aqua")
    }
}
