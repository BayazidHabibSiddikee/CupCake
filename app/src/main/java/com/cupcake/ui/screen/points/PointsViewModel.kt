package com.cupcake.ui.screen.points

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cupcake.game.GameManager
import com.cupcake.game.PointsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class PointsViewModel @Inject constructor(
    private val pointsManager: PointsManager,
    private val gameManager: GameManager
) : ViewModel() {

    val points = pointsManager.points
    val level = pointsManager.level
    val xpProgress = pointsManager.xpProgress
    val totalGamesPlayed = pointsManager.totalGamesPlayed
    val totalWins = pointsManager.totalWins
    val gameStats = pointsManager.gameStats
    val achievements = pointsManager.achievements

    /** Marin's cravings / affection, surfaced alongside the head-to-head record. */
    val affection = com.cupcake.ai.DemandManager.affection
    val currentDemand = com.cupcake.ai.DemandManager.currentDemand

    fun getLevelProgress(): Pair<Long, Long> = pointsManager.getLevelProgress()

    fun botStatus(): GameManager.BotStatus = gameManager.getBotStatus()

    /** Pays for Marin's currently pending craving with coins, when affordable. */
    fun fulfillDemand() { com.cupcake.ai.DemandManager.fulfillFromUi() }
}