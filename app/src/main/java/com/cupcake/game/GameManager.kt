package com.cupcake.game

import com.cupcake.ai.CharacterManager
import com.cupcake.ai.OllamaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

// Forward declarations for game engines used in data class initializers


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
    private var pendingMemoryUnflip: List<Int>? = null

    fun startGame(type: Game.GameType, diff: TicTacToeEngine.Difficulty = TicTacToeEngine.Difficulty.NORMAL): Game.State {
        gameType = type
        difficulty = diff
        cheatMode = (diff == TicTacToeEngine.Difficulty.CHEATING)
        pendingMemoryUnflip = null

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
            if (pendingMemoryUnflip != null) return Result(false, "Cards are flipping back")
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

            // Two cards face up with no match: flip them back after a short delay
            val mismatched = newBoard.cards.indices.filter {
                newBoard.cards[it].isFlipped && !newBoard.cards[it].isMatched
            }
            if (mismatched.size == 2) {
                pendingMemoryUnflip = mismatched
                CoroutineScope(Dispatchers.IO).launch {
                    kotlinx.coroutines.delay(900)
                    unflipMemoryCards()
                }
            }

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

    /**
     * Submit a typed answer for games that need one (Word Scramble, Number Base).
     * Unlike [makeHumanMove] this carries the actual text instead of a board position,
     * so the engine can really check the answer.
     */
    fun submitTextAnswer(answer: String): Result {
        val game = currentGame ?: return Result(false, "No active game")
        if (game.status != Game.GameStatus.PLAYING) return Result(false, "Game over")

        return when (game.gameType) {
            Game.GameType.WORD_SCRAMBLE -> {
                val board = game.board as? Game.WordScrambleBoard ?: return Result(false, "Invalid board")
                notifyLLMMove(game.gameType, "human", answer.length, game)
                val newBoard = WordScrambleEngine.checkAnswer(board, answer)
                val newHistory = game.moveHistory + Game.Move(Game.Player.HUMAN, answer.length)

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
                Result(
                    true,
                    if (newBoard.solved) "Solved in ${newBoard.attempts} attempt(s)!"
                    else "Attempt ${newBoard.attempts}/${newBoard.maxAttempts}",
                    currentGame
                )
            }

            Game.GameType.NUMBER_BASE -> {
                val board = game.board as? Game.NumberBaseBoard ?: return Result(false, "Invalid board")
                notifyLLMMove(game.gameType, "human", answer.length, game)
                val newBoard = NumberBaseEngine.checkAnswer(board, answer)
                val newHistory = game.moveHistory + Game.Move(Game.Player.HUMAN, answer.length)

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
                Result(true, "Answer ${newBoard.questionsAnswered}/10", currentGame)
            }

            else -> Result(false, "This game does not take text answers")
        }
    }

    /**
     * Submit a full 4-color guess for Mastermind with real answer checking.
     * Each color is an index into the 6-color palette (0-5).
     */
    fun submitMastermindGuess(guess: List<Int>): Result {
        val game = currentGame ?: return Result(false, "No active game")
        if (game.status != Game.GameStatus.PLAYING) return Result(false, "Game over")
        if (game.gameType != Game.GameType.MASTERMIND) return Result(false, "Mastermind is not the active game")
        if (guess.size != 4 || guess.any { it !in 0..5 }) return Result(false, "Guess must be 4 colors (0-5)")

        val board = game.board as? Game.MastermindBoard ?: return Result(false, "Invalid board")
        notifyLLMMove(game.gameType, "human", guess.first(), game)
        val newBoard = MastermindEngine.checkWin(board, guess)
        val newHistory = game.moveHistory + Game.Move(Game.Player.HUMAN, guess.first())

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
        val last = newBoard.guesses.lastOrNull()
        return Result(true, "Exact: ${last?.exactMatches ?: 0}, Color: ${last?.colorMatches ?: 0}", currentGame)
    }

    private fun unflipMemoryCards() {
        val indices = pendingMemoryUnflip ?: return
        pendingMemoryUnflip = null
        val game = currentGame ?: return
        if (game.gameType != Game.GameType.MEMORY_MATCH) return
        val board = game.board as? Game.MemoryMatchBoard ?: return
        currentGame = game.copy(board = MemoryMatchEngine.unflip(board, indices))
        onGameStateChanged?.invoke(currentGame)
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
                        character?.id ?: "marin_kitagawa",
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
                    character?.id ?: "marin_kitagawa",
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
                    character?.id ?: "marin_kitagawa",
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
                            val fallback = when {
                                isDraw -> "A draw?! No way, next round I'm definitely taking the win!"
                                humanWon -> if (jealousy > 0.4) "Kyaa! You totally cheated! Hmph, I'm pouting now! 😤" else "No way! Rematch right now, you big bully!"
                                else -> "Hehe! Look who won! You owe me a cosplay treat! ✨"
                            }
                            onGameReaction(fallback, humanWon, isDraw)
                        }
                    }
                )
            } catch (e: Exception) {
                android.util.Log.w("GameManager", "Reaction generation failed", e)
                val fallback = when {
                    isDraw -> "A draw?! Next round I'm definitely winning!"
                    humanWon -> "Kyaa! No way, you cheated! Rematch right now! 😤"
                    else -> "Hehe! Look who won! You can't beat me! ✨"
                }
                onGameReaction(fallback, humanWon, isDraw)
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
