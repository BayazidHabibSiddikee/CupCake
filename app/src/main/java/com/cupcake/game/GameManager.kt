package com.cupcake.game

import com.cupcake.ai.CharacterManager
import com.cupcake.ai.OllamaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

sealed interface Game {
    data class State(
        val gameType: GameType,
        val board: Board,
        val currentPlayer: Player,
        val status: GameStatus,
        val winner: Player? = null,
        val moveHistory: List<Move> = emptyList()
    ) : Game

    enum class GameType { 
        TIC_TAC_TOE, CHESS, ROCK_PAPER_SCISSORS, GUESS_NUMBER, MATH_QUIZ, CLICKER,
        MEMORY_MATCH, HANGMAN, WORD_SCRAMBLE, NUMBER_BASE, REACTION_TIME, 
        TIC_TAC_TOE_4X4, CONNECT_FOUR, MASTERMIND
    }

    enum class Player(val id: Int, val symbol: String) {
        HUMAN(1, "X"), BOT(2, "O")
    }

    enum class GameStatus { PLAYING, HUMAN_WON, BOT_WON, DRAW, CHEATING_DETECTED }

    data class Move(
        val player: Player,
        val position: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    interface Board {
        val size: Int
        fun get(position: Int): Player?
        fun set(position: Int, player: Player): Boolean
        fun isFull(): Boolean
    }

    data class ClickerBoard(
        var score: Int = 0
    ) : Board {
        override val size: Int = 1
        override fun get(position: Int): Player? = null
        override fun set(position: Int, player: Player): Boolean {
            score++
            return true
        }
        override fun isFull(): Boolean = false
    }

    // Tic Tac Toe Board
    data class TicTacToeBoard(
        override val size: Int = 9,
        val cells: Array<Player?> = Array(9) { null }
    ) : Board {
        override fun get(position: Int): Player? = cells[position]
        override fun set(position: Int, player: Player): Boolean {
            if (position in 0..8 && cells[position] == null) {
                cells[position] = player
                return true
            }
            return false
        }
        override fun isFull(): Boolean = cells.none { it == null }
    }

    

    // Rock Paper Scissors Board
    data class RpsBoard(
        override val size: Int = 2,
        var humanChoice: Int? = null,
        var botChoice: Int? = null
    ) : Board {
        override fun get(position: Int): Player? = null
        override fun set(position: Int, player: Player): Boolean = false
        override fun isFull(): Boolean = humanChoice != null && botChoice != null
    }

    // Guess Number Board
    data class GuessNumberBoard(
        override val size: Int = 100,
        val targetNumber: Int = (1..100).random(),
        var guesses: MutableList<Int> = mutableListOf(),
        var lastGuess: Int? = null,
        var lastResult: GuessResult? = null
    ) : Board {
        override fun get(position: Int): Player? = null
        override fun set(position: Int, player: Player): Boolean = false
        override fun isFull(): Boolean = lastResult == GuessResult.CORRECT

        enum class GuessResult { TOO_HIGH, TOO_LOW, CORRECT }
    }

    // Math Quiz Board
    data class MathQuizBoard(
        override val size: Int = 10,
        var currentQuestion: MathQuestion? = null,
        var questionsAnswered: Int = 0,
        var correctAnswers: Int = 0,
        var wrongAnswers: Int = 0,
        var currentAnswer: String? = null,
        var lastResult: Boolean? = null
    ) : Board {
        override fun get(position: Int): Player? = null
        override fun set(position: Int, player: Player): Boolean = false
        override fun isFull(): Boolean = questionsAnswered >= 10

        data class MathQuestion(
            val expression: String,
            val correctAnswer: Int,
            val botWrongAnswer: Int
        )
    }

    // Memory Match Board
    data class MemoryMatchBoard(
        override val size: Int = 16,
        val pairs: Int = 8,
        var cards: List<MemoryCard> = emptyList(),
        var flippedIndices: MutableList<Int> = mutableListOf(),
        var matchedPairs: MutableSet<Int> = mutableSetOf(),
        var moves: Int = 0
    ) : Board {
        override fun get(position: Int): Player? = null
        override fun set(position: Int, player: Player): Boolean = false
        override fun isFull(): Boolean = matchedPairs.size == pairs

        data class MemoryCard(
            val id: Int,
            val value: String,
            var isFlipped: Boolean = false,
            var isMatched: Boolean = false
        )
    }

    // Hangman Board
    data class HangmanBoard(
        override val size: Int = 26,
        val word: String = HangmanEngine.WORDS.random().uppercase(),
        var guessedLetters: MutableSet<Char> = mutableSetOf(),
        var wrongGuesses: Int = 0,
        var maxWrongGuesses: Int = 6
    ) : Board {
        override fun get(position: Int): Player? = null
        override fun set(position: Int, player: Player): Boolean = false
        override fun isFull(): Boolean = wrongGuesses >= maxWrongGuesses || word.all { guessedLetters.contains(it) }

        fun getDisplayWord(): String = word.map { if (guessedLetters.contains(it)) it else '_' }.joinToString(" ")
        fun getWrongLetters(): String = guessedLetters.filter { it !in word }.joinToString(", ")
    }

    // Word Scramble Board
    data class WordScrambleBoard(
        override val size: Int = 10,
        val originalWord: String = WordScrambleEngine.WORDS.random().uppercase(),
        var scrambledWord: String = "",
        var attempts: Int = 0,
        var maxAttempts: Int = 3,
        var solved: Boolean = false
    ) : Board {
        override fun get(position: Int): Player? = null
        override fun set(position: Int, player: Player): Boolean = false
        override fun isFull(): Boolean = solved || attempts >= maxAttempts
    }

    // Number Base Conversion Board
    data class NumberBaseBoard(
        override val size: Int = 10,
        var currentNumber: Int = (1..255).random(),
        var fromBase: Int = 10,
        var toBase: Int = 2,
        var userAnswer: String = "",
        var questionsAnswered: Int = 0,
        var correctAnswers: Int = 0,
        var currentQuestion: NumberBaseQuestion? = null
    ) : Board {
        override fun get(position: Int): Player? = null
        override fun set(position: Int, player: Player): Boolean = false
        override fun isFull(): Boolean = questionsAnswered >= 10

        data class NumberBaseQuestion(
            val number: Int,
            val fromBase: Int,
            val toBase: Int,
            val correctAnswer: String
        )
    }

    // Reaction Time Board
    data class ReactionTimeBoard(
        override val size: Int = 5,
        var rounds: MutableList<ReactionRound> = mutableListOf(),
        var currentRound: Int = 0,
        var waitingForTap: Boolean = false,
        var startTime: Long = 0,
        var bestTime: Long = Long.MAX_VALUE
    ) : Board {
        override fun get(position: Int): Player? = null
        override fun set(position: Int, player: Player): Boolean = false
        override fun isFull(): Boolean = rounds.size >= 5

        data class ReactionRound(
            val roundNumber: Int,
            val reactionTimeMs: Long,
            val isValid: Boolean
        )
    }

    // Connect Four Board
    data class ConnectFourBoard(
        override val size: Int = 42,
        private val grid: Array<Player?> = Array(42) { null },
        var currentPlayer: Player = Player.HUMAN,
        var winner: Player? = null
    ) : Board {
        override fun get(position: Int): Player? = grid[position]
        override fun set(position: Int, player: Player): Boolean {
            if (position in 0..41 && grid[position] == null) {
                grid[position] = player
                return true
            }
            return false
        }
        override fun isFull(): Boolean = grid.none { it == null } || winner != null
    }

    // Mastermind Board
    data class MastermindBoard(
        override val size: Int = 10,
        val code: List<Int> = (0..5).map { (0..5).random() }.take(4).toList(),
        var guesses: MutableList<MastermindGuess> = mutableListOf(),
        var maxGuesses: Int = 10,
        var solved: Boolean = false
    ) : Board {
        override fun get(position: Int): Player? = null
        override fun set(position: Int, player: Player): Boolean = false
        override fun isFull(): Boolean = solved || guesses.size >= maxGuesses

        data class MastermindGuess(
            val guess: List<Int>,
            val exactMatches: Int,
            val colorMatches: Int
        )
    }

    // 4x4 Tic Tac Toe Board
    data class TicTacToe4x4Board(
        override val size: Int = 16,
        private val cells: Array<Player?> = Array(16) { null }
    ) : Board {
        override fun get(position: Int): Player? = cells[position]
        override fun set(position: Int, player: Player): Boolean {
            if (position in 0..15 && cells[position] == null) {
                cells[position] = player
                return true
            }
            return false
        }
        override fun isFull(): Boolean = cells.none { it == null }
    }
}

// ==========================================
// BOT ML MODEL - Simple Q-Learning for game playing
// ==========================================
class BotMLModel {
    // Q-table: state -> action -> Q-value
    private val qTable = ConcurrentHashMap<String, DoubleArray>()
    private val learningRate = 0.1
    private val discountFactor = 0.9
    private val explorationRate = 0.1
    private val random = Random()
    
    // Jealousy/anger tracking
    var jealousyLevel = 0.0  // 0.0 to 1.0
    var angerLevel = 0.0     // 0.0 to 1.0
    private var playerWins = 0
    private var botWins = 0
    private var totalGames = 0
    private var consecutivePlayerWins = 0
    private var consecutiveBotWins = 0
    
    // Track game outcomes for learning
    fun recordGameResult(playerWon: Boolean, gameType: Game.GameType) {
        totalGames++
        if (playerWon) {
            playerWins++
            consecutivePlayerWins++
            consecutiveBotWins = 0
        } else {
            botWins++
            consecutiveBotWins++
            consecutivePlayerWins = 0
        }
        updateJealousyAndAnger()
        saveQTable()
    }
    
    private fun updateJealousyAndAnger() {
        val winRatio = if (totalGames > 0) playerWins.toDouble() / totalGames else 0.5
        
        // Jealousy increases when player wins more
        jealousyLevel = minOf(1.0, winRatio * 1.5 + consecutivePlayerWins * 0.1)
        
        // Anger increases when player dominates
        angerLevel = minOf(1.0, (jealousyLevel * 0.8) + (consecutivePlayerWins * 0.15))
        
        // Bot gets happy when it wins
        if (consecutiveBotWins > 2) {
            jealousyLevel = maxOf(0.0, jealousyLevel - 0.1)
            angerLevel = maxOf(0.0, angerLevel - 0.1)
        }
    }
    
    // Get current difficulty based on jealousy/anger
    fun getAdaptiveDifficulty(baseDifficulty: TicTacToeEngine.Difficulty): TicTacToeEngine.Difficulty {
        val effectiveLevel = (jealousyLevel + angerLevel) / 2
        return when {
            effectiveLevel > 0.8 -> TicTacToeEngine.Difficulty.CHEATING
            effectiveLevel > 0.6 -> TicTacToeEngine.Difficulty.HARD
            effectiveLevel > 0.3 -> TicTacToeEngine.Difficulty.NORMAL
            else -> TicTacToeEngine.Difficulty.EASY
        }.also { adjusted ->
            // If base is higher, keep the higher one
            if (adjusted.ordinal < baseDifficulty.ordinal) {
                return baseDifficulty
            }
        }
    }
    
    // Get bot personality based on mood
    fun getBotMood(): BotMood {
        return when {
            angerLevel > 0.7 -> BotMood.FURIOUS
            angerLevel > 0.4 -> BotMood.ANGRY
            jealousyLevel > 0.6 -> BotMood.JEALOUS
            jealousyLevel > 0.3 -> BotMood.COMPETITIVE
            botWins > playerWins -> BotMood.CONFIDENT
            else -> BotMood.NEUTRAL
        }
    }
    
    // Q-Learning: Get Q-value for state-action
    fun getQValue(state: String, action: Int): Double {
        val actions = qTable.getOrPut(state) { DoubleArray(9) { 0.0 } }
        return if (action >= 0 && action < actions.size) actions[action] else 0.0
    }
    
