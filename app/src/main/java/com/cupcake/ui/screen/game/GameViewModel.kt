package com.cupcake.ui.screen.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cupcake.ai.EnergyManager
import com.cupcake.game.GameManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class GameViewModel @Inject constructor(
    private val gameManager: GameManager,
    private val energyManager: EnergyManager
) : ViewModel() {

    private val _gameState = MutableStateFlow<GameManager.Result?>(null)
    val gameState = _gameState.asStateFlow()

    private val _availableGames = MutableStateFlow<List<GameOption>>(rememberGameOptions())
    val availableGames = _availableGames.asStateFlow()

    private val _showGameSelector = MutableStateFlow(false)
    val showGameSelector = _showGameSelector.asStateFlow()

    init {
        gameManager.onGameStateChanged = { state ->
            _gameState.value = state?.let {
                com.cupcake.game.GameManager.Result(true, "Bot moved", it)
            }
        }
    }

    fun setShowGameSelector(visible: Boolean) {
        _showGameSelector.value = visible
    }

    val selectedDifficulty = MutableStateFlow(com.cupcake.game.TicTacToeEngine.Difficulty.NORMAL)

    fun startGame(gameOption: GameOption, difficulty: com.cupcake.game.TicTacToeEngine.Difficulty) {
        if (!energyManager.consumeEnergy(com.cupcake.ai.EnergyManager.ENERGY_PER_GAME)) return
        
        val result = gameManager.startGame(gameOption.type, difficulty)
        _gameState.value = com.cupcake.game.GameManager.Result(true, "Game started", result)
        _showGameSelector.value = false
    }

    fun makeMove(position: Int) {
        gameManager.makeHumanMove(position)
        _gameState.value = gameManager.getCurrentState()?.let { 
            com.cupcake.game.GameManager.Result(true, "Move made", it) 
        }
    }

    fun endGame() {
        gameManager.startGame(com.cupcake.game.Game.GameType.TIC_TAC_TOE) // reset
        _gameState.value = null
        _showGameSelector.value = true
    }

    fun restartGame() {
        val currentType = _gameState.value?.state?.gameType ?: com.cupcake.game.Game.GameType.TIC_TAC_TOE
        val result = gameManager.startGame(currentType, selectedDifficulty.value)
        _gameState.value = com.cupcake.game.GameManager.Result(true, "Game restarted", result)
    }

    private fun rememberGameOptions(): List<GameOption> {
        return listOf(
            GameOption(
                type = com.cupcake.game.Game.GameType.TIC_TAC_TOE,
                name = "Tic-Tac-Toe",
                description = "Classic 3x3 with rage reactions",
                icon = "⭕❌",
                personality = "rage_gamer"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.CHESS,
                name = "Chess",
                description = "Play against Stockfish engine",
                icon = "♟️",
                personality = "rage_gamer"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.ROCK_PAPER_SCISSORS,
                name = "Rock Paper Scissors",
                description = "Quick rounds with trash talk",
                icon = "🪨📄✂️",
                personality = "sarcastic"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.GUESS_NUMBER,
                name = "Guess the Number",
                description = "Bot picks 1-100, you guess",
                icon = "🔢",
                personality = "grumpy"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.MATH_QUIZ,
                name = "Math Quiz",
                description = "Bot gives wrong answers on purpose",
                icon = "➕➖✖️➗",
                personality = "tutor_bot"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.CLICKER,
                name = "Cupcake Clicker",
                description = "Mindless tapping, sarcastic bot",
                icon = "🧁",
                personality = "sarcastic"
            )
        )
    }
}

data class GameOption(
    val type: com.cupcake.game.Game.GameType,
    val name: String,
    val description: String,
    val icon: String,
    val personality: String
)