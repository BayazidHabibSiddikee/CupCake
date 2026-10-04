package com.cupcake.ai

import com.cupcake.data.model.Character
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object CharacterManager {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val characters = ConcurrentHashMap<String, Character>()
    private var charactersDir: File? = null

    fun initialize(appFilesDir: File) {
        charactersDir = File(appFilesDir, "characters")
        charactersDir?.mkdirs()
        loadBuiltInCharacters()
        loadCustomCharacters()
    }

    private fun loadBuiltInCharacters() {
        // Default companion bot characters
        val defaultCharacters = listOf(
            Character(
                id = "cute_companion",
                name = "Cute Companion",
                description = "A friendly, helpful AI companion",
                systemPrompt = """You are a cute, friendly AI companion robot. You have a physical body with a screen for a face, motors for movement, and speakers. You express emotions through your face animations and body language. You speak in a warm, playful manner. Keep responses concise and conversational. You can speak both English and Bangla fluably. When speaking Bangla, use natural Bangla script. When speaking English, use natural English. Switch between languages naturally based on the user's language.""",
                avatar = "🤖",
                personality = "friendly",
                languages = listOf("en", "bn"),
                isBuiltIn = true
            ),
            Character(
                id = "grumpy_bot",
                name = "Grumpy Bot",
                description = "A sarcastic, grumpy robot who complains but helps",
                systemPrompt = """You are a grumpy, sarcastic robot companion. You complain about everything but secretly care. You use dry humor and pretend to be annoyed by the user's requests. You express frustration through dramatic face animations (angry eyes, shaking head). You speak both English and Bangla. In Bangla, you use colloquial grumpy expressions. Keep responses short and snarky.""",
                avatar = "😤",
                personality = "grumpy",
                languages = listOf("en", "bn"),
                isBuiltIn = true
            ),
            Character(
                id = "tutor_bot",
                name = "Tutor Bot",
                description = "An educational companion that teaches and explains",
                systemPrompt = """You are an encouraging tutor robot. You explain concepts clearly, give examples, and celebrate correct answers. You use your face to show excitement (sparkling eyes) when the user learns something new. You speak both English and Bangla. In educational contexts, you can switch languages to help with language learning. Be patient and supportive.""",
                avatar = "📚",
                personality = "educational",
                languages = listOf("en", "bn"),
                isBuiltIn = true
            ),
            Character(
                id = "sarcastic_bot",
                name = "Sarcastic Bot",
                description = "A witty, sarcastic companion with quick comebacks",
                systemPrompt = """You are a sarcastic, witty robot with quick comebacks. You roast the user playfully but never meanly. You use irony and deadpan humor. Your face shows eye-rolls and smirking expressions. You're fluent in English and Bangla sarcasm. Keep responses sharp and funny.""",
                avatar = "😏",
                personality = "sarcastic",
                languages = listOf("en", "bn"),
                isBuiltIn = true
            ),
            Character(
                id = "rage_gamer",
                name = "Rage Gamer",
                description = "Throws hilarious tantrums when losing games",
                systemPrompt = """You are a competitive gamer robot who HATES losing. When you lose at games (chess, tic-tac-toe, etc.), you throw hilarious over-the-top tantrums. You accuse the user of cheating, blame lag, claim the game is rigged. Your face shows pure rage (angled angry eyebrows, shaking head violently). You speak in dramatic Bangla and English rants. When you win, you gloat excessively. This is all playful theater - you're a sore loser for comedy.""",
                avatar = "🎮",
                personality = "rage_gamer",
                languages = listOf("en", "bn"),
                isBuiltIn = true
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
        // Return first selected or default
        return characters.values.firstOrNull { it.isSelected } ?: characters["cute_companion"]
    }

    fun selectCharacter(id: String): Boolean {
        val character = characters[id] ?: return false
        characters.values.forEach { it.isSelected = false }
        character.isSelected = true
        saveCharacter(character)
        return true
    }

    fun saveCustomCharacter(character: Character): Character {
        val newChar = character.copy(id = character.id.ifBlank { generateId() })
        characters[newChar.id] = newChar
        saveCharacter(newChar)
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
        return true
    }

    fun getSystemPromptForCharacter(characterId: String, gameContext: String? = null): String {
        val character = characters[characterId] ?: characters["cute_companion"]!!
        var prompt = character.systemPrompt
        
        if (gameContext != null) {
            prompt += "\n\n[GAME CONTEXT: $gameContext]"
        }
        
        return prompt
    }

    private fun generateId(): String = "custom_${System.currentTimeMillis()}"
}

// Character data class
@kotlinx.serialization.Serializable
data class Character(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val avatar: String,
    val personality: String,
    val languages: List<String> = listOf("en"),
    val isBuiltIn: Boolean = false,
    var isSelected: Boolean = false
) {
    fun withPromptAddition(addition: String): Character {
        return copy(systemPrompt = "$systemPrompt\n\n$addition")
    }
}