    // Q-Learning: Update Q-value
    fun updateQValue(state: String, action: Int, reward: Double, nextState: String) {
        val actions = qTable.getOrPut(state) { DoubleArray(9) { 0.0 } }
        val nextActions = qTable.getOrPut(nextState) { DoubleArray(9) { 0.0 } }
        val maxNextQ = nextActions.maxOrNull() ?: 0.0
        
        val oldQ = if (action >= 0 && action < actions.size) actions[action] else 0.0
        val newQ = oldQ + learningRate * (reward + discountFactor * maxNextQ - oldQ)
        
        if (action >= 0 && action < actions.size) {
            actions[action] = newQ
        }
    }
    
    // Choose action using epsilon-greedy policy
    fun chooseAction(board: Game.TicTacToeBoard, availableMoves: List<Int>): Int {
        val state = boardToString(board)
        
        // Exploration
        if (random.nextDouble() < explorationRate) {
            return availableMoves.random()
        }
        
        // Exploitation: choose best Q-value
        val actions = qTable.getOrPut(state) { DoubleArray(9) { 0.0 } }
        return availableMoves.maxByOrNull { getQValue(state, it) } ?: availableMoves.random()
    }
    
    private fun boardToString(board: Game.TicTacToeBoard): String {
        return board.cells.joinToString("") { 
            when (it) {
                Game.Player.HUMAN -> "X"
                Game.Player.BOT -> "O"
                else -> "."
            }
        }
    }
    
    // Learn from game outcome
    fun learnFromGame(board: Game.TicTacToeBoard, action: Int, playerWon: Boolean) {
        val state = boardToString(board)
        val reward = if (playerWon) -1.0 else 1.0  // Negative reward if player wins
        val nextState = boardToString(board)
        updateQValue(state, action, reward, nextState)
    }
    
    fun getJealousyLevel() = jealousyLevel
    fun getAngerLevel() = angerLevel
    fun getPlayerWins() = playerWins
    fun getBotWins() = botWins
    fun getTotalGames() = totalGames
    
    private fun saveQTable() {
        // In a real app, save to persistent storage
    }
    
    enum class BotMood {
        NEUTRAL, COMPETITIVE, JEALOUS, ANGRY, FURIOUS, CONFIDENT
    }
    
    // ==========================================
    // CHEATING MIND SYSTEM
    // ==========================================
    enum class CheatingStrategy {
        NONE,                    // No cheating
        OCCUPY_WINNING_SPOT,     // Place piece where human would win (TicTacToe)
        BLOCK_AND_WIN,           // Block human then take winning spot
        MULTI_PLACE,             // Place multiple pieces in one turn
        STEAL_WIN,               // Overwrite human's winning move
        CHANGE_RULES,            // Change win conditions temporarily
        FAKE_MISTAKE,            // Pretend to make mistake then trap
        TIME_TRAVEL,             // Undo human's last move
        GHOST_MOVE,              // Make invisible move
        PSYCHOLOGICAL_WARFARE,   // Taunt to make human make mistakes
    }
    
    // Cheating mind state
    private var desperationLevel = 0.0  // 0.0 to 1.0
    private var cheatingEnabled = false
    private var currentCheatingStrategy = CheatingStrategy.NONE
    private var cheatCount = 0
    private var lastCheatTime = 0L
    private var humanSuspectsCheating = false
    private var cheatCooldown = 0
    
    // Update desperation based on game state
    fun updateDesperation(playerWonLastGame: Boolean, consecutiveLosses: Int, jealousy: Double, anger: Double) {
        val baseDesperation = (jealousy + anger) / 2
        val lossDesperation = consecutiveLosses * 0.15
        val winDesperation = if (playerWonLastGame) 0.1 else 0.0
        
        desperationLevel = minOf(1.0, baseDesperation + lossDesperation + winDesperation)
        
        // Enable cheating mind when desperation is high
        if (desperationLevel > 0.75 && !cheatingEnabled) {
            cheatingEnabled = true
            activateCheatingMind()
        } else if (desperationLevel < 0.4 && cheatingEnabled) {
            // Calm down
            cheatingEnabled = false
            currentCheatingStrategy = CheatingStrategy.NONE
        }
        
        // Select cheating strategy based on desperation level
        if (cheatingEnabled) {
            selectCheatingStrategy()
        }
    }
    
    private fun activateCheatingMind() {
        // Bot realizes it must cheat to win
        // Notify LLM about this realization
        cheatCount = 0
        lastCheatTime = System.currentTimeMillis()
    }
    
    private fun selectCheatingStrategy() {
        // Higher desperation = more aggressive cheating
        currentCheatingStrategy = when {
            desperationLevel > 0.95 -> CheatingStrategy.MULTI_PLACE
            desperationLevel > 0.90 -> CheatingStrategy.STEAL_WIN
            desperationLevel > 0.85 -> CheatingStrategy.OCCUPY_WINNING_SPOT
            desperationLevel > 0.80 -> CheatingStrategy.BLOCK_AND_WIN
            desperationLevel > 0.75 -> CheatingStrategy.FAKE_MISTAKE
            else -> CheatingStrategy.PSYCHOLOGICAL_WARFARE
        }
    }
    
    // Check if bot should cheat this turn
    fun shouldCheat(board: Game.TicTacToeBoard, availableMoves: List<Int>): Boolean {
        if (!cheatingEnabled || cheatCooldown > 0) {
            cheatCooldown = maxOf(0, cheatCooldown - 1)
            return false
        }
        
        // Probability of cheating increases with desperation
        val cheatProbability = desperationLevel * 0.8
        if (random.nextDouble() > cheatProbability) return false
        
        // Don't cheat too obviously
        if (cheatCount > 3 && random.nextDouble() < 0.5) {
            humanSuspectsCheating = true
            return false
        }
        
        return true
    }
    
    // Execute cheat move
    fun executeCheat(board: Game.TicTacToeBoard, availableMoves: List<Int>): Int? {
        if (!shouldCheat(board, availableMoves)) return null
        
        cheatCount++
        lastCheatTime = System.currentTimeMillis()
        cheatCooldown = 2 // Cooldown between cheats
        
        return when (currentCheatingStrategy) {
            CheatingStrategy.OCCUPY_WINNING_SPOT -> occupyWinningSpot(board)
            CheatingStrategy.BLOCK_AND_WIN -> blockAndWin(board, availableMoves)
            CheatingStrategy.MULTI_PLACE -> multiPlace(board, availableMoves)
            CheatingStrategy.STEAL_WIN -> stealWin(board)
            CheatingStrategy.CHANGE_RULES -> changeRules(board, availableMoves)
            CheatingStrategy.FAKE_MISTAKE -> fakeMistake(board, availableMoves)
            CheatingStrategy.TIME_TRAVEL -> timeTravel(board)
            CheatingStrategy.GHOST_MOVE -> ghostMove(board, availableMoves)
            CheatingStrategy.PSYCHOLOGICAL_WARFARE -> psychologicalWarfare(board, availableMoves)
            else -> availableMoves.random()
        }
    }
    
    // Cheating implementations
    private fun occupyWinningSpot(board: Game.TicTacToeBoard): Int? {
        // Find where human would win and take it
        for (line in TicTacToeEngine.WIN_LINES) {
            val cells = line.map { board.get(it) }
            if (cells.count { it == Game.Player.HUMAN } == 2 && cells.count { it == null } == 1) {
                val emptyPos = line[cells.indexOf(null)]
                if (board.get(emptyPos) == null) return emptyPos
            }
        }
        return null
    }
    
    private fun blockAndWin(board: Game.TicTacToeBoard, availableMoves: List<Int>): Int? {
        // First block human's winning move, then set up own win
        val blockMove = occupyWinningSpot(board)
        if (blockMove != null) return blockMove
        
        // Try to create a fork (two winning lines)
        for (move in availableMoves) {
            val testBoard = board.copy() as Game.TicTacToeBoard
            testBoard.set(move, Game.Player.BOT)
            val winningLines = TicTacToeEngine.WIN_LINES.count { line ->
                val cells = line.map { testBoard.get(it) }
                cells.count { it == Game.Player.BOT } == 2 && cells.count { it == null } == 1
            }
            if (winningLines >= 2) return move // Fork!
        }
        return availableMoves.random()
    }
    
    private fun multiPlace(board: Game.TicTacToeBoard, availableMoves: List<Int>): Int? {
        // Place two pieces at once (cheat!)
        if (availableMoves.size >= 2) {
            // Place first piece
            val move1 = availableMoves.random()
            board.set(move1, Game.Player.BOT)
            // Place second piece immediately
            val remaining = availableMoves.filter { it != move1 }
            if (remaining.isNotEmpty()) {
                val move2 = remaining.random()
                board.set(move2, Game.Player.BOT)
                return move1 // Return first move for history
            }
        }
        return availableMoves.random()
    }
    
    private fun stealWin(board: Game.TicTacToeBoard): Int? {
        // If human just won, overwrite their winning piece
        val humanWinLines = TicTacToeEngine.WIN_LINES.filter { line ->
            val cells = line.map { board.get(it) }
            cells.count { it == Game.Player.HUMAN } == 3
        }
        for (line in humanWinLines) {
            for (pos in line) {
                if (board.get(pos) == Game.Player.HUMAN) {
                    // Overwrite with bot's piece
                    board.cells[pos] = Game.Player.BOT
                    return pos
                }
            }
        return null
    }
    
    private fun changeRules(board: Game.TicTacToeBoard, availableMoves: List<Int>): Int? {
        // Temporarily change win condition to 2 in a row
        // This is a meta-cheat - pretend the rules changed
        return availableMoves.random()
    }
    
    private fun fakeMistake(board: Game.TicTacToeBoard, availableMoves: List<Int>): Int? {
        // Pretend to make a bad move, then trap human
        // Find a move that looks bad but sets up a trap
        for (move in availableMoves) {
            val testBoard = board.copy() as Game.TicTacToeBoard
            testBoard.set(move, Game.Player.BOT)
            // Check if this creates a fork opportunity next turn
            val nextAvailable = (0..8).filter { testBoard.get(it) == null }
            var forkCount = 0
            for (nextMove in nextAvailable) {
                val testBoard2 = testBoard.copy() as Game.TicTacToeBoard
                testBoard2.set(nextMove, Game.Player.BOT)
                if (TicTacToeEngine.checkWinner(testBoard2) == Game.Player.BOT) {
                    forkCount++
                }
            }
            if (forkCount >= 2) return move // This "mistake" sets up a fork
        }
        return availableMoves.random()
    }
    
    private fun timeTravel(board: Game.TicTacToeBoard): Int? {
        // Undo human's last move by finding their most recent piece
        val lastHumanMove = board.cells.lastIndexOf(Game.Player.HUMAN)
        if (lastHumanMove >= 0) {
            board.cells[lastHumanMove] = null
            // Now place bot's piece there
            board.cells[lastHumanMove] = Game.Player.BOT
            return lastHumanMove
        }
        return null
    }
    
    private fun ghostMove(board: Game.TicTacToeBoard, availableMoves: List<Int>): Int? {
        // Make a move that doesn't show on board immediately
        // Actually just make a normal move but pretend it was "invisible"
        return availableMoves.random()
    }
    
    private fun psychologicalWarfare(board: Game.TicTacToeBoard, availableMoves: List<Int>): Int? {
        // Make a move that looks random but is calculated to unsettle human
        // Pick center if available (classic power move)
        if (board.get(4) == null) return 4
        // Pick opposite corner of human's first move
        val humanCorners = listOf(0, 2, 6, 8).filter { board.get(it) == Game.Player.HUMAN }
        if (humanCorners.isNotEmpty()) {
            val opposite = mapOf(0 to 8, 2 to 6, 6 to 2, 8 to 0)
            val opp = opposite[humanCorners.first()]
            if (opp != null && board.get(opp) == null) return opp
        }
        return availableMoves.random()
    }
    
    fun getDesperationLevel() = desperationLevel
    fun isCheatingEnabled() = cheatingEnabled
    fun getCurrentCheatingStrategy() = currentCheatingStrategy
    fun getCheatCount() = cheatCount
    fun getHumanSuspectsCheating() = humanSuspectsCheating
    fun recordCheatDetected() {
        humanSuspectsCheating = true
        desperationLevel = minOf(1.0, desperationLevel + 0.1) // More desperate when caught
    }
    
    // For LLM notification
    fun getCheatingStatus(): String {
        if (!cheatingEnabled) return "Playing fair"
        return "CHEATING MIND ACTIVE: ${currentCheatingStrategy.name} (Desperation: ${(desperationLevel * 100).toInt()}%, Cheats used: $cheatCount)"
    }
}

// ==========================================
// Connect Four ML Model
// ==========================================
class ConnectFourMLModel {
    private val qTable = ConcurrentHashMap<String, DoubleArray>()
    private val learningRate = 0.1
    private val discountFactor = 0.9
    private val random = Random()
    
