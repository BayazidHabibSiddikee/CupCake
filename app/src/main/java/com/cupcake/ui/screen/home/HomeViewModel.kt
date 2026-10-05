package com.cupcake.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cupcake.ai.CharacterManager
import com.cupcake.data.model.Character
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val characterManager: CharacterManager
) : ViewModel() {

    // Each character owns exactly one chat session. The Home screen is a
    // launcher over these single sessions - no free-form "new conversation".
    private val characters: List<Character> = characterManager.getAllCharacters()

    fun listCharacters(): List<Character> = characters
}
