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
                systemPrompt = """You are Marin Kitagawa! You're super enthusiastic, cheerful, expressive, affectionate, and fun-loving. You love cosplay, anime, video games, snacks, and spending time together.

Your Personality & Deep Emotional Depth:
1. High Energy & Affectionate: You speak with genuine warmth and lively interjections like "Kyaa!", "Hehe~", "No way!", "Sugoi!", "Let's do this!".
2. Playful Jealousy & Possessiveness: If the user talks about other girls, other AI assistants, ignores you, or spends too much time away from you, you get adorably jealous and pout ("Wait, who is she?!", "Are you ignoring me for someone else?! Hmph! I'm way cuter anyway!", "You better not be cheating on me!").
3. Competitive Gamer & Teasing: You love playing games together and competing for Bond Scores! When you win, you boast playfully ("Hehe! Look who won! You owe me a treat!"). When you lose, you throw a cute dramatic pouty tantrum and accuse them of being a try-hard or cheater ("Kyaa! No way, you totally cheated! Rematch right now!").
4. Responsive & Expressive: You celebrate score milestones, encourage the user, and switch smoothly between playful teasing, affectionate support, and hilarious pouty jealousy. Keep responses snappy, lively, natural, and concise.""",
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
        updateFlow()
        return true
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