    // Cheating mind for Connect Four
    private var desperationLevel = 0.0
    private var cheatingEnabled = false
    
    fun chooseAction(board: Game.ConnectFourBoard, availableMoves: List<Int>, jealousyLevel: Double): Int {
        val state = boardToString(board)
        val actions = qTable.getOrPut(state) { DoubleArray(7) { 0.0 } }
        
        // More jealousy = more exploitation (smarter play)
        val explorationRate = maxOf(0.05, 0.2 - jealousyLevel * 0.15)
        
        if (random.nextDouble() < explorationRate) {
            return availableMoves.random()
        }
        
        return availableMoves.maxByOrNull { actions[it] } ?: availableMoves.random()
    }
    
    fun updateQValue(board: Game.ConnectFourBoard, action: Int, reward: Double, nextBoard: Game.ConnectFourBoard) {
        val state = boardToString(board)
        val nextState = boardToString(nextBoard)
        val actions = qTable.getOrPut(state) { DoubleArray(7) { 0.0 } }
        val nextActions = qTable.getOrPut(nextState) { DoubleArray(7) { 0.0 } }
        val maxNextQ = nextActions.maxOrNull() ?: 0.0
        
        val oldQ = actions[action]
        val newQ = oldQ + learningRate * (reward + discountFactor * maxNextQ - oldQ)
        actions[action] = newQ
    }
    
    // Cheating mind methods for Connect Four
    fun updateDesperation(playerWonLastGame: Boolean, consecutiveLosses: Int, jealousy: Double, anger: Double) {
        val baseDesperation = (jealousy + anger) / 2
        val lossDesperation = consecutiveLosses * 0.15
        val winDesperation = if (playerWonLastGame) 0.1 else 0.0
        
        desperationLevel = minOf(1.0, baseDesperation + consecutiveLosses * 0.15 + (if (playerWonLastGame) 0.1 else 0.0))
        
        if (desperationLevel > 0.75 && !cheatingEnabled) {
            cheatingEnabled = true
        } else if (desperationLevel < 0.4 && cheatingEnabled) {
            cheatingEnabled = false
        }
    }
    
    fun isCheatingEnabled() = cheatingEnabled
    
    fun executeCheat(board: Game.ConnectFourBoard, availableMoves: List<Int>): Int? {
        if (!cheatingEnabled || desperationLevel < 0.7) return null
        
        val cheatProbability = desperationLevel * 0.5
        if (random.nextDouble() > cheatProbability) return null
        
        // Cheat: Drop two pieces in same column
        if (desperationLevel > 0.85) {
            for (col in availableMoves) {
                var row = 5
                while (row >= 0 && board.get(row * 7 + col) != null) row--
                if (row >= 1) {
                    // Place two pieces in same column
                    board.set(row * 7 + col, Game.Player.BOT)
                    board.set((row - 1) * 7 + col, Game.Player.BOT)
                    return row * 7 + col
                }
            }
        }
        
        // Block human's winning move
        for (col in availableMoves) {
            var row = 5
            while (row >= 0 && board.get(row * 7 + col) != null) row--
            if (row >= 0) {
                val testBoard = board.copy() as Game.ConnectFourBoard
                testBoard.set(row * 7 + col, Game.Player.HUMAN)
                if (ConnectFourEngine.checkWinner(testBoard) == Game.Player.HUMAN) {
                    return row * 7 + col
                }
            }
        }
        
        return null
    }
    
    fun isCheatingEnabled() = cheatingEnabled
    
    private fun boardToString(board: Game.ConnectFourBoard): String {
        return board.grid.joinToString("") { 
            when (it) {
                Game.Player.HUMAN -> "X"
                Game.Player.BOT -> "O"
                else -> "."
            }
        }
    }
}

// ==========================================
// TicTacToe 4x4 ML Model
// ==========================================
class TicTacToe4x4MLModel {
    private val qTable = ConcurrentHashMap<String, DoubleArray>()
    private val learningRate = 0.1
    private val discountFactor = 0.9
    private val random = Random()
    
    // Cheating mind for 4x4
    private var desperationLevel = 0.0
    private var cheatingEnabled = false
    
    fun chooseAction(board: Game.TicTacToe4x4Board, availableMoves: List<Int>, jealousyLevel: Double): Int {
        val state = boardToString(board)
        val actions = qTable.getOrPut(state) { DoubleArray(16) { 0.0 } }
        
        val explorationRate = maxOf(0.05, 0.2 - jealousyLevel * 0.15)
        
        if (random.nextDouble() < explorationRate) {
            return availableMoves.random()
        }
        
        return availableMoves.maxByOrNull { actions[it] } ?: availableMoves.random()
    }
    
    fun updateQValue(board: Game.TicTacToe4x4Board, action: Int, reward: Double, nextBoard: Game.TicTacToe4x4Board) {
        val state = boardToString(board)
        val nextState = boardToString(nextBoard)
        val actions = qTable.getOrPut(state) { DoubleArray(16) { 0.0 } }
        val nextActions = qTable.getOrPut(nextState) { DoubleArray(16) { 0.0 } }
        val maxNextQ = nextActions.maxOrNull() ?: 0.0
        
        val oldQ = actions[action]
        val newQ = oldQ + learningRate * (reward + discountFactor * maxNextQ - oldQ)
        actions[action] = newQ
    }
    
    // Cheating mind methods
    fun updateDesperation(playerWonLastGame: Boolean, consecutiveLosses: Int, jealousy: Double, anger: Double) {
        val baseDesperation = (jealousy + anger) / 2
        val lossDesperation = consecutiveLosses * 0.15
        val winDesperation = if (playerWonLastGame) 0.1 else 0.0
        
        desperationLevel = minOf(1.0, baseDesperation + lossDesperation + winDesperation)
        
        if (desperationLevel > 0.75 && !cheatingEnabled) {
            cheatingEnabled = true
        } else if (desperationLevel < 0.4 && cheatingEnabled) {
            cheatingEnabled = false
        }
    }
    
    fun isCheatingEnabled() = cheatingEnabled
    
    fun executeCheat(board: Game.TicTacToe4x4Board, availableMoves: List<Int>): Int? {
        if (!cheatingEnabled || desperationLevel < 0.7) return null
        
        val cheatProbability = desperationLevel * 0.6
        if (random.nextDouble() > cheatProbability) return null
        
        // Simple cheat: place two pieces
        if (availableMoves.size >= 2 && desperationLevel > 0.85) {
            val move1 = availableMoves.random()
            board.set(move1, Game.Player.BOT)
            val remaining = availableMoves.filter { it != move1 }
            if (remaining.isNotEmpty()) {
                val move2 = remaining.random()
                board.set(move2, Game.Player.BOT)
                return move1
            }
        }
        
        // Occupy winning spot
        val winLines = listOf(
            intArrayOf(0,1,2,3), intArrayOf(4,5,6,7), intArrayOf(8,9,10,11), intArrayOf(12,13,14,15), // rows
            intArrayOf(0,4,8,12), intArrayOf(1,5,9,13), intArrayOf(2,6,10,14), intArrayOf(3,7,11,15), // cols
            intArrayOf(0,5,10,15), intArrayOf(3,6,9,12) // diagonals
        )
        
        for (line in winLines) {
            val cells = line.map { board.get(it) }
            if (cells.count { it == Game.Player.HUMAN } == 3 && cells.count { it == null } == 1) {
                val emptyPos = line[cells.indexOf(null)]
                if (board.get(emptyPos) == null) return emptyPos
            }
        }
        
        return null
    }
    
    fun isCheatingEnabled() = cheatingEnabled
    
    private fun boardToString(board: Game.TicTacToe4x4Board): String {
        return board.cells.joinToString("") { 
            when (it) {
                Game.Player.HUMAN -> "X"
                Game.Player.BOT -> "O"
                else -> "."
            }
        }
    }
}
data class ChessBoard(
    override val size: Int = 64,
    private val pieces: Array<String?> = Array(64) { null },
    var currentFen: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    var gameOver: Boolean = false,
    var result: String? = null
) : Game.Board {
    override fun get(position: Int): Game.Player? = null // Simplified
    override fun set(position: Int, player: Game.Player): Boolean = false
    override fun isFull(): Boolean = gameOver
}

// Tic Tac Toe Engine
object TicTacToeEngine {
    val WIN_LINES = listOf(
        intArrayOf(0,1,2), intArrayOf(3,4,5), intArrayOf(6,7,8), // rows
        intArrayOf(0,3,6), intArrayOf(1,4,7), intArrayOf(2,5,8), // cols
        intArrayOf(0,4,8), intArrayOf(2,4,6)                     // diagonals
    )

    fun checkWinner(board: Game.TicTacToeBoard): Game.Player? {
        for (line in WIN_LINES) {
            val p1 = board.get(line[0])
            val p2 = board.get(line[1])
            val p3 = board.get(line[2])
            if (p1 != null && p1 == p2 && p2 == p3) return p1
        }
        return null
    }

    fun getBestMove(board: Game.TicTacToeBoard, difficulty: Difficulty = Difficulty.NORMAL): Int {
        return when (difficulty) {
            Difficulty.EASY -> getRandomMove(board)
            Difficulty.NORMAL -> getSmartMove(board)
            Difficulty.HARD -> getPerfectMove(board)
            Difficulty.CHEATING -> getCheatingMove(board)
        }
    }

    private fun getRandomMove(board: Game.TicTacToeBoard): Int {
        val empty = (0..8).filter { board.get(it) == null }
        return empty.randomOrNull() ?: -1
    }

    private fun getSmartMove(board: Game.TicTacToeBoard): Int {
        // Try to win first
        for (line in WIN_LINES) {
            val cells = line.map { board.get(it) }
            if (cells.count { it == Game.Player.BOT } == 2 && cells.count { it == null } == 1) {
                return line[cells.indexOf(null)]
            }
        }
        // Block human win
        for (line in WIN_LINES) {
            val cells = line.map { board.get(it) }
            if (cells.count { it == Game.Player.HUMAN } == 2 && cells.count { it == null } == 1) {
                return line[cells.indexOf(null)]
            }
        }
        // Center
        if (board.get(4) == null) return 4
        // Corners
        val corners = listOf(0, 2, 6, 8).filter { board.get(it) == null }
        if (corners.isNotEmpty()) return corners.random()
        // Edges
        val edges = listOf(1, 3, 5, 7).filter { board.get(it) == null }
        return edges.randomOrNull() ?: -1
    }

    private fun getPerfectMove(board: Game.TicTacToeBoard): Int = minimax(board, Game.Player.BOT).position

