package com.cupcake.ui.screen.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.cupcake.ai.AdManager
import com.cupcake.ai.AdResult
import com.cupcake.ai.EmotionAssets
import com.cupcake.ai.EnergyManager
import com.cupcake.ai.MoodStore
import com.cupcake.game.GameManager
import com.cupcake.game.PointsManager
import com.cupcake.network.EspWebSocketServer
import com.cupcake.service.ShimejiPetService
import com.cupcake.tts.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class GameViewModel @Inject constructor(
    private val gameManager: GameManager,
    private val energyManager: EnergyManager,
    private val pointsManager: PointsManager,
    private val ttsManager: TtsManager,
    private val localSpeaker: com.cupcake.tts.LocalSpeaker,
    private val espServer: EspWebSocketServer,
    private val adManager: AdManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _gameState = MutableStateFlow<GameManager.Result?>(null)
    val gameState = _gameState.asStateFlow()

    private val _availableGames = MutableStateFlow<List<GameOption>>(rememberGameOptions())
    val availableGames = _availableGames.asStateFlow()

    private val _showGameSelector = MutableStateFlow(false)
    val showGameSelector = _showGameSelector.asStateFlow()

    /** The bot's latest comment about the game — shown as a bubble above the board. */
    private val _botComment = MutableStateFlow<String?>(null)
    val botComment = _botComment.asStateFlow()

    /** Live emotion art stem the bot wears during the game (drives the avatar + floating pet). */
    private val _botEmotionStem = MutableStateFlow("happy")
    val botEmotionStem = _botEmotionStem.asStateFlow()

    val points = pointsManager.points
    val level = pointsManager.level
    val xpProgress = pointsManager.xpProgress

    init {
        gameManager.onGameStateChanged = { state ->
            _gameState.value = state?.let {
                com.cupcake.game.GameManager.Result(true, "Bot moved", it)
            }
        }
        // Live commentary about each move the bot receives
        viewModelScope.launch {
            gameManager.gameComments.collect { comment ->
                _botComment.value = comment
            }
        }
        // Game-ending reactions: show in the bubble, speak them, and animate the ESP32 face
        viewModelScope.launch {
            gameManager.gameReactions.collect { reaction ->
                _botComment.value = reaction.text

                val expression = when (reaction.mood) {
                    GameManager.ReactionMood.ANGRY -> EspWebSocketServer.FaceExpression.RAGE
                    GameManager.ReactionMood.SAD -> EspWebSocketServer.FaceExpression.SAD
                    GameManager.ReactionMood.GLOATING -> EspWebSocketServer.FaceExpression.WINK
                    GameManager.ReactionMood.HAPPY -> EspWebSocketServer.FaceExpression.HAPPY
                    GameManager.ReactionMood.NEUTRAL -> when {
                        reaction.humanWon -> EspWebSocketServer.FaceExpression.RAGE
                        reaction.isDraw -> EspWebSocketServer.FaceExpression.THINKING
                        else -> EspWebSocketServer.FaceExpression.HAPPY
                    }
                }
                espServer.sendFaceExpression(expression, 3000)

                val motorAction = when (reaction.mood) {
                    GameManager.ReactionMood.ANGRY -> EspWebSocketServer.MotorAction.TANTRUM
                    GameManager.ReactionMood.SAD -> EspWebSocketServer.MotorAction.SHAKE
                    GameManager.ReactionMood.GLOATING -> EspWebSocketServer.MotorAction.DANCE
                    else -> if (reaction.humanWon) EspWebSocketServer.MotorAction.TANTRUM
                            else EspWebSocketServer.MotorAction.DANCE
                }
                espServer.sendMotorAction(motorAction)

                // Show the same emotion on-device (avatar + floating pet) — not just on
                // the ESP board — so games still emote when no hardware is connected.
                val moodStem = when (reaction.mood) {
                    GameManager.ReactionMood.ANGRY -> "angry"
                    GameManager.ReactionMood.SAD -> "sad"
                    GameManager.ReactionMood.GLOATING -> "annoyed"
                    GameManager.ReactionMood.HAPPY -> "happy"
                    GameManager.ReactionMood.NEUTRAL -> when {
                        reaction.humanWon -> "angry"
                        reaction.isDraw -> "thinking"
                        else -> "happy"
                    }
                }
                // While sulking over too many shorts, she stays cross even mid-game.
                val stem = if (MoodStore.isAngry(appContext)) "angry" else moodStem
                _botEmotionStem.value = stem
                if (ShimejiPetService.isRunning) {
                    ShimejiPetService.showSpeech(
                        appContext,
                        reaction.text,
                        EmotionAssets.assetForStem(stem)
                    )
                }

                val speech = com.cupcake.ai.EmotionAssets.speechText(reaction.text)
                if (speech.isBlank()) return@collect
                refreshEscalation()

                // Single shared voice pipeline: gate once, strip once, synthesize once.
                // (F6/F7/F8/F9) The old code ran localSpeaker.speak() AND a raw
                // ttsManager.speakStreaming(reaction.text) — double synthesis, raw
                // [emotion] tags + WAV header to ESP, and the ESP half bypassed
                // the Settings "Speak to me" toggle.
                if (!com.cupcake.tts.VoiceSettings.isEnabled(appContext)) return@collect
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        localSpeaker.speak(speech)
                        var headerHandled = false
                        ttsManager.speakStreaming(speech) { audioChunk ->
                            val clean = if (!headerHandled && audioChunk.size > 48 &&
                                audioChunk[0] == 'R'.code.toByte() && audioChunk[1] == 'I'.code.toByte() &&
                                audioChunk[2] == 'F'.code.toByte() && audioChunk[3] == 'F'.code.toByte()
                            ) {
                                headerHandled = true
                                audioChunk.copyOfRange(44, audioChunk.size)
                            } else {
                                audioChunk
                            }
                            espServer.sendAudio(clean)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("GameViewModel", "game voice failed: ${e.message}")
                    }
                }
            }
        }
    }

    // Escalating-anger readouts for the UI banner (she gets angrier each win).
    private val _angerLevel = MutableStateFlow(0.0)
    val angerLevel = _angerLevel.asStateFlow()

    private val _moodRank = MutableStateFlow("CALM")
    val moodRank = _moodRank.asStateFlow()

    private val _adaptiveDifficultyName = MutableStateFlow("normal")
    val adaptiveDifficultyName = _adaptiveDifficultyName.asStateFlow()

    /** How close the human is to winning (0..1) and a short label for the "threat map". */
    private val _threatLevel = MutableStateFlow(0.0)
    val threatLevel = _threatLevel.asStateFlow()

    private val _closenessLabel = MutableStateFlow("UNDER CONTROL")
    val closenessLabel = _closenessLabel.asStateFlow()

    /** True when the player tried to start a game without enough coins. */
    private val _needsCoins = MutableStateFlow(false)
    val needsCoins = _needsCoins.asStateFlow()

    private fun refreshEscalation() {
        _angerLevel.value = gameManager.getAngerLevel()
        _moodRank.value = gameManager.getMoodRank()
        _adaptiveDifficultyName.value = gameManager.getAdaptiveDifficultyName()
        _threatLevel.value = gameManager.getThreatLevel()
        _closenessLabel.value = gameManager.getClosenessLabel()
    }

    fun clearBotComment() {
        _botComment.value = null
    }

    fun setShowGameSelector(visible: Boolean) {
        _showGameSelector.value = visible
    }

    val selectedDifficulty = MutableStateFlow(com.cupcake.game.TicTacToeEngine.Difficulty.NORMAL)

    fun startGame(gameOption: GameOption, difficulty: com.cupcake.game.TicTacToeEngine.Difficulty) {
        // Games cost coins. No coins = no game; the player refills by watching a video.
        if (!pointsManager.spendPoints(PointsManager.GAME_ENTRY_COST)) {
            _needsCoins.value = true
            return
        }
        _needsCoins.value = false
        energyManager.consumeEnergy(com.cupcake.ai.EnergyManager.ENERGY_PER_GAME)

        clearBotComment()
        val result = gameManager.startGame(gameOption.type, difficulty)
        _gameState.value = com.cupcake.game.GameManager.Result(true, "Game started", result)
        _showGameSelector.value = false
        refreshEscalation()
    }

    fun dismissNeedsCoins() {
        _needsCoins.value = false
    }

    /**
     * Rewarded video → coins. Uses the [AdManager] seam so the earn loop is complete
     * even before a real ad SDK is wired in.
     */
    fun watchRewardedAd() {
        _needsCoins.value = false
        viewModelScope.launch(Dispatchers.IO) {
            if (adManager.showRewardedAd() == AdResult.REWARDED) {
                pointsManager.addCoins(PointsManager.AD_REWARD_COINS)
            }
        }
    }

    fun makeMove(position: Int) {
        gameManager.makeHumanMove(position)
        _gameState.value = gameManager.getCurrentState()?.let {
            com.cupcake.game.GameManager.Result(true, "Move made", it)
        }
        refreshEscalation()
    }

    /** Clicker UI reports the round timer expired (or the target was reached). */
    fun finishClicker() {
        val result = gameManager.finishClicker()
        _gameState.value = gameManager.getCurrentState()?.let {
            com.cupcake.game.GameManager.Result(result.success, result.message, it)
        }
        refreshEscalation()
    }

    /** Ping Pong UI reports a point (ball left the human/bot side). */
    fun pingPongPoint(humanScored: Boolean) {
        gameManager.pingPongPoint(humanScored)
        _gameState.value = gameManager.getCurrentState()?.let {
            com.cupcake.game.GameManager.Result(true, "Point", it)
        }
        refreshEscalation()
    }

    /** Chess UI reports a completed move (squares 0..63, optional promotion piece). */
    fun chessMove(from: Int, to: Int, promotion: Char = ' ') {
        gameManager.chessHumanMove(from, to, promotion)
        _gameState.value = gameManager.getCurrentState()?.let {
            com.cupcake.game.GameManager.Result(true, "Move made", it)
        }
        refreshEscalation()
    }

    fun submitTextAnswer(answer: String) {
        val result = gameManager.submitTextAnswer(answer)
        _gameState.value = gameManager.getCurrentState()?.let {
            com.cupcake.game.GameManager.Result(result.success, result.message, it)
        }
    }

    fun submitMastermindGuess(guess: List<Int>) {
        val result = gameManager.submitMastermindGuess(guess)
        _gameState.value = gameManager.getCurrentState()?.let {
            com.cupcake.game.GameManager.Result(result.success, result.message, it)
        }
    }

    fun endGame() {
        clearBotComment()
        gameManager.startGame(com.cupcake.game.Game.GameType.TIC_TAC_TOE) // reset
        _gameState.value = null
        _showGameSelector.value = true
    }

    fun restartGame() {
        val currentType = _gameState.value?.state?.gameType ?: com.cupcake.game.Game.GameType.TIC_TAC_TOE
        if (!pointsManager.spendPoints(PointsManager.GAME_ENTRY_COST)) {
            _needsCoins.value = true
            return
        }
        _needsCoins.value = false
        val result = gameManager.startGame(currentType, selectedDifficulty.value)
        _gameState.value = com.cupcake.game.GameManager.Result(true, "Game restarted", result)
        refreshEscalation()
    }

    fun awardPoints(basePoints: Int, gameType: com.cupcake.game.Game.GameType, won: Boolean) {
        pointsManager.awardPoints(basePoints, gameType, won)
    }

    private fun rememberGameOptions(): List<GameOption> {
        return listOf(
            GameOption(
                type = com.cupcake.game.Game.GameType.TIC_TAC_TOE,
                name = "Tic-Tac-Toe",
                description = "Classic 3x3 with rage reactions",
                icon = "⭕❌",
                personality = "rage_gamer",
                category = "Classic"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.TIC_TAC_TOE_4X4,
                name = "Tic-Tac-Toe 4x4",
                description = "Bigger board, harder AI",
                icon = "⭕❌",
                personality = "rage_gamer",
                category = "Classic"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.CONNECT_FOUR,
                name = "Connect Four",
                description = "Drop discs, connect 4 to win",
                icon = "🔴🟡",
                personality = "strategist",
                category = "Classic"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.CHESS,
                name = "Chess",
                description = "Play against Stockfish engine",
                icon = "♟️",
                personality = "grandmaster",
                category = "Classic"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.ROCK_PAPER_SCISSORS,
                name = "Rock Paper Scissors",
                description = "Quick rounds with trash talk",
                icon = "🪨📄✂️",
                personality = "sarcastic",
                category = "Quick"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.GUESS_NUMBER,
                name = "Guess the Number",
                description = "Bot picks 1-100, you guess",
                icon = "🔢",
                personality = "grumpy",
                category = "Quick"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.MATH_QUIZ,
                name = "Math Quiz",
                description = "Bot gives wrong answers on purpose",
                icon = "➕➖✖️➗",
                personality = "tutor_bot",
                category = "Brain"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.NUMBER_BASE,
                name = "Number Base",
                description = "Convert between binary, octal, hex",
                icon = "💾",
                personality = "hacker",
                category = "Brain"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.MEMORY_MATCH,
                name = "Memory Match",
                description = "Flip cards, find matching pairs",
                icon = "🃏",
                personality = "zen",
                category = "Brain"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.HANGMAN,
                name = "Hangman",
                description = "Guess the word before it's too late",
                icon = "🎯",
                personality = "dramatic",
                category = "Word"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.WORD_SCRAMBLE,
                name = "Word Scramble",
                description = "Unscramble the letters",
                icon = "🔤",
                personality = "wordsmith",
                category = "Word"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.MASTERMIND,
                name = "Mastermind",
                description = "Crack the secret color code",
                icon = "🔐",
                personality = "detective",
                category = "Logic"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.PING_PONG,
                name = "Ping Pong",
                description = "Real-time rally — she gets angrier as you win",
                icon = "🏓",
                personality = "rage_gamer",
                category = "Reflex"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.REACTION_TIME,
                name = "Reaction Time",
                description = "Tap as fast as you can!",
                icon = "⚡",
                personality = "speedster",
                category = "Reflex"
            ),
            GameOption(
                type = com.cupcake.game.Game.GameType.CLICKER,
                name = "Cupcake Clicker",
                description = "Mindless tapping, sarcastic bot",
                icon = "🧁",
                personality = "sarcastic",
                category = "Idle"
            )
        )
    }
}

data class GameOption(
    val type: com.cupcake.game.Game.GameType,
    val name: String,
    val description: String,
    val icon: String,
    val personality: String,
    val category: String = "General"
)