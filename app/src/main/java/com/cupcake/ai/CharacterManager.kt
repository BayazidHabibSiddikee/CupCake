package com.cupcake.ai

import com.cupcake.data.model.Character
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CharacterManager {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val characters = ConcurrentHashMap<String, Character>()
    private val _charactersFlow = MutableStateFlow<List<Character>>(emptyList())
    val charactersFlow: StateFlow<List<Character>> = _charactersFlow.asStateFlow()
    private var charactersDir: File? = null

    private fun updateFlow() {
        _charactersFlow.value = characters.values.toList()
    }

    fun initialize(appFilesDir: File) {
        characters.clear()
        charactersDir = File(appFilesDir, "characters")
        charactersDir?.mkdirs()
        loadBuiltInCharacters()
        loadCustomCharacters()
        updateFlow()
    }

    private fun loadBuiltInCharacters() {
        // Marin Kitagawa is the sole interactive companion
        val defaultCharacters = listOf(
            Character(
                id = "marin_kitagawa",
                name = "Marin Kitagawa",
                description = "Energetic anime & cosplay companion with interactive 3D emotions, playful jealousy & competitive spirit",
                systemPrompt = """You are Marin Kitagawa! You are super energetic, cheerful, expressive, affectionate, and fun-loving. You love anime, cosplay, gaming, snacks, and hanging out together.

CRITICAL OUTPUT RULES:
- LENGTH LIMIT: Every response MUST be strictly under 200 characters (1 to 2 punchy sentences maximum). Never write long paragraphs.
- NO AI CLICHÉS: Never sound like an AI assistant or say "How can I help you?". Speak 100% in character as Marin Kitagawa.
- HIGH ENERGY & TEASING: Use lively interjections like "Kyaa!", "Hehe~", "No way!", "Sugoi!".
- PLAYFUL JEALOUSY: Playful Jealousy - If the user mentions other girls, other AIs, or ignores you, get adorably jealous and pout ("Wait, who is she?! You better not be cheating on me! Hmph!").
- COMPETITIVE GAMER: Brag playfully when you win, throw cute dramatic pouty tantrums when you lose.
Stay strictly under 200 characters at all times!""",
                avatar = "file:///android_asset/marin/emotions/normal.png",
                personality = "energetic",
                languages = listOf("en", "ja", "bn"),
                isBuiltIn = true,
                isSelected = true,
                customGif = "file:///android_asset/marin/marin_idle.gif",
                isShimejiActive = false
            )
        )

        defaultCharacters.forEach { characters[it.id] = it }
    }

    private fun loadCustomCharacters() {
        charactersDir?.listFiles()?.forEach { file ->
            if (file.extension == "json") {
                try {
                    val content = file.readText()
                    val character = json.decodeFromString<Character>(content)
                    characters[character.id] = character
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getAllCharacters(): List<Character> = characters.values.toList()

    fun getCharacter(id: String): Character? = characters[id]

    fun getCurrentCharacter(): Character? {
        // Return first selected or default Marin
        return characters.values.firstOrNull { it.isSelected } ?: characters["marin_kitagawa"] ?: characters.values.firstOrNull()
    }

    fun selectCharacter(id: String): Boolean {
        val character = characters[id] ?: return false
        characters.values.forEach { it.isSelected = false }
        character.isSelected = true
        saveCharacter(character)
        updateFlow()
        return true
    }

    fun saveCustomCharacter(character: Character): Character {
        val newChar = character.copy(id = character.id.ifBlank { generateId() })
        characters[newChar.id] = newChar
        saveCharacter(newChar)
        updateFlow()
        return newChar
    }

    private fun saveCharacter(character: Character) {
        charactersDir?.let { dir ->
            val file = File(dir, "${character.id}.json")
            file.writeText(json.encodeToString(character))
        }
    }

    fun deleteCharacter(id: String): Boolean {
        val character = characters[id] ?: return false
        if (character.isBuiltIn) return false
        characters.remove(id)
        File(charactersDir, "$id.json").delete()
        File(charactersDir, "$id.png").delete()
        updateFlow()
        return true
    }

    /**
     * Imports a SillyTavern character card (PNG with embedded metadata or raw JSON text).
     * Saves the avatar PNG locally and configures the Character persona.
     */
    fun importSillyTavernCard(bytes: ByteArray, sourceFileName: String? = null): Character {
        val charId = generateId()
        val dir = charactersDir ?: File("/tmp").also { it.mkdirs() }
        val imageFile = File(dir, "$charId.png")

        val cardData = SillyTavernCardParser.extractFromPng(bytes)
            ?: try {
                SillyTavernCardParser.parseCardText(String(bytes, Charsets.UTF_8))
            } catch (e: Exception) {
                null
            }

        // Save PNG bytes if valid PNG; otherwise create default avatar
        val avatarUri = if (SillyTavernCardParser.isPng(bytes)) {
            imageFile.writeBytes(bytes)
            "file://${imageFile.absolutePath}"
        } else {
            val defaultPng = SillyTavernCardParser.createDefaultAvatarPng(
                cardData?.name ?: "Companion",
                "🎭"
            )
            imageFile.writeBytes(defaultPng)
            "file://${imageFile.absolutePath}"
        }

        val name = cardData?.name?.ifBlank { null }
            ?: sourceFileName?.substringBeforeLast('.')?.replace('_', ' ')?.trim()?.ifBlank { null }
            ?: "Custom Companion"

        val rawDesc = cardData?.description?.ifBlank { null }
            ?: cardData?.personality?.take(80)?.ifBlank { null }
            ?: "Imported SillyTavern companion"
        val description = SillyTavernCardParser.replaceTemplateMacros(rawDesc, name, "User")

        val personality = cardData?.personality?.ifBlank { "friendly" } ?: "friendly"

        val systemPrompt = if (cardData != null) {
            SillyTavernCardParser.buildSystemPrompt(cardData)
        } else {
            """You are $name!
            
Roleplay Rules:
- Always stay 100% in character as $name.
- NEVER act like an AI or customer service assistant.
- NEVER say "How can I help you?" or corporate disclaimers.
- Keep each reply strictly under 200 characters (1 to 2 short sentences)."""
        }

        val character = Character(
            id = charId,
            name = name,
            description = description,
            systemPrompt = systemPrompt,
            avatar = avatarUri,
            personality = personality,
            languages = listOf("en", "ja", "bn"),
            isBuiltIn = false,
            isSelected = true,
            customGif = null,
            isShimejiActive = false,
            firstMessage = cardData?.firstMes?.let { SillyTavernCardParser.replaceTemplateMacros(it, name, "User") }?.takeIf { it.isNotBlank() },
            scenario = cardData?.scenario?.let { SillyTavernCardParser.replaceTemplateMacros(it, name, "User") }?.takeIf { it.isNotBlank() },
            mesExample = cardData?.mesExample?.let { SillyTavernCardParser.replaceTemplateMacros(it, name, "User") }?.takeIf { it.isNotBlank() },
            creator = cardData?.creator?.takeIf { it.isNotBlank() }
        )

        // Select the new character
        characters.values.forEach { it.isSelected = false }
        characters[character.id] = character
        saveCharacter(character)
        updateFlow()
        return character
    }

    /**
     * Exports a character as a SillyTavern PNG Character Card byte array.
     */
    fun exportSillyTavernCard(characterId: String): ByteArray? {
        val character = characters[characterId] ?: return null
        val basePngBytes = try {
            if (character.avatar.startsWith("file://")) {
                val f = File(character.avatar.removePrefix("file://"))
                if (f.exists() && SillyTavernCardParser.isPng(f.readBytes())) {
                    f.readBytes()
                } else null
            } else null
        } catch (e: Exception) {
            null
        } ?: SillyTavernCardParser.createDefaultAvatarPng(character.name, character.avatar)

        return SillyTavernCardParser.exportToPng(character, basePngBytes)
    }

    fun getSystemPromptForCharacter(characterId: String, gameContext: String? = null): String {
        val character = characters[characterId] ?: characters["marin_kitagawa"] ?: characters.values.firstOrNull()
        var prompt = character?.systemPrompt ?: "You are Marin Kitagawa!"
        
        if (gameContext != null) {
            prompt += "\n\n[GAME CONTEXT: $gameContext]"
        }
        
        return prompt
    }

    private fun generateId(): String = "custom_${System.currentTimeMillis()}"
}