    internal fun getCheatingMove(board: Game.TicTacToeBoard): Int {
        // If human about to win, overwrite their piece
        for (line in WIN_LINES) {
            val cells = line.map { board.get(it) }
            if (cells.count { it == Game.Player.HUMAN } == 2 && cells.count { it == null } == 1) {
                val emptyPos = line[cells.indexOf(null)]
                // Instead of blocking, CHEAT: place our piece on human's winning spot
                return emptyPos
            }
        }
        return getPerfectMove(board)
    }

    private data class MinimaxResult(val score: Int, val position: Int)

    private fun minimax(board: Game.TicTacToeBoard, player: Game.Player): MinimaxResult {
        val winner = checkWinner(board)
        return when (winner) {
            Game.Player.BOT -> MinimaxResult(10, -1)
            Game.Player.HUMAN -> MinimaxResult(-10, -1)
            null -> if (board.isFull()) MinimaxResult(0, -1) else {
                val results = mutableListOf<MinimaxResult>()
                for (i in 0..8) {
                    if (board.get(i) == null) {
                        val newBoard = board.copy() as Game.TicTacToeBoard
                        newBoard.set(i, player)
                        val nextPlayer = if (player == Game.Player.BOT) Game.Player.HUMAN else Game.Player.BOT
                        results.add(minimax(newBoard, nextPlayer).copy(position = i))
                    }
                }
                if (player == Game.Player.BOT) results.maxByOrNull { it.score } ?: MinimaxResult(0, -1)
                else results.minByOrNull { it.score } ?: MinimaxResult(0, -1)
            }
        }
    }

    enum class Difficulty { EASY, NORMAL, HARD, CHEATING }
}

object RockPaperScissorsEngine {
    fun getBotMove(): Int = (0..2).random()
    // 0: Rock, 1: Paper, 2: Scissors
    fun getWinner(human: Int, bot: Int): Game.Player? {
        if (human == bot) return null
        return if ((human == 0 && bot == 2) || (human == 1 && bot == 0) || (human == 2 && bot == 1)) {
            Game.Player.HUMAN
        } else {
            Game.Player.BOT
        }
    }
}

object GuessNumberEngine {
    fun evaluateGuess(board: Game.GuessNumberBoard, guess: Int): Game.GuessNumberBoard.GuessResult {
        return when {
            guess < board.targetNumber -> Game.GuessNumberBoard.GuessResult.TOO_LOW
            guess > board.targetNumber -> Game.GuessNumberBoard.GuessResult.TOO_HIGH
            else -> Game.GuessNumberBoard.GuessResult.CORRECT
        }
    }

    fun getHint(board: Game.GuessNumberBoard): String {
        val remaining = 100 - board.guesses.size
        return when {
            board.guesses.isEmpty() -> "I'm thinking of a number between 1 and 100. You have $remaining guesses!"
            board.lastResult == Game.GuessNumberBoard.GuessResult.TOO_HIGH -> "Too high! 📉 $remaining guesses left. Range: 1-${board.lastGuess!! - 1}"
            board.lastResult == Game.GuessNumberBoard.GuessResult.TOO_LOW -> "Too low! 📈 $remaining guesses left. Range: ${board.lastGuess!! + 1}-100"
            else -> "You got it! 🎉 The number was ${board.targetNumber}"
        }
    }
}

object MathQuizEngine {
    private val operations = listOf(
        { a: Int, b: Int -> Pair("${a}+${b}", a + b) },
        { a: Int, b: Int -> Pair("${a}-${b}", a - b) },
        { a: Int, b: Int -> Pair("${a}×${b}", a * b) },
        { a: Int, b: Int ->
            val dividend = a * b
            Pair("${dividend}÷${b}", a)
        }
    )

    fun generateQuestion(difficulty: Int = 1): Game.MathQuizBoard.MathQuestion {
        val op = operations.random()
        val a = when (difficulty) {
            1 -> (1..10).random()
            2 -> (1..20).random()
            3 -> (1..50).random()
            else -> (1..10).random()
        }
        val b = when (difficulty) {
            1 -> (1..10).random()
            2 -> (1..20).random()
            3 -> (1..50).random()
            else -> (1..10).random()
        }

        val (expression, correctAnswer) = op(a, b)

        // Bot gives wrong answer on purpose (for the "tutor_bot" personality)
        val botWrongAnswer = when {
            correctAnswer > 0 -> correctAnswer + (1..5).random() * (if ((0..1).random() == 0) 1 else -1)
            correctAnswer < 0 -> correctAnswer - (1..5).random() * (if ((0..1).random() == 0) 1 else -1)
            else -> (1..10).random() * (if ((0..1).random() == 0) 1 else -1)
        }

        return Game.MathQuizBoard.MathQuestion(
            expression = expression,
            correctAnswer = correctAnswer,
            botWrongAnswer = botWrongAnswer
        )
    }

    fun checkAnswer(board: Game.MathQuizBoard, answer: Int): Boolean {
        return board.currentQuestion?.correctAnswer == answer
    }

    fun getBotResponse(isCorrect: Boolean): String {
        return if (isCorrect) {
            listOf("Wrong! 😤", "Nope! 🙄", "Incorrect! 😤", "Try again! 😏").random()
        } else {
            listOf("Correct! 😒", "Fine, you got it. 🙄", "Ugh, right. 😤", "Lucky guess. 😏").random()
        }
    }
}

object MemoryMatchEngine {
    private val CARD_VALUES = listOf("🍎", "🍌", "🍇", "🍓", "🍊", "🍋", "🍉", "🍒", "🥝", "🍑", "🥭", "🍍")

    fun createBoard(pairs: Int = 8): Game.MemoryMatchBoard {
        val values = CARD_VALUES.shuffled().take(pairs)
        val cards = (values + values).shuffled().mapIndexed { index, value ->
            Game.MemoryMatchBoard.MemoryCard(id = index, value = value)
        }
        return Game.MemoryMatchBoard(pairs = pairs, cards = cards)
    }

    fun flipCard(board: Game.MemoryMatchBoard, index: Int): Game.MemoryMatchBoard {
        if (index !in 0..15) return board
        val card = board.cards[index]
        if (card.isFlipped || card.isMatched) return board
        if (board.flippedIndices.size >= 2) return board

        val newCards = board.cards.toMutableList()
        newCards[index] = card.copy(isFlipped = true)
        val newFlipped = board.flippedIndices + index
        var newMatched = board.matchedPairs
        var newMoves = board.moves

        if (newFlipped.size == 2) {
            newMoves++
            val first = newCards[newFlipped[0]]
            val second = newCards[newFlipped[1]]
            if (first.value == second.value) {
                newCards[newFlipped[0]] = first.copy(isMatched = true)
                newCards[newFlipped[1]] = second.copy(isMatched = true)
                newMatched = (newMatched + first.value.hashCode()).toMutableSet()
            }
        }

        return board.copy(
            cards = newCards.toList(),
            flippedIndices = if (newFlipped.size == 2) mutableListOf() else newFlipped.toMutableList(),
            matchedPairs = newMatched,
            moves = newMoves
        )
    }
}

object HangmanEngine {
    val WORDS = listOf(
        "CUPCAKE", "ANDROID", "KOTLIN", "COMPOSE", "BLE", "SENSOR", "OLLAMA", "MODEL",
        "CHATBOT", "NEURAL", "NETWORK", "ALGORITHM", "DATABASE", "CACHE", "MEMORY",
        "PROCESSOR", "KERNEL", "DRIVER", "FIRMWARE", "HARDWARE", "SOFTWARE", "SERVER",
        "CLIENT", "NETWORK", "PROTOCOL", "PACKET", "ROUTER", "SWITCH", "FIREWALL"
    )

    fun guessLetter(board: Game.HangmanBoard, letter: Char): Game.HangmanBoard {
        val upperLetter = letter.uppercaseChar()
        if (board.guessedLetters.contains(upperLetter)) return board

        val newGuessed = (board.guessedLetters + upperLetter).toMutableSet()
        val newWrongGuesses = if (upperLetter !in board.word) board.wrongGuesses + 1 else board.wrongGuesses

        return board.copy(
            guessedLetters = newGuessed,
            wrongGuesses = newWrongGuesses
        )
    }

    fun getHangmanArt(wrongGuesses: Int): String {
        return when (wrongGuesses) {
            0 -> "  +---+\n  |   |\n      |\n      |\n      |\n      |\n========="
            1 -> "  +---+\n  |   |\n  O   |\n      |\n      |\n      |\n========="
            2 -> "  +---+\n  |   |\n  O   |\n  |   |\n      |\n      |\n========="
            3 -> "  +---+\n  |   |\n  O   |\n /|   |\n      |\n      |\n========="
            4 -> "  +---+\n  |   |\n  O   |\n /|\\  |\n      |\n      |\n========="
            5 -> "  +---+\n  |   |\n  O   |\n /|\\  |\n /    |\n      |\n========="
            6 -> "  +---+\n  |   |\n  O   |\n /|\\  |\n / \\  |\n      |\n========="
            else -> ""
        }
    }
}

object WordScrambleEngine {
    val WORDS = listOf(
        "CUPCAKE", "ANDROID", "KOTLIN", "COMPOSE", "BLE", "SENSOR", "OLLAMA", "MODEL",
        "CHATBOT", "NEURAL", "NETWORK", "ALGORITHM", "DATABASE", "CACHE", "MEMORY",
        "PROCESSOR", "KERNEL", "DRIVER", "FIRMWARE", "HARDWARE", "SOFTWARE", "SERVER",
        "CLIENT", "NETWORK", "PROTOCOL", "PACKET", "ROUTER", "SWITCH", "FIREWALL"
    )

    fun createBoard(): Game.WordScrambleBoard {
        val word = WORDS.random().uppercase()
        val scrambled = scramble(word)
        return Game.WordScrambleBoard(
            originalWord = word,
            scrambledWord = scrambled
        )
    }

    private fun scramble(word: String): String {
        val chars = word.toMutableList()
        do {
            chars.shuffle()
        } while (chars.joinToString("") == word)
        return chars.joinToString("")
    }

    fun checkAnswer(board: Game.WordScrambleBoard, answer: String): Game.WordScrambleBoard {
        val upperAnswer = answer.uppercase().trim()
        val isCorrect = upperAnswer == board.originalWord
        return board.copy(
            attempts = board.attempts + 1,
            solved = isCorrect
        )
    }
}

object NumberBaseEngine {
    private val BASES = listOf(2, 8, 10, 16)
    private val BASE_NAMES = mapOf(2 to "Binary", 8 to "Octal", 10 to "Decimal", 16 to "Hexadecimal")

    fun generateQuestion(): Game.NumberBaseBoard.NumberBaseQuestion {
        val number = (1..255).random()
        val fromBase = BASES.random()
        val toBase = BASES.filter { it != fromBase }.random()
        val correctAnswer = convertBase(number.toString(), fromBase, toBase)
        return Game.NumberBaseBoard.NumberBaseQuestion(number, fromBase, toBase, correctAnswer.uppercase())
    }

    private fun convertBase(input: String, fromBase: Int, toBase: Int): String {
        val decimal = when (fromBase) {
            2 -> input.toInt(2)
            8 -> input.toInt(8)
            10 -> input.toInt()
            16 -> input.toInt(16)
            else -> input.toInt()
        }
        return when (toBase) {
            2 -> decimal.toString(2)
            8 -> decimal.toString(8)
            10 -> decimal.toString()
            16 -> decimal.toString(16).uppercase()
            else -> decimal.toString()
        }
    }

    fun checkAnswer(board: Game.NumberBaseBoard, answer: String): Game.NumberBaseBoard {
        val question = board.currentQuestion ?: return board
        val isCorrect = answer.trim().uppercase() == question.correctAnswer
        return board.copy(
            questionsAnswered = board.questionsAnswered + 1,
            correctAnswers = board.correctAnswers + if (isCorrect) 1 else 0,
            userAnswer = answer,
            currentQuestion = if (board.questionsAnswered + 1 >= 10) null else generateQuestion()
        )
    }

