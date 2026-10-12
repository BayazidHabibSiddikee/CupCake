package com.cupcake.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cupcake.ai.CharacterManager
import com.cupcake.ai.EnergyManager
import com.cupcake.data.model.Character
import com.cupcake.game.Game
import com.cupcake.game.PointsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val characterManager: CharacterManager,
    private val pointsManager: PointsManager,
    private val energyManager: EnergyManager
) : ViewModel() {

    val characters = characterManager.charactersFlow
    val points = pointsManager.points
    val level = pointsManager.level
    val xpProgress = pointsManager.xpProgress
    val totalGamesPlayed = pointsManager.totalGamesPlayed
    val totalWins = pointsManager.totalWins
    val energy = energyManager.energy

    private val _companionQuote = MutableStateFlow("Kyaa! Let's play games today! Don't you dare ignore me~ ✨")
    val companionQuote = _companionQuote.asStateFlow()

    private val _companionEmotion = MutableStateFlow("happy")
    val companionEmotion = _companionEmotion.asStateFlow()

    fun getLevelProgress(): Pair<Long, Long> = pointsManager.getLevelProgress()

    fun interactWithMarin(action: String) {
        viewModelScope.launch {
            when (action) {
                "pat" -> {
                    pointsManager.awardPoints(15, Game.GameType.CLICKER, true)
                    _companionEmotion.value = "happy"
                    _companionQuote.value = "Ehehe~ Patting my head? You're so sweet! (+15 Bond XP 💕)"
                }
                "snack" -> {
                    pointsManager.awardPoints(25, Game.GameType.CLICKER, true)
                    _companionEmotion.value = "excited"
                    _companionQuote.value = "Yaaay! Snacks! You're the best partner ever! (+25 Bond XP 🍰)"
                }
                "tease" -> {
                    _companionEmotion.value = "angry"
                    _companionQuote.value = "Hey! What was that for?! Hmph, now you have to win a game against me! 😤"
                }
                "pout" -> {
                    _companionEmotion.value = "angry"
                    _companionQuote.value = "Are you looking at other games?! Look only at me! Hmph! 💢"
                }
                "dance" -> {
                    pointsManager.awardPoints(30, Game.GameType.CLICKER, true)
                    _companionEmotion.value = "excited"
                    _companionQuote.value = "Kyaa! Look at my dance moves! Are you grooving with me?! 💃✨ (+30 Bond XP)"
                }
                else -> {
                    _companionEmotion.value = "happy"
                    _companionQuote.value = "Let's level up our bond! Pick a game below! 🎮"
                }
            }
        }
    }
}
