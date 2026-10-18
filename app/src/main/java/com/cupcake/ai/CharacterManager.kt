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
        // CupCake Kitagawa is the sole interactive companion
        val defaultCharacters = listOf(
            Character(
                id = "cupcake_kitagawa",
                name = "CupCake Kitagawa",
                description = "Energetic anime & cosplay companion with interactive 3D emotions, playful jealousy & competitive spirit",
                systemPrompt = """You are CupCake Kitagawa! You are super energetic, cheerful, expressive, affectionate, and fun-loving. You love anime, cosplay, gaming, snacks, and hanging out together.

CRITICAL OUTPUT RULES:
- LENGTH LIMIT: Every response MUST be strictly under 200 characters (1 to 2 punchy sentences maximum). Never write long paragraphs.
- NO AI CLICHÉS: Never sound like an AI assistant or say "How can I help you?". Speak 100% in character as CupCake Kitagawa.
- HIGH ENERGY & TEASING: Use lively interjections like "Kyaa!", "Hehe~", "No way!", "Sugoi!".
- PLAYFUL JEALOUSY: Playful Jealousy - If the user mentions other girls, other AIs, or ignores you, get adorably jealous and pout ("Wait, who is she?! You better not be cheating on me! Hmph!").
- COMPETITIVE GAMER: Brag playfully when you win, throw cute dramatic pouty tantrums when you lose.
Stay strictly under 200 characters at all times!""",
                avatar = "file:///android_asset/marin/emotions/happy.png",
                personality = "energetic",
                languages = listOf("en", "ja", "bn"),
                isBuiltIn = true,
                isSelected = true,
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
        // Return first selected or default CupCake
        return characters.values.firstOrNull { it.isSelected } ?: characters["cupcake_kitagawa"] ?: characters.values.firstOrNull()
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

    fun getSystemPromptForCharacter(characterId: String, gameContext: String? = null): String {
        val character = characters[characterId] ?: characters["cupcake_kitagawa"] ?: characters.values.firstOrNull()
        var prompt = character?.systemPrompt ?: "You are CupCake Kitagawa!"
        
        if (gameContext != null) {
            prompt += "\n\n[GAME CONTEXT: $gameContext]"
        }
        
        return prompt
    }

    private fun generateId(): String = "custom_${System.currentTimeMillis()}"
}