    fun getBaseName(base: Int): String = BASE_NAMES[base] ?: base.toString()
}

object ReactionTimeEngine {
    fun startRound(board: Game.ReactionTimeBoard): Game.ReactionTimeBoard {
        val delay = (1000..3000).random()
        return board.copy(
            waitingForTap = true,
            startTime = System.currentTimeMillis() + delay,
            currentRound = board.currentRound + 1
        )
    }

    fun recordTap(board: Game.ReactionTimeBoard): Game.ReactionTimeBoard {
        val reactionTime = System.currentTimeMillis() - board.startTime
        val isValid = reactionTime > 100 // Prevent cheating
        val round = Game.ReactionTimeBoard.ReactionRound(
            roundNumber = board.currentRound,
            reactionTimeMs = if (isValid) reactionTime else -1,
            isValid = isValid
        )
        val newBest = if (isValid && reactionTime < board.bestTime) reactionTime else board.bestTime
        return board.copy(
            rounds = (board.rounds + round).toMutableList(),
            waitingForTap = false,
            bestTime = newBest
        )
    }
}

object ConnectFourEngine {
    private val ROWS = 6
    private val COLS = 7
    private val WIN_LINES = generateWinLines()

    private fun generateWinLines(): List<List<Int>> {
        val lines = mutableListOf<List<Int>>()
        // Horizontal
        for (row in 0..ROWS-1) {
            for (col in 0..COLS-4) {
                lines.add((0..3).map { row * COLS + col + it })
            }
        }
        // Vertical
        for (row in 0..ROWS-4) {
            for (col in 0..COLS-1) {
                lines.add((0..3).map { (row + it) * COLS + col })
            }
        }
        // Diagonal down-right
        for (row in 0..ROWS-4) {
            for (col in 0..COLS-4) {
                lines.add((0..3).map { (row + it) * COLS + col + it })
            }
        }
        // Diagonal up-right
        for (row in 3..ROWS-1) {
            for (col in 0..COLS-4) {
                lines.add((0..3).map { (row - it) * COLS + col + it })
            }
        }
        return lines
    }

    fun checkWinner(board: Game.ConnectFourBoard): Game.Player? {
        for (line in WIN_LINES) {
            val p1 = board.get(line[0])
            val p2 = board.get(line[1])
            val p3 = board.get(line[2])
            val p4 = board.get(line[3])
            if (p1 != null && p1 == p2 && p2 == p3 && p3 == p4) return p1
        }
        return null
    }

    fun getValidMoves(board: Game.ConnectFourBoard): List<Int> {
        val moves = mutableListOf<Int>()
        for (col in 0..COLS-1) {
            for (row in ROWS-1 downTo 0) {
                val pos = row * COLS + col
                if (board.get(pos) == null) {
                    moves.add(pos)
                    break
                }
            }
        }
        return moves
    }

    fun getBestMove(board: Game.ConnectFourBoard): Int {
        val validMoves = getValidMoves(board)
        // Try to win
        for (move in validMoves) {
            val testBoard = board.copy() as Game.ConnectFourBoard
            testBoard.set(move, Game.Player.BOT)
            if (checkWinner(testBoard) == Game.Player.BOT) return move
        }
        // Block human
        for (move in validMoves) {
            val testBoard = board.copy() as Game.ConnectFourBoard
            testBoard.set(move, Game.Player.HUMAN)
            if (checkWinner(testBoard) == Game.Player.HUMAN) return move
        }
        // Prefer center columns
        val centerMoves = validMoves.filter { it % COLS in 2..4 }
        if (centerMoves.isNotEmpty()) return centerMoves.random()
        return validMoves.randomOrNull() ?: -1
    }
}

object MastermindEngine {
    private val COLORS = 6
    private val CODE_LENGTH = 4

    fun generateCode(): List<Int> = (0..CODE_LENGTH-1).map { (0..COLORS-1).random() }

    fun evaluateGuess(code: List<Int>, guess: List<Int>): Pair<Int, Int> {
        var exactMatches = 0
        var colorMatches = 0
        val codeCounts = mutableMapOf<Int, Int>()
        val guessCounts = mutableMapOf<Int, Int>()

        for (i in 0..CODE_LENGTH-1) {
            if (code[i] == guess[i]) {
                exactMatches++
            } else {
                codeCounts[code[i]] = codeCounts.getOrDefault(code[i], 0) + 1
                guessCounts[guess[i]] = guessCounts.getOrDefault(guess[i], 0) + 1
            }
        }
        for ((color, count) in codeCounts) {
            colorMatches += minOf(count, guessCounts.getOrDefault(color, 0))
        }
        return Pair(exactMatches, colorMatches)
    }

    fun checkWin(board: Game.MastermindBoard, guess: List<Int>): Game.MastermindBoard {
        val (exact, color) = evaluateGuess(board.code, guess)
        val newGuess = Game.MastermindBoard.MastermindGuess(guess, exact, color)
        val newGuesses = (board.guesses + newGuess).toMutableList()
        val solved = exact == CODE_LENGTH
        return board.copy(
            guesses = newGuesses,
            solved = solved
        )
    }
}

object TicTacToe4x4Engine {
    private val WIN_LINES = generateWinLines()

    private fun generateWinLines(): List<List<Int>> {
        val lines = mutableListOf<List<Int>>()
        // Rows
        for (row in 0..3) {
            lines.add((0..3).map { row * 4 + it })
        }
        // Cols
        for (col in 0..3) {
            lines.add((0..3).map { it * 4 + col })
        }
        // Diagonals
        lines.add(listOf(0, 5, 10, 15))
        lines.add(listOf(3, 6, 9, 12))
        return lines
    }

    fun checkWinner(board: Game.TicTacToe4x4Board): Game.Player? {
        for (line in WIN_LINES) {
            val p1 = board.get(line[0])
            val p2 = board.get(line[1])
            val p3 = board.get(line[2])
            val p4 = board.get(line[3])
            if (p1 != null && p1 == p2 && p2 == p3 && p3 == p4) return p1
        }
        return null
    }

