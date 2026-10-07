package com.cupcake.game

import com.cupcake.ai.CharacterManager
import com.cupcake.ai.LlamaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import java.util.Random

sealed interface Game {
    data class State(
        val gameType: GameType,
        val board: Board,
        val currentPlayer: Player,
        val status: GameStatus,
        val winner: Player? = null,
        val moveHistory: List<Move> = emptyList()
    ) : Game

    enum class GameType { TIC_TAC_TOE, CHESS, ROCK_PAPER_SCISSORS, GUESS_NUMBER, MATH_QUIZ, CLICKER }

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
        fun copy(): Board
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
        override fun copy(): Board = ClickerBoard(score)
    }

    // Tic Tac Toe Board
    data class TicTacToeBoard(
        override val size: Int = 9,
        private val cells: Array<Player?> = Array(9) { null }
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
        override fun copy(): Board = copy(cells = cells.clone())
    }

    // Chess board (simplified - using Stockfish later)
    data class ChessBoard(
        override val size: Int = 64,
        private val pieces: Array<String?> = Array(64) { null }
    ) : Board {
        override fun get(position: Int): Player? = null // Simplified
        override fun set(position: Int, player: Player): Boolean = false
        override fun isFull(): Boolean = false
        override fun copy(): Board = this
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
        override fun copy(): Board = this.copy()
    }
}

// Tic Tac Toe Engine
object TicTacToeEngine {
    private val WIN_LINES = listOf(
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

// Game Manager
class GameManager(private val llamaEngine: LlamaEngine, private val characterManager: CharacterManager) {

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
                board = Game.ChessBoard(),
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

        if (game.gameType == Game.GameType.CLICKER) {
            val board = game.board as? Game.ClickerBoard ?: return Result(false, "Invalid board")
            board.set(position, Game.Player.HUMAN)
            
            currentGame = game.copy(board = board)
            onGameStateChanged?.invoke(currentGame)
            
            // Trigger sarcastic reaction every 50 taps
            if (board.score > 0 && board.score % 50 == 0) {
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
            onGameStateChanged?.invoke(currentGame)
            return Result(true, "Game over", currentGame)
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
            return Result(true, "Game over", currentGame)
        }

        if (board.isFull()) {
            currentGame = game.copy(board = board, status = Game.GameStatus.DRAW, moveHistory = newHistory)
            triggerReaction(false, isDraw = true)
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
            Thread.sleep(500) // Thinking delay
            makeBotMove()
        }

        return Result(true, "Move made", currentGame)
    }

    private fun makeBotMove() {
        val game = currentGame ?: return
        val board = game.board as? Game.TicTacToeBoard ?: return

        val move = if (cheatMode) {
            TicTacToeEngine.getCheatingMove(board)
        } else {
            TicTacToeEngine.getBestMove(board, difficulty)
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
            } else if (board.isFull()) {
                currentGame = game.copy(board = board, currentPlayer = Game.Player.HUMAN, status = Game.GameStatus.DRAW, moveHistory = newHistory)
                triggerReaction(false, isDraw = true)
            } else {
                currentGame = game.copy(board = board, currentPlayer = Game.Player.HUMAN, moveHistory = newHistory)
            }
            onGameStateChanged?.invoke(currentGame)
        }
    }

    private fun triggerClickerReaction(score: Int) {
        if (!llamaEngine.isReady()) return
        val character = characterManager.getCurrentCharacter()
        val personality = character?.personality ?: "friendly"

        val prompt = "The human has mindlessly tapped the screen $score times. React with a sarcastic comment about them wasting time. Keep it under 15 words in $personality style."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fullPrompt = characterManager.getSystemPromptForCharacter(
                    character?.id ?: "cute_companion",
                    "Game: CLICKER, Score: $score"
                ) + "\n\nUser: $prompt\nAssistant:"

                llamaEngine.generateStream(fullPrompt).consumeEach { token ->
                    onGameReaction(token, false, false)
                }
            } catch (e: Exception) {
                android.util.Log.w("GameManager", "Reaction generation failed", e)
            }
        }
    }

    private fun triggerReaction(humanWon: Boolean, isDraw: Boolean = false) {
        // LLM reactions need a loaded model; the game itself is fully playable
        // without it.
        if (!llamaEngine.isReady()) return
        val character = characterManager.getCurrentCharacter()
        val personality = character?.personality ?: "friendly"

        val prompt = when {
            isDraw -> "The game ended in a draw. React in character. Keep it under 15 words."
            humanWon -> "The human beat you at ${gameType?.name?.lowercase()}. Throw a ${personality} reaction in Bangla or English. Under 15 words."
            else -> "You beat the human at ${gameType?.name?.lowercase()}. React in character. Under 15 words."
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fullPrompt = characterManager.getSystemPromptForCharacter(
                    character?.id ?: "cute_companion",
                    "Game: ${gameType?.name}, Human won: $humanWon, Draw: $isDraw"
                ) + "\n\nUser: $prompt\nAssistant:"

                llamaEngine.generateStream(fullPrompt).consumeEach { token ->
                    // Send to ESP32 for face animation + TTS
                    onGameReaction(token, humanWon, isDraw)
                }
            } catch (e: Exception) {
                android.util.Log.w("GameManager", "Reaction generation failed", e)
            }
        }
    }

    // Callback for ESP32 reactions
    var onGameReaction: (String, Boolean, Boolean) -> Unit = { _, _, _ -> }

    // Callback for state changes that happen off the calling thread (bot moves)
    var onGameStateChanged: ((Game.State?) -> Unit)? = null

    fun getCurrentState(): Game.State? = currentGame

    data class Result(
        val success: Boolean,
        val message: String,
        val state: Game.State? = null
    )
}