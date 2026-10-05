package com.cupcake.ui.screen.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cupcake.ai.CharacterManager
import com.cupcake.data.model.Character
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class CharacterViewModel @Inject constructor(
    private val characterManager: CharacterManager
) : ViewModel() {

    private val _characters = MutableStateFlow(characterManager.getAllCharacters())
    val characters = _characters.asStateFlow()

    val showCreateDialog = MutableStateFlow(false)
    val editingCharacterId = MutableStateFlow<String?>(null)
    val newCharName = MutableStateFlow("")
    val newCharPrompt = MutableStateFlow("")
    val newCharPersonality = MutableStateFlow("friendly")

    private fun refresh() {
        _characters.value = characterManager.getAllCharacters()
    }

    fun selectCharacter(id: String) {
        characterManager.selectCharacter(id)
        refresh()
    }

    fun editCharacter(character: Character) {
        editingCharacterId.value = character.id
        newCharName.value = character.name
        newCharPrompt.value = character.systemPrompt
        newCharPersonality.value = character.personality
        showCreateDialog.value = true
    }

    fun deleteCharacter(id: String) {
        characterManager.deleteCharacter(id)
        refresh()
    }

    fun saveCharacter() {
        val name = newCharName.value.trim()
        val prompt = newCharPrompt.value.trim()
        val personality = newCharPersonality.value.trim()
        
        if (name.isBlank() || prompt.isBlank()) return

        val character = Character(
            id = editingCharacterId.value ?: "custom_${System.currentTimeMillis()}",
            name = name,
            description = "Custom character",
            systemPrompt = prompt,
            avatar = "🎭",
            personality = personality.ifEmpty { "friendly" },
            languages = listOf("en", "bn"),
            isBuiltIn = false,
            isSelected = false
        )

        characterManager.saveCustomCharacter(character)
        refresh()
        clearForm()
    }

    fun clearForm() {
        editingCharacterId.value = null
        newCharName.value = ""
        newCharPrompt.value = ""
        newCharPersonality.value = "friendly"
    }
}