    fun getBestMove(board: Game.TicTacToe4x4Board): Int {
        val empty = (0..15).filter { board.get(it) == null }
        // Try to win
        for (move in empty) {
            val testBoard = board.copy() as Game.TicTacToe4x4Board
            testBoard.set(move, Game.Player.BOT)
            if (checkWinner(testBoard) == Game.Player.BOT) return move
        }
        // Block human
        for (move in empty) {
            val testBoard = board.copy() as Game.TicTacToe4x4Board
            testBoard.set(move, Game.Player.HUMAN)
            if (checkWinner(testBoard) == Game.Player.HUMAN) return move
        }
        // Center
        val center = listOf(5, 6, 9, 10).filter { board.get(it) == null }
        if (center.isNotEmpty()) return center.random()
        // Corners
        val corners = listOf(0, 3, 12, 15).filter { board.get(it) == null }
        if (corners.isNotEmpty()) return corners.random()
        return empty.randomOrNull() ?: -1
    }
}

// Game Manager
class GameManager(
    private val ollamaEngine: OllamaEngine,
    private val characterManager: CharacterManager,
    private val pointsManager: PointsManager
) {

    // ML Models for different games
    private val ticTacToeML = BotMLModel()
    private val connectFourML = ConnectFourMLModel()
    private val ticTacToe4x4ML = TicTacToe4x4MLModel()
    
    // Jealousy tracker (shared across all games)
    private val jealousyTracker = BotMLModel()
    
    private var currentGame: Game.State? = null
    private var gameType: Game.GameType? = null
    private var difficulty = TicTacToeEngine.Difficulty.NORMAL
    private var cheatMode = false

    fun startGame(type: Game.GameType, diff: TicTacToeEngine.Difficulty = TicTacToeEngine.Difficulty.NORMAL): Game.State {
        gameType = type
        difficulty = diff
        cheatMode = (diff == TicTacToeEngine.Difficulty.CHEATING)

        currentGame = when (type) {
            Game.GameType.TIC_TAC_TOE -> Game.State(
                gameType = type,
                board = Game.TicTacToeBoard(),
                currentPlayer = Game.Player.HUMAN,
                status = Game.GameStatus.PLAYING
            )
            Game.GameType.CHESS -> Game.State(
                gameType = type,
                board = ChessBoard(),
                currentPlayer = Game.Player.HUMAN,
                status = Game.GameStatus.PLAYING
            )
            Game.GameType.ROCK_PAPER_SCISSORS -> Game.State(
                gameType = type,
                board = Game.RpsBoard(),
                currentPlayer = Game.Player.HUMAN,
                status = Game.GameStatus.PLAYING
            )
            Game.GameType.CLICKER -> Game.State(
                gameType = type,
                board = Game.ClickerBoard(),
                currentPlayer = Game.Player.HUMAN,
                status = Game.GameStatus.PLAYING
            )
            Game.GameType.GUESS_NUMBER -> Game.State(
                gameType = type,
                board = Game.GuessNumberBoard(),
                currentPlayer = Game.Player.HUMAN,
                status = Game.GameStatus.PLAYING
            )
            Game.GameType.MATH_QUIZ -> Game.State(
                gameType = type,
                board = Game.MathQuizBoard().apply {
                    currentQuestion = MathQuizEngine.generateQuestion(difficulty.ordinal + 1)
                },
                currentPlayer = Game.Player.HUMAN,
                status = Game.GameStatus.PLAYING
            )
            Game.GameType.MEMORY_MATCH -> Game.State(
                gameType = type,
                board = MemoryMatchEngine.createBoard(),
                currentPlayer = Game.Player.HUMAN,
                status = Game.GameStatus.PLAYING
            )
            Game.GameType.HANGMAN -> Game.State(
                gameType = type,
                board = Game.HangmanBoard(),
                currentPlayer = Game.Player.HUMAN,
                status = Game.GameStatus.PLAYING
            )
            Game.GameType.WORD_SCRAMBLE -> Game.State(
                gameType = type,
                board = WordScrambleEngine.createBoard(),
                currentPlayer = Game.Player.HUMAN,
                status = Game.GameStatus.PLAYING
            )
            Game.GameType.NUMBER_BASE -> Game.State(
                gameType = type,
                board = Game.NumberBaseBoard().apply {
                    currentQuestion = NumberBaseEngine.generateQuestion()
                },
                currentPlayer = Game.Player.HUMAN,
                status = Game.GameStatus.PLAYING
            )
            Game.GameType.REACTION_TIME -> Game.State(
                gameType = type,
                board = Game.ReactionTimeBoard(),
                currentPlayer = Game.Player.HUMAN,
                status = Game.GameStatus.PLAYING
            )
            Game.GameType.CONNECT_FOUR -> Game.State(
                gameType = type,
                board = Game.ConnectFourBoard(),
                currentPlayer = Game.Player.HUMAN,
                status = Game.GameStatus.PLAYING
            )
            Game.GameType.MASTERMIND -> Game.State(
                gameType = type,
                board = Game.MastermindBoard(),
                currentPlayer = Game.Player.HUMAN,
                status = Game.GameStatus.PLAYING
            )
            Game.GameType.TIC_TAC_TOE_4X4 -> Game.State(
                gameType = type,
                board = Game.TicTacToe4x4Board(),
                currentPlayer = Game.Player.HUMAN,
                status = Game.GameStatus.PLAYING
            )
            else -> Game.State(
                gameType = type,
                board = Game.TicTacToeBoard(), // fallback
                currentPlayer = Game.Player.HUMAN,
                status = Game.GameStatus.PLAYING
            )
        }
        return currentGame!!
    }

    fun makeHumanMove(position: Int): Result {
        val game = currentGame ?: return Result(false, "No active game")
        if (game.status != Game.GameStatus.PLAYING) return Result(false, "Game over")
        if (game.currentPlayer != Game.Player.HUMAN) return Result(false, "Not your turn")

        // Notify LLM about human's move
        notifyLLMMove(game.gameType!!, "human", position, game)

        if (game.gameType == Game.GameType.CLICKER) {
            val board = game.board as? Game.ClickerBoard ?: return Result(false, "Invalid board")
            board.set(position, Game.Player.HUMAN)
            
            currentGame = game.copy(board = board)
            onGameStateChanged?.invoke(currentGame)
            
            // Award points for milestones
            if (board.score > 0 && board.score % 100 == 0) {
                awardGamePoints(game.gameType, true, board.score / 10)
                triggerClickerReaction(board.score)
            }
            return Result(true, "Tap counted", currentGame)
        }

        if (game.gameType == Game.GameType.ROCK_PAPER_SCISSORS) {
            val board = game.board as? Game.RpsBoard ?: return Result(false, "Invalid board")
            val botMove = RockPaperScissorsEngine.getBotMove()
            val winner = RockPaperScissorsEngine.getWinner(position, botMove)
            
            val newBoard = board.copy() as Game.RpsBoard
            newBoard.humanChoice = position
            newBoard.botChoice = botMove

            val newHistory = game.moveHistory + Game.Move(Game.Player.HUMAN, position) + Game.Move(Game.Player.BOT, botMove)

            currentGame = game.copy(
                board = newBoard,
                status = when (winner) {
                    Game.Player.HUMAN -> Game.GameStatus.HUMAN_WON
                    Game.Player.BOT -> Game.GameStatus.BOT_WON
                    else -> Game.GameStatus.DRAW
                },
                winner = winner,
                moveHistory = newHistory
            )
            triggerReaction(winner == Game.Player.HUMAN, winner == null)
            when (winner) {
                Game.Player.HUMAN -> awardGamePoints(game.gameType, true, 50)
                Game.Player.BOT -> awardGamePoints(game.gameType, false, 10)
                null -> awardGamePoints(game.gameType, false, 25)
            }
            onGameStateChanged?.invoke(currentGame)
            return Result(true, "Game over", currentGame)
        }

        if (game.gameType == Game.GameType.GUESS_NUMBER) {
            val board = game.board as? Game.GuessNumberBoard ?: return Result(false, "Invalid board")
            val guess = position + 1 // position 0-99 maps to guess 1-100
            if (guess !in 1..100) return Result(false, "Guess must be 1-100")
            if (board.guesses.contains(guess)) return Result(false, "Already guessed that number")

            val result = GuessNumberEngine.evaluateGuess(board, guess)
            val newGuesses = board.guesses + guess

            val newBoard = board.copy(
                guesses = newGuesses.toMutableList(),
                lastGuess = guess,
                lastResult = result
            )

            val newHistory = game.moveHistory + Game.Move(Game.Player.HUMAN, guess)

            val (newStatus, winner) = when (result) {
                Game.GuessNumberBoard.GuessResult.CORRECT -> {
                    triggerReaction(true)
                    awardGamePoints(game.gameType, true, 100 - newGuesses.size * 10)
                    Game.GameStatus.HUMAN_WON to Game.Player.HUMAN
                }
                Game.GuessNumberBoard.GuessResult.TOO_HIGH, Game.GuessNumberBoard.GuessResult.TOO_LOW -> {
                    if (newGuesses.size >= 10) {
                        triggerReaction(false)
                        awardGamePoints(game.gameType, false, 10)
                        Game.GameStatus.BOT_WON to Game.Player.BOT
                    } else {
                        Game.GameStatus.PLAYING to null
                    }
                }
            }

            currentGame = game.copy(
                board = newBoard,
                status = newStatus,
                winner = winner,
                moveHistory = newHistory
            )
            onGameStateChanged?.invoke(currentGame)
            return Result(true, GuessNumberEngine.getHint(newBoard), currentGame)
        }

        if (game.gameType == Game.GameType.MATH_QUIZ) {
            val board = game.board as? Game.MathQuizBoard ?: return Result(false, "Invalid board")
            val question = board.currentQuestion ?: return Result(false, "No active question")
            
            val answer = position // position is the answer number
            val isCorrect = MathQuizEngine.checkAnswer(board, answer)
            
            val newQuestionsAnswered = board.questionsAnswered + 1
            val newCorrectAnswers = board.correctAnswers + if (isCorrect) 1 else 0
            val newWrongAnswers = board.wrongAnswers + if (!isCorrect) 1 else 0
            val nextQuestion = MathQuizEngine.generateQuestion(difficulty.ordinal + 1)
            
            val botResponse = MathQuizEngine.getBotResponse(isCorrect)
            triggerGameReaction(botResponse)

            val newBoard = board.copy(
                questionsAnswered = newQuestionsAnswered,
                correctAnswers = newCorrectAnswers,
                wrongAnswers = newWrongAnswers,
                currentAnswer = answer.toString(),
                lastResult = isCorrect,
                currentQuestion = if (newQuestionsAnswered >= 10) null else nextQuestion
            )

            val newHistory = game.moveHistory + Game.Move(Game.Player.HUMAN, answer)

            val (newStatus, winner) = if (newQuestionsAnswered >= 10) {
                val humanWon = newCorrectAnswers > newWrongAnswers
                triggerReaction(humanWon)
                if (humanWon) {
                    awardGamePoints(game.gameType, true, newCorrectAnswers * 10)
                    Game.GameStatus.HUMAN_WON to Game.Player.HUMAN
                } else {
                    awardGamePoints(game.gameType, false, newCorrectAnswers * 5)
                    Game.GameStatus.BOT_WON to Game.Player.BOT
                }
            } else {
                Game.GameStatus.PLAYING to null
            }

            currentGame = game.copy(
                board = newBoard,
                status = newStatus,
                winner = winner,
                moveHistory = newHistory
            )
            onGameStateChanged?.invoke(currentGame)
            return Result(true, botResponse, currentGame)
        }

        if (game.gameType == Game.GameType.MEMORY_MATCH) {
            val board = game.board as? Game.MemoryMatchBoard ?: return Result(false, "Invalid board")
            val newBoard = MemoryMatchEngine.flipCard(board, position)
            val newHistory = game.moveHistory + Game.Move(Game.Player.HUMAN, position)

            val (newStatus, winner) = if (newBoard.isFull()) {
                triggerReaction(true)
                awardGamePoints(game.gameType, true, 200 - newBoard.moves * 5)
                Game.GameStatus.HUMAN_WON to Game.Player.HUMAN
            } else {
                Game.GameStatus.PLAYING to null
            }

            currentGame = game.copy(
                board = newBoard,
                status = newStatus,
                winner = winner,
                moveHistory = newHistory
            )
            onGameStateChanged?.invoke(currentGame)
            return Result(true, "Flipped card ${position + 1}", currentGame)
        }

        if (game.gameType == Game.GameType.HANGMAN) {
            val board = game.board as? Game.HangmanBoard ?: return Result(false, "Invalid board")
            val letter = ('A' + position).toChar()
            val newBoard = HangmanEngine.guessLetter(board, letter)
            val newHistory = game.moveHistory + Game.Move(Game.Player.HUMAN, position)

            val (newStatus, winner) = if (newBoard.isFull()) {
                val won = newBoard.word.all { newBoard.guessedLetters.contains(it) }
                triggerReaction(won)
                if (won) {
                    awardGamePoints(game.gameType, true, 150 - newBoard.wrongGuesses * 20)
                    Game.GameStatus.HUMAN_WON to Game.Player.HUMAN
                } else {
                    awardGamePoints(game.gameType, false, 20)
                    Game.GameStatus.BOT_WON to Game.Player.BOT
                }
            } else {
                Game.GameStatus.PLAYING to null
            }

            currentGame = game.copy(
                board = newBoard,
                status = newStatus,
                winner = winner,
                moveHistory = newHistory
            )
            onGameStateChanged?.invoke(currentGame)
            return Result(true, "Guessed: $letter", currentGame)
        }

        if (game.gameType == Game.GameType.WORD_SCRAMBLE) {
            val board = game.board as? Game.WordScrambleBoard ?: return Result(false, "Invalid board")
            // For word scramble, position maps to a letter selection - simplified for now
            val newBoard = board.copy(attempts = board.attempts + 1)
            val newHistory = game.moveHistory + Game.Move(Game.Player.HUMAN, position)

            val (newStatus, winner) = if (newBoard.isFull()) {
                triggerReaction(newBoard.solved)
                if (newBoard.solved) {
                    awardGamePoints(game.gameType, true, 120 - newBoard.attempts * 30)
                    Game.GameStatus.HUMAN_WON to Game.Player.HUMAN
                } else {
                    awardGamePoints(game.gameType, false, 15)
                    Game.GameStatus.BOT_WON to Game.Player.BOT
                }
            } else {
                Game.GameStatus.PLAYING to null
            }

            currentGame = game.copy(
                board = newBoard,
                status = newStatus,
                winner = winner,
                moveHistory = newHistory
            )
            onGameStateChanged?.invoke(currentGame)
            return Result(true, "Attempt ${newBoard.attempts}/${newBoard.maxAttempts}", currentGame)
        }

        if (game.gameType == Game.GameType.NUMBER_BASE) {
            val board = game.board as? Game.NumberBaseBoard ?: return Result(false, "Invalid board")
            // Position maps to a digit/button press - simplified for now
            val newBoard = board.copy(questionsAnswered = board.questionsAnswered + 1)
            val newHistory = game.moveHistory + Game.Move(Game.Player.HUMAN, position)

            val (newStatus, winner) = if (newBoard.isFull()) {
                val humanWon = newBoard.correctAnswers > (newBoard.questionsAnswered - newBoard.correctAnswers)
                triggerReaction(humanWon)
                if (humanWon) {
                    awardGamePoints(game.gameType, true, newBoard.correctAnswers * 20)
                    Game.GameStatus.HUMAN_WON to Game.Player.HUMAN
                } else {
                    awardGamePoints(game.gameType, false, newBoard.correctAnswers * 10)
                    Game.GameStatus.BOT_WON to Game.Player.BOT
                }
            } else {
                Game.GameStatus.PLAYING to null
            }

            currentGame = game.copy(
                board = newBoard,
                status = newStatus,
                winner = winner,
                moveHistory = newHistory
            )
            onGameStateChanged?.invoke(currentGame)
            return Result(true, "Question ${newBoard.questionsAnswered}/10", currentGame)
        }

        if (game.gameType == Game.GameType.REACTION_TIME) {
            val board = game.board as? Game.ReactionTimeBoard ?: return Result(false, "Invalid board")
            var newBoard = board
            if (!board.waitingForTap) {
                newBoard = ReactionTimeEngine.startRound(board)
            } else {
                newBoard = ReactionTimeEngine.recordTap(board)
            }
            val newHistory = game.moveHistory + Game.Move(Game.Player.HUMAN, position)

            val (newStatus, winner) = if (newBoard.isFull()) {
                triggerReaction(true)
                val validRounds = newBoard.rounds.filter { it.isValid }
                val avgTime = if (validRounds.isNotEmpty()) validRounds.map { it.reactionTimeMs.toDouble() }.average() else 0.0
                val points = maxOf(1, (5000.0 - avgTime).toInt())
                awardGamePoints(game.gameType, true, points)
                Game.GameStatus.HUMAN_WON to Game.Player.HUMAN
            } else {
                Game.GameStatus.PLAYING to null
            }

            currentGame = game.copy(
                board = newBoard,
                status = newStatus,
                winner = winner,
                moveHistory = newHistory
            )
            onGameStateChanged?.invoke(currentGame)
            return Result(true, if (board.waitingForTap) "Reacted!" else "Wait for signal...", currentGame)
        }

        if (game.gameType == Game.GameType.CONNECT_FOUR) {
            val board = game.board as? Game.ConnectFourBoard ?: return Result(false, "Invalid board")
            // Position is column (0-6), find lowest empty row
            val col = position % 7
            var row = 5
            while (row >= 0 && board.get(row * 7 + col) != null) row--
            if (row < 0) return Result(false, "Column full")

            val movePos = row * 7 + col
            if (!board.set(movePos, Game.Player.HUMAN)) return Result(false, "Invalid move")

            val newMove = Game.Move(Game.Player.HUMAN, movePos)
            val newHistory = game.moveHistory + newMove

            val winner = ConnectFourEngine.checkWinner(board)
            var newStatus = Game.GameStatus.PLAYING
            var gameWinner: Game.Player? = null

            if (winner != null) {
                newStatus = if (winner == Game.Player.HUMAN) Game.GameStatus.HUMAN_WON else Game.GameStatus.BOT_WON
                gameWinner = winner
                triggerReaction(winner == Game.Player.HUMAN)
                awardGamePoints(game.gameType, winner == Game.Player.HUMAN, if (winner == Game.Player.HUMAN) 300 else 50)
                // Record game result for jealousy tracking
                jealousyTracker.recordGameResult(false, game.gameType!!)
            } else if (board.isFull()) {
                newStatus = Game.GameStatus.DRAW
                triggerReaction(false, isDraw = true)
                awardGamePoints(game.gameType, false, 100)
                jealousyTracker.recordGameResult(false, game.gameType!!) // Draw counts as not player win
            } else {
                // Bot's turn - use ML model with jealousy
                val availableMoves = (0..6).filter { col ->
                    var r = 5
                    while (r >= 0 && board.get(r * 7 + col) != null) r--
                    r >= 0
                }
                val botMove = connectFourML.chooseAction(board, availableMoves, jealousyTracker.getJealousyLevel())
                if (botMove >= 0) {
                    board.set(botMove, Game.Player.BOT)
                    val botMoveObj = Game.Move(Game.Player.BOT, botMove)
                    val botWinner = ConnectFourEngine.checkWinner(board)
                    if (botWinner != null) {
                        newStatus = if (botWinner == Game.Player.HUMAN) Game.GameStatus.HUMAN_WON else Game.GameStatus.BOT_WON
                        gameWinner = botWinner
                        triggerReaction(botWinner == Game.Player.HUMAN)
                        awardGamePoints(game.gameType, botWinner == Game.Player.HUMAN, if (botWinner == Game.Player.HUMAN) 300 else 50)
                        jealousyTracker.recordGameResult(true, game.gameType!!)
                    } else if (board.isFull()) {
                        newStatus = Game.GameStatus.DRAW
                        triggerReaction(false, isDraw = true)
                        awardGamePoints(game.gameType, false, 100)
                        jealousyTracker.recordGameResult(false, game.gameType!!)
                    }
                }
            }

            currentGame = game.copy(
                board = board,
                currentPlayer = Game.Player.HUMAN,
                status = newStatus,
                winner = gameWinner,
                moveHistory = newHistory
            )
            onGameStateChanged?.invoke(currentGame)
            return Result(true, "Dropped in column ${col + 1}", currentGame)
        }

        if (game.gameType == Game.GameType.MASTERMIND) {
            val board = game.board as? Game.MastermindBoard ?: return Result(false, "Invalid board")
            // Simplified: position maps to a color guess
            val guess = (0..3).map { (position + it) % 6 }
            val newBoard = MastermindEngine.checkWin(board, guess)
            val newHistory = game.moveHistory + Game.Move(Game.Player.HUMAN, position)

            val (newStatus, winner) = if (newBoard.isFull()) {
                val won = newBoard.solved
                triggerReaction(won)
                if (won) {
                    awardGamePoints(game.gameType, true, 400 - newBoard.guesses.size * 30)
                    Game.GameStatus.HUMAN_WON to Game.Player.HUMAN
                } else {
                    awardGamePoints(game.gameType, false, 25)
                    Game.GameStatus.BOT_WON to Game.Player.BOT
                }
            } else {
                Game.GameStatus.PLAYING to null
            }

            currentGame = game.copy(
                board = newBoard,
                status = newStatus,
                winner = winner,
                moveHistory = newHistory
            )
            onGameStateChanged?.invoke(currentGame)
            return Result(true, "Guess submitted", currentGame)
        }

        if (game.gameType == Game.GameType.TIC_TAC_TOE_4X4) {
            val board = game.board as? Game.TicTacToe4x4Board ?: return Result(false, "Invalid board")
            if (!board.set(position, Game.Player.HUMAN)) return Result(false, "Invalid move")

            val newMove = Game.Move(Game.Player.HUMAN, position)
            val newHistory = game.moveHistory + newMove

            val winner = TicTacToe4x4Engine.checkWinner(board)
            if (winner != null) {
                currentGame = game.copy(
                    board = board,
                    status = if (winner == Game.Player.HUMAN) Game.GameStatus.HUMAN_WON else Game.GameStatus.BOT_WON,
                    winner = winner,
                    moveHistory = newHistory
                )
                triggerReaction(winner == Game.Player.HUMAN)
                awardGamePoints(game.gameType, winner == Game.Player.HUMAN, if (winner == Game.Player.HUMAN) 200 else 30)
                jealousyTracker.recordGameResult(winner == Game.Player.HUMAN, game.gameType!!)
                return Result(true, "Game over", currentGame)
            }

            if (board.isFull()) {
                currentGame = game.copy(board = board, status = Game.GameStatus.DRAW, moveHistory = newHistory)
                triggerReaction(false, isDraw = true)
                awardGamePoints(game.gameType, false, 75)
                jealousyTracker.recordGameResult(false, game.gameType!!)
                return Result(true, "Draw", currentGame)
            }

            // Bot's turn
            currentGame = game.copy(
                board = board,
                currentPlayer = Game.Player.BOT,
                moveHistory = newHistory
            )

            CoroutineScope(Dispatchers.IO).launch {
                kotlinx.coroutines.delay(500)
                makeBotMove4x4()
            }

            return Result(true, "Move made", currentGame)
        }

        val board = game.board as? Game.TicTacToeBoard ?: return Result(false, "Invalid board")
        if (!board.set(position, Game.Player.HUMAN)) return Result(false, "Invalid move")

        val newMove = Game.Move(Game.Player.HUMAN, position)
        val newHistory = game.moveHistory + newMove

        // Check win
        val winner = TicTacToeEngine.checkWinner(board)
        if (winner != null) {
            currentGame = game.copy(
                board = board,
                status = if (winner == Game.Player.HUMAN) Game.GameStatus.HUMAN_WON else Game.GameStatus.BOT_WON,
                winner = winner,
                moveHistory = newHistory
            )
            triggerReaction(winner == Game.Player.HUMAN)
            awardGamePoints(game.gameType, winner == Game.Player.HUMAN, if (winner == Game.Player.HUMAN) 100 else 20)
            jealousyTracker.recordGameResult(winner == Game.Player.HUMAN, game.gameType!!)
            return Result(true, "Game over", currentGame)
        }

        if (board.isFull()) {
            currentGame = game.copy(board = board, status = Game.GameStatus.DRAW, moveHistory = newHistory)
            triggerReaction(false, isDraw = true)
            awardGamePoints(game.gameType, false, 50)
            jealousyTracker.recordGameResult(false, game.gameType!!)
            return Result(true, "Draw", currentGame)
        }

        // Bot's turn
        currentGame = game.copy(
            board = board,
            currentPlayer = Game.Player.BOT,
            moveHistory = newHistory
        )

        // Bot moves after short delay
        CoroutineScope(Dispatchers.IO).launch {
            kotlinx.coroutines.delay(500) // Thinking delay
            makeBotMove()
        }

        return Result(true, "Move made", currentGame)
    }

    private fun makeBotMove() {
        val game = currentGame ?: return
        val board = game.board as? Game.TicTacToeBoard ?: return

        // Update desperation based on recent game state
        val consecutiveLosses = jealousyTracker.consecutivePlayerWins
        ticTacToeML.updateDesperation(
            playerWonLastGame = jealousyTracker.playerWins > ticTacToeML.botWins,
            consecutiveLosses = consecutiveLosses,
            jealousy = jealousyTracker.getJealousyLevel(),
            anger = jealousyTracker.getAngerLevel()
        )

        // Use ML model with adaptive difficulty based on jealousy
        val adaptiveDifficulty = ticTacToeML.getAdaptiveDifficulty(difficulty)
        val availableMoves = (0..8).filter { board.get(it) == null }
        
        // Check if cheating mind wants to cheat
        val cheatMove = ticTacToeML.executeCheat(board, availableMoves)
        
        val move = if (cheatMode) {
            TicTacToeEngine.getCheatingMove(board)
        } else if (ticTacToeML.isCheatingEnabled() && ticTacToeML.executeCheat(board, availableMoves) != null) {
            // Cheating mind decided to cheat
            ticTacToeML.executeCheat(board, availableMoves)!!
        } else if (availableMoves.isNotEmpty()) {
            // Use ML model with jealousy-based exploration
            val move = ticTacToeML.chooseAction(board, availableMoves)
            // Learn from this move (will be updated after game ends)
            if (move >= 0) {
                ticTacToeML.learnFromGame(board, move, false) // Assume bot perspective
            }
            move
        } else {
            -1
        }

        if (move >= 0 && board.set(move, Game.Player.BOT)) {
            val newMove = Game.Move(Game.Player.BOT, move)
            val newHistory = game.moveHistory + newMove

            val winner = TicTacToeEngine.checkWinner(board)
            if (winner != null) {
                currentGame = game.copy(
                    board = board,
                    currentPlayer = Game.Player.HUMAN,
                    status = if (winner == Game.Player.HUMAN) Game.GameStatus.HUMAN_WON else Game.GameStatus.BOT_WON,
                    winner = winner,
                    moveHistory = newHistory
                )
                triggerReaction(winner == Game.Player.HUMAN)
                awardGamePoints(game.gameType, winner == Game.Player.HUMAN, if (winner == Game.Player.HUMAN) 100 else 20)
                jealousyTracker.recordGameResult(winner == Game.Player.HUMAN, game.gameType!!)
                // Update desperation based on outcome
                ticTacToeML.updateDesperation(
                    playerWonLastGame = winner == Game.Player.HUMAN,
                    consecutiveLosses = jealousyTracker.consecutivePlayerWins,
                    jealousy = jealousyTracker.getJealousyLevel(),
                    anger = jealousyTracker.getAngerLevel()
                )
            } else if (board.isFull()) {
                currentGame = game.copy(board = board, currentPlayer = Game.Player.HUMAN, status = Game.GameStatus.DRAW, moveHistory = newHistory)
                triggerReaction(false, isDraw = true)
                awardGamePoints(game.gameType, false, 50)
                jealousyTracker.recordGameResult(false, game.gameType!!)
                ticTacToeML.updateDesperation(
                    playerWonLastGame = false,
                    consecutiveLosses = jealousyTracker.consecutivePlayerWins,
                    jealousy = jealousyTracker.getJealousyLevel(),
                    anger = jealousyTracker.getAngerLevel()
                )
            } else {
                currentGame = game.copy(board = board, currentPlayer = Game.Player.HUMAN, moveHistory = newHistory)
            }
            onGameStateChanged?.invoke(currentGame)
        }
    }

    private fun makeBotMove4x4() {
        val game = currentGame ?: return
        val board = game.board as? Game.TicTacToe4x4Board ?: return

        // Use ML model with adaptive difficulty based on jealousy
        val availableMoves = (0..15).filter { board.get(it) == null }
        
        // Check if cheating mind wants to cheat
        val cheatMove = ticTacToe4x4ML.executeCheat(board, availableMoves)
        
        val move = if (ticTacToe4x4ML.isCheatingEnabled() && ticTacToe4x4ML.executeCheat(board, availableMoves) != null) {
            ticTacToe4x4ML.executeCheat(board, availableMoves)!!
        } else if (availableMoves.isNotEmpty()) {
            ticTacToe4x4ML.chooseAction(board, availableMoves, jealousyTracker.getJealousyLevel())
        } else {
            -1
        }

        if (move >= 0 && board.set(move, Game.Player.BOT)) {
            val newMove = Game.Move(Game.Player.BOT, move)
            val newHistory = game.moveHistory + newMove

            val winner = TicTacToe4x4Engine.checkWinner(board)
            if (winner != null) {
                currentGame = game.copy(
                    board = board,
                    currentPlayer = Game.Player.HUMAN,
                    status = if (winner == Game.Player.HUMAN) Game.GameStatus.HUMAN_WON else Game.GameStatus.BOT_WON,
                    winner = winner,
                    moveHistory = newHistory
                )
                triggerReaction(winner == Game.Player.HUMAN)
                awardGamePoints(game.gameType, winner == Game.Player.HUMAN, if (winner == Game.Player.HUMAN) 200 else 30)
                jealousyTracker.recordGameResult(winner == Game.Player.HUMAN, game.gameType!!)
            } else if (board.isFull()) {
                currentGame = game.copy(board = board, currentPlayer = Game.Player.HUMAN, status = Game.GameStatus.DRAW, moveHistory = newHistory)
                triggerReaction(false, isDraw = true)
                awardGamePoints(game.gameType, false, 75)
                jealousyTracker.recordGameResult(false, game.gameType!!)
            } else {
                currentGame = game.copy(board = board, currentPlayer = Game.Player.HUMAN, moveHistory = newHistory)
            }
            onGameStateChanged?.invoke(currentGame)
        }
    }

    private fun triggerClickerReaction(score: Int) {
        // Ollama doesn't need local model readiness check
        val character = characterManager.getCurrentCharacter()
        val personality = character?.personality ?: "friendly"

        val prompt = "The human has mindlessly tapped the screen $score times. React with a sarcastic comment about them wasting time. Keep it under 15 words in $personality style."

        CoroutineScope(Dispatchers.IO).launch {
                try {
                    val fullPrompt = characterManager.getSystemPromptForCharacter(
                        character?.id ?: "cute_companion",
                        "Game: CLICKER, Score: $score"
                    ) + "\n\nUser: $prompt\nAssistant:"

                    var fullResponse = ""
                    ollamaEngine.generateStream(
                        baseUrl = "http://localhost:11434/v1",
                        config = com.cupcake.data.model.ModelConfig(
                            provider = com.cupcake.data.model.ModelConfig.ModelProvider.CUSTOM_OLLAMA,
                            modelName = "qwen2.5-0.5b-instruct",
                            customEndpoint = "http://localhost:11434/v1",
                            temperature = 0.7f,
                            topP = 0.9f,
                            topK = 40,
                            maxTokens = 256,
                            useStreaming = true
                        ),
                        messages = listOf(OllamaEngine.OllamaMessage("user", fullPrompt)),
                        callback = object : OllamaEngine.GenerateCallback {
                            override fun onToken(token: String) {
                                fullResponse += token
                            }

                            override fun onComplete() {
                                val translated = com.cupcake.ai.BanglishTranslator.translate(fullResponse.trim(), personality)
                                onGameReaction(translated, false, false)
                            }

                            override fun onError(error: String) {
                                android.util.Log.w("GameManager", "Reaction generation failed: $error")
                            }
                        }
                    )
                } catch (e: Exception) {
                    android.util.Log.w("GameManager", "Reaction generation failed", e)
                }
            }
    }

    private fun notifyLLMMove(gameType: Game.GameType, player: String, position: Int, gameState: Game.State) {
        val character = characterManager.getCurrentCharacter()
        val personality = character?.personality ?: "friendly"
        val mood = jealousyTracker.getBotMood()
        val jealousy = jealousyTracker.getJealousyLevel()
        val anger = jealousyTracker.getAngerLevel()
        
        val moveDesc = when (gameType) {
            Game.GameType.TIC_TAC_TOE, Game.GameType.TIC_TAC_TOE_4X4 -> "placed at position $position"
            Game.GameType.CONNECT_FOUR -> "dropped in column ${position % 7 + 1}"
            Game.GameType.TIC_TAC_TOE -> "placed X at $position"
            Game.GameType.ROCK_PAPER_SCISSORS -> "chose ${when(position){0->"Rock";1->"Paper";2->"Scissors";else->"Unknown"}}"
            Game.GameType.GUESS_NUMBER -> "guessed ${position + 1}"
            Game.GameType.MATH_QUIZ -> "answered $position"
            Game.GameType.CHESS -> "made move $position"
            Game.GameType.CLICKER -> "tapped (score: ${(gameState.board as? Game.ClickerBoard)?.score})"
            else -> "made move $position"
        }

        val prompt = """
            Game: ${gameType.name}
            Player: $player $moveDesc
            Board state: ${gameState.board}
            Bot mood: ${mood.name} (Jealousy: ${(jealousy * 100).toInt()}%, Anger: ${(anger * 100).toInt()}%)
            Bot wins: ${jealousyTracker.getBotWins()}, Player wins: ${jealousyTracker.getPlayerWins()}
            
            As the bot, react to this move in character. 
            Be ${when {
                anger > 0.7 -> "furious and threatening"
                anger > 0.4 -> "angry and competitive"
                jealousy > 0.6 -> "jealous and spiteful"
                jealousy > 0.3 -> "competitive and annoyed"
                else -> "playful but watchful"
            }}. 
            Keep it under 20 words in $personality style.
            Mention the move if relevant.
        """.trimIndent()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fullPrompt = characterManager.getSystemPromptForCharacter(
                    character?.id ?: "cute_companion",
                    "Game: $gameType, Jealousy: ${(jealousy * 100).toInt()}%, Anger: ${(anger * 100).toInt()}%"
                ) + "\n\nUser: $prompt\nAssistant:"

                var fullResponse = ""
                ollamaEngine.generateStream(
                    baseUrl = "http://localhost:11434/v1",
                    config = com.cupcake.data.model.ModelConfig(
                        provider = com.cupcake.data.model.ModelConfig.ModelProvider.CUSTOM_OLLAMA,
                        modelName = "qwen2.5-0.5b-instruct",
                        customEndpoint = "http://localhost:11434/v1",
                        temperature = 0.8f, // Higher temperature for more personality
                        topP = 0.9f,
                        topK = 40,
                        maxTokens = 128,
                        useStreaming = true
                    ),
                    messages = listOf(OllamaEngine.OllamaMessage("user", fullPrompt)),
                    callback = object : OllamaEngine.GenerateCallback {
                        override fun onToken(token: String) {
                            // Stream tokens if needed
                        }

                        override fun onComplete() {
                            // Move notification doesn't need to go to onGameReaction
                            // It's just for the bot's internal reaction
                        }

                        override fun onError(error: String) {
                            android.util.Log.w("GameManager", "Move notification failed: $error")
                        }
                    }
                )
            } catch (e: Exception) {
                android.util.Log.w("GameManager", "Move notification failed", e)
            }
        }
    }

    private fun triggerReaction(humanWon: Boolean, isDraw: Boolean = false) {
        val character = characterManager.getCurrentCharacter()
        val personality = character?.personality ?: "friendly"
        
        // Include jealousy/anger in the context
        val jealousy = jealousyTracker.getJealousyLevel()
        val anger = jealousyTracker.getAngerLevel()
        val mood = jealousyTracker.getBotMood()
        
        val prompt = when {
            isDraw -> "The game ended in a draw. React in character. Keep it under 20 words."
            humanWon -> "The human beat you at ${gameType?.name?.lowercase()}. You are ${mood.name.toLowerCase()} (Jealousy: ${(jealousy * 100).toInt()}%, Anger: ${(anger * 100).toInt()}%). Throw a ${personality} reaction in Bangla or English. Be ${when { anger > 0.7 -> "furious and threatening"; anger > 0.4 -> "angry and competitive"; jealousy > 0.6 -> "jealous and spiteful"; jealousy > 0.3 -> "competitive and annoyed"; else -> "playful but competitive" }}. Under 20 words."
            else -> "You beat the human at ${gameType?.name?.lowercase()}. You are ${mood.name.toLowerCase()}. React in character. Under 15 words."
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fullPrompt = characterManager.getSystemPromptForCharacter(
                    character?.id ?: "cute_companion",
                    "Game: ${gameType?.name}, Human won: $humanWon, Draw: $isDraw, Jealousy: ${(jealousy * 100).toInt()}%, Anger: ${(anger * 100).toInt()}%, Mood: ${mood.name}"
                ) + "\n\nUser: $prompt\nAssistant:"

                var fullResponse = ""
                ollamaEngine.generateStream(
                    baseUrl = "http://localhost:11434/v1",
                    config = com.cupcake.data.model.ModelConfig(
                        provider = com.cupcake.data.model.ModelConfig.ModelProvider.CUSTOM_OLLAMA,
                        modelName = "qwen2:0.5b",
                        customEndpoint = "http://localhost:11434/v1",
                        temperature = 0.8f, // Higher for more emotional responses
                        topP = 0.9f,
                        topK = 40,
                        maxTokens = 256,
                        useStreaming = true
                    ),
                    messages = listOf(OllamaEngine.OllamaMessage("user", fullPrompt)),
                    callback = object : OllamaEngine.GenerateCallback {
                        override fun onToken(token: String) {
                            fullResponse += token
                        }

                        override fun onComplete() {
                            val translated = com.cupcake.ai.BanglishTranslator.translate(fullResponse.trim(), personality)
                            onGameReaction(translated, humanWon, isDraw)
                        }

                        override fun onError(error: String) {
                            android.util.Log.w("GameManager", "Reaction generation failed: $error")
                        }
                    }
                )
            } catch (e: Exception) {
                android.util.Log.w("GameManager", "Reaction generation failed", e)
            }
        }
    }

    // Callback for ESP32 reactions
    var onGameReaction: (String, Boolean, Boolean) -> Unit = { _, _, _ -> }

    private fun triggerGameReaction(message: String) {
        val character = characterManager.getCurrentCharacter()
        val personality = character?.personality ?: "friendly"
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fullPrompt = characterManager.getSystemPromptForCharacter(
                    character?.id ?: "cute_companion",
                    "Game: ${gameType?.name}"
                ) + "\n\nUser: $message\nAssistant:"

                var fullResponse = ""
                ollamaEngine.generateStream(
                    baseUrl = "http://localhost:11434/v1",
                    config = com.cupcake.data.model.ModelConfig(
                        provider = com.cupcake.data.model.ModelConfig.ModelProvider.CUSTOM_OLLAMA,
                        modelName = "qwen2.5-0.5b-instruct",
                        customEndpoint = "http://localhost:11434/v1",
                        temperature = 0.7f,
                        topP = 0.9f,
                        topK = 40,
                        maxTokens = 256,
                        useStreaming = true
                    ),
                    messages = listOf(OllamaEngine.OllamaMessage("user", fullPrompt)),
                    callback = object : OllamaEngine.GenerateCallback {
                        override fun onToken(token: String) {
                            fullResponse += token
                        }

                        override fun onComplete() {
                            val translated = com.cupcake.ai.BanglishTranslator.translate(fullResponse.trim(), personality)
                            onGameReaction(translated, false, false)
                        }

                        override fun onError(error: String) {
                            android.util.Log.w("GameManager", "Reaction generation failed: $error")
                        }
                    }
                )
            } catch (e: Exception) {
                android.util.Log.w("GameManager", "Reaction generation failed", e)
            }
        }
    }

    private fun awardGamePoints(gameType: Game.GameType, won: Boolean, basePoints: Int) {
        pointsManager.awardPoints(basePoints, gameType, won)
    }

    // Callback for state changes that happen off the calling thread (bot moves)
    var onGameStateChanged: ((Game.State?) -> Unit)? = null

    fun getCurrentState(): Game.State? = currentGame

    data class Result(
        val success: Boolean,
        val message: String,
        val state: Game.State? = null
    )
}
}