package com.cupcake.game

import com.cupcake.ai.BanglishTranslator
import com.cupcake.ai.CharacterManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

// Forward declarations for game engines used in data class initializers


// Game Manager
class GameManager(
    private val llmRouter: GameLLMRouter,
    private val characterManager: CharacterManager,
    private val pointsManager: PointsManager,
    private val sessionStore: com.cupcake.chat.ChatSessionStore = com.cupcake.chat.ChatSessionStore()
) {

    /**
     * Live bot commentary about individual moves. Collected by the Games screen
     * (bubble above the board) and persisted to the character's chat session.
     */
    val gameComments = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 32)

    /** Game-ending / milestone reactions: outcome flags drive TTS + ESP32 expressions. */
    val gameReactions = kotlinx.coroutines.flow.MutableSharedFlow<GameReaction>(extraBufferCapacity = 32)

    data class GameReaction(
        val text: String,
        val humanWon: Boolean,
        val isDraw: Boolean,
        val mood: ReactionMood = ReactionMood.NEUTRAL
    )

    /**
     * Emotional colour of a reaction, so the UI/TTS/ESP32 can pick the matching
     * face instead of guessing from [GameReaction.humanWon] alone. The companion
     * escalates HAPPY -> ANGRY -> SAD as the human keeps winning.
     */
    enum class ReactionMood { NEUTRAL, HAPPY, ANGRY, SAD, GLOATING }

    init {
        // Persist everything the bot says about the game into the character's
        // chat session, regardless of which screen is open.
        val persistScope = CoroutineScope(Dispatchers.IO)
        persistScope.launch {
            gameComments.collect { text -> persistBotUtterance(text) }
        }
        persistScope.launch {
            gameReactions.collect { reaction -> persistBotUtterance(reaction.text) }
        }
    }

    private fun persistBotUtterance(text: String) {
        if (text.isBlank()) return
        val characterId = characterManager.getCurrentCharacter()?.id ?: "marin_kitagawa"
        sessionStore.addMessage(
            characterId,
            com.cupcake.data.model.ChatMessage.assistant(
                text,
                sessionStore.conversationIdFor(characterId),
                modelUsed = "game"
            )
        )
    }

    // Games where per-move LLM notifications are skipped:
    // - CLICKER / REACTION_TIME: taps are too rapid (milestone/end reactions still fire)
    // - TIC_TAC_TOE(_4X4): the bot replies with its own move, which notifies instead (one call per round)
    private val MOVE_NOTIFY_EXEMPT = setOf(
        Game.GameType.CLICKER,
        Game.GameType.REACTION_TIME,
        Game.GameType.TIC_TAC_TOE,
        Game.GameType.TIC_TAC_TOE_4X4,
        Game.GameType.PING_PONG,
        Game.GameType.CHESS
    )

    // ML Models for different games
    private val ticTacToeML = BotMLModel()
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
        jealousyTracker.resetThreat()

        if (type == Game.GameType.ROCK_PAPER_SCISSORS) {
            RockPaperScissorsEngine.reset()
        }

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
            Game.GameType.PING_PONG -> Game.State(
                gameType = type,
                board = Game.PingPongBoard(),
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
        if (type == Game.GameType.PING_PONG) {
            gameComments.tryEmit(
                BotTrashTalk.welcomeLine(jealousyTracker.getAdaptiveDifficulty(diff).name.lowercase())
            )
        }
        return currentGame!!
    }

    fun makeHumanMove(position: Int): Result {
        val game = currentGame ?: return Result(false, "No active game")
        if (game.status != Game.GameStatus.PLAYING) return Result(false, "Game over")
        if (game.currentPlayer != Game.Player.HUMAN) return Result(false, "Not your turn")

        val gameType = game.gameType
        val result = applyHumanMove(position)

        // Re-score how close the human is to winning; this deterministically drives
        // Marin's anger and the adaptive difficulty from here on.
        if (result.success) refreshThreat()

        // Send the move to the bot AFTER the board has been updated, so the
        // prompt contains the real position of every piece/card/disc.
        // Game-ending moves trigger triggerReaction() instead (one bot message per event).
        if (result.success &&
            gameType !in MOVE_NOTIFY_EXEMPT &&
            currentGame?.status == Game.GameStatus.PLAYING
        ) {
            notifyLLMMove(gameType, "human", position, currentGame ?: game)
        }
        return result
    }

    private fun applyHumanMove(position: Int): Result {
        val game = currentGame ?: return Result(false, "No active game")
        if (game.status != Game.GameStatus.PLAYING) return Result(false, "Game over")
        if (game.currentPlayer != Game.Player.HUMAN) return Result(false, "Not your turn")

        if (game.gameType == Game.GameType.CLICKER) {
            val board = game.board as? Game.ClickerBoard ?: return Result(false, "Invalid board")
            if (board.finished) return Result(false, "Round over")

            board.set(position, Game.Player.HUMAN)
            currentGame = game.copy(board = board)
            onGameStateChanged?.invoke(currentGame)

            // A comment every ten taps, not every tap.
            if (board.score % 10 == 0) triggerClickerReaction(board.score)

            // Hitting the target ends the round immediately; the UI timer ends it too.
            if (board.score >= board.target) return finishClicker()
            return Result(true, "Tap counted", currentGame)
        }

        if (game.gameType == Game.GameType.ROCK_PAPER_SCISSORS) {
            val board = game.board as? Game.RpsBoard ?: return Result(false, "Invalid board")
            val botMove = RockPaperScissorsEngine.getBotMove(position)
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
                val validRounds = newBoard.rounds.count { it.isValid }
                if (validRounds >= ReactionTimeEngine.REACTION_WIN_ROUNDS) {
                    triggerReaction(true)
                    val times = newBoard.rounds.filter { it.isValid }.map { it.reactionTimeMs.toDouble() }
                    val avgTime = if (times.isNotEmpty()) times.average() else 0.0
                    val points = maxOf(1, (5000.0 - avgTime).toInt())
                    awardGamePoints(game.gameType, true, points)
                    jealousyTracker.recordGameResult(true, game.gameType!!)
                    Game.GameStatus.HUMAN_WON to Game.Player.HUMAN
                } else {
                    // Fewer than the required clean rounds: too many early taps.
                    triggerReaction(false)
                    awardGamePoints(game.gameType, false, 0)
                    jealousyTracker.recordGameResult(false, game.gameType!!)
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
            return Result(true, if (newBoard.waitingForTap) "Wait for signal..." else "Reacted!", currentGame)
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

            val winner = ConnectFourEngine.checkWinner(board)
            var newStatus = Game.GameStatus.PLAYING
            var gameWinner: Game.Player? = null
            var newHistory = game.moveHistory + Game.Move(Game.Player.HUMAN, movePos)

            if (winner != null) {
                newStatus = if (winner == Game.Player.HUMAN) Game.GameStatus.HUMAN_WON else Game.GameStatus.BOT_WON
                gameWinner = winner
                triggerReaction(winner == Game.Player.HUMAN)
                awardGamePoints(game.gameType, winner == Game.Player.HUMAN, if (winner == Game.Player.HUMAN) 300 else 50)
                jealousyTracker.recordGameResult(winner == Game.Player.HUMAN, game.gameType!!)
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

            if (board.isFull()) {
                newStatus = Game.GameStatus.DRAW
                triggerReaction(false, isDraw = true)
                awardGamePoints(game.gameType, false, 100)
                jealousyTracker.recordGameResult(false, game.gameType!!) // Draw counts as not player win
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

            // Human move stands; bot answers after a short "thinking" delay so it
            // feels like a real opponent rather than an instant computer reply.
            currentGame = game.copy(
                board = board,
                currentPlayer = Game.Player.BOT,
                status = Game.GameStatus.PLAYING,
                moveHistory = newHistory
            )
            onGameStateChanged?.invoke(currentGame)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    kotlinx.coroutines.delay(500)
                    makeBotMoveConnectFour()
                } catch (e: Exception) {
                    android.util.Log.e("GameManager", "Connect Four bot move failed", e)
                }
            }
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
            try {
                kotlinx.coroutines.delay(500) // Thinking delay
                makeBotMove()
            } catch (e: Exception) {
                android.util.Log.e("GameManager", "Bot move failed", e)
            }
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
                if (newStatus == Game.GameStatus.PLAYING) {
                    notifyLLMMove(game.gameType, "human", answer.length, currentGame ?: game, detail = answer)
                }
                Result(
                    true,
                    if (newBoard.solved) "Solved in ${newBoard.attempts} attempt(s)!"
                    else "Attempt ${newBoard.attempts}/${newBoard.maxAttempts}",
                    currentGame
                )
            }

            Game.GameType.NUMBER_BASE -> {
                val board = game.board as? Game.NumberBaseBoard ?: return Result(false, "Invalid board")
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
                if (newStatus == Game.GameStatus.PLAYING) {
                    notifyLLMMove(game.gameType, "human", answer.length, currentGame ?: game, detail = answer)
                }
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
        if (newStatus == Game.GameStatus.PLAYING) {
            val palette = listOf("🔴", "🔵", "🟢", "🟡", "🟣", "🟠")
            notifyLLMMove(
                game.gameType, "human", guess.first(), currentGame ?: game,
                detail = guess.joinToString("") { palette[it] } +
                    " (exact=${last?.exactMatches ?: 0}, color=${last?.colorMatches ?: 0})"
            )
        }
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

        // Adaptive difficulty: as the human keeps winning, the shared jealousy/anger
        // tracker promotes the bot from EASY up to CHEATING. That is the "she gets
        // angry and raises the level" behaviour.
        val adaptiveDifficulty = jealousyTracker.getAdaptiveDifficulty(difficulty)
        val availableMoves = (0..8).filter { board.get(it) == null }
        if (availableMoves.isEmpty()) return

        // Check if cheating mind wants to cheat
        val mlCheat = if (ticTacToeML.isCheatingEnabled()) ticTacToeML.executeCheat(board, availableMoves) else null

        val move = when {
            cheatMode -> TicTacToeEngine.getCheatingMove(board)
            mlCheat != null -> mlCheat
            // Real opponent: perfect minimax on HARD/CHEATING, heuristic below that.
            else -> TicTacToeEngine.getBestMove(board, adaptiveDifficulty)
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
            // Tell the bot about its own completed round (human move + bot reply)
            if (currentGame?.status == Game.GameStatus.PLAYING) {
                currentGame?.let { notifyLLMMove(game.gameType, "bot", move, it) }
            }
        }
    }

    private fun makeBotMove4x4() {
        val game = currentGame ?: return
        val board = game.board as? Game.TicTacToe4x4Board ?: return

        // Use ML model with adaptive difficulty based on jealousy
        val availableMoves = (0..15).filter { board.get(it) == null }
        
        // Check if cheating mind wants to cheat
        val mlCheat = if (ticTacToe4x4ML.isCheatingEnabled()) ticTacToe4x4ML.executeCheat(board, availableMoves) else null
        
        val move = when {
            mlCheat != null -> mlCheat
            availableMoves.isNotEmpty() -> TicTacToe4x4Engine.getBestMove(board, jealousyTracker.getAdaptiveDifficulty(difficulty))
            else -> -1
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
            // Tell the bot about its own completed round (human move + bot reply)
            if (currentGame?.status == Game.GameStatus.PLAYING) {
                currentGame?.let { notifyLLMMove(game.gameType, "bot", move, it) }
            }
        }
    }

    private fun makeBotMoveConnectFour() {
        val game = currentGame ?: return
        val board = game.board as? Game.ConnectFourBoard ?: return
        if (board.isFull() || ConnectFourEngine.checkWinner(board) != null) return

        // The engine returns a drop position (row * 7 + col).
        val move = ConnectFourEngine.getBestMove(board, jealousyTracker.getAdaptiveDifficulty(difficulty))
        if (move < 0 || !board.set(move, Game.Player.BOT)) return

        val newMove = Game.Move(Game.Player.BOT, move)
        val newHistory = game.moveHistory + newMove
        val winner = ConnectFourEngine.checkWinner(board)

        when {
            winner != null -> {
                currentGame = game.copy(
                    board = board,
                    currentPlayer = Game.Player.HUMAN,
                    status = if (winner == Game.Player.HUMAN) Game.GameStatus.HUMAN_WON else Game.GameStatus.BOT_WON,
                    winner = winner,
                    moveHistory = newHistory
                )
                triggerReaction(winner == Game.Player.HUMAN)
                awardGamePoints(game.gameType, winner == Game.Player.HUMAN, if (winner == Game.Player.HUMAN) 300 else 50)
                jealousyTracker.recordGameResult(winner == Game.Player.HUMAN, game.gameType!!)
            }
            board.isFull() -> {
                currentGame = game.copy(
                    board = board,
                    currentPlayer = Game.Player.HUMAN,
                    status = Game.GameStatus.DRAW,
                    moveHistory = newHistory
                )
                triggerReaction(false, isDraw = true)
                awardGamePoints(game.gameType, false, 100)
                jealousyTracker.recordGameResult(false, game.gameType!!)
            }
            else -> currentGame = game.copy(board = board, currentPlayer = Game.Player.HUMAN, moveHistory = newHistory)
        }
        onGameStateChanged?.invoke(currentGame)
        if (currentGame?.status == Game.GameStatus.PLAYING) {
            currentGame?.let { notifyLLMMove(game.gameType, "bot", move, it) }
        }
    }

    private fun triggerClickerReaction(score: Int) {
        val character = characterManager.getCurrentCharacter()
        val personality = character?.personality ?: "friendly"
        val characterId = character?.id ?: "marin_kitagawa"

        val prompt = "The human has mindlessly tapped the screen $score times. Tease them playfully about wasting time — stay kind, no insults. Under 15 words in $personality style."

        CoroutineScope(Dispatchers.IO).launch {
            val raw = llmRouter.generate(
                characterId = characterId,
                gameContext = "Game: CLICKER, Score: $score",
                userPrompt = prompt,
                temperature = 0.7f,
                maxTokens = 64
            )
            val text = ToneGuard.sanitize(BanglishTranslator.translate(raw, personality))
            if (text.isNotBlank()) {
                gameReactions.tryEmit(GameReaction(text, false, false))
            }
        }
    }

     private fun notifyLLMMove(
        gameType: Game.GameType,
        player: String,
        position: Int,
        gameState: Game.State,
        detail: String? = null
    ) {
        val character = characterManager.getCurrentCharacter()
        val characterName = character?.name ?: "Marin"
        val personality = character?.personality ?: "friendly"
        val mood = jealousyTracker.getBotMood()
        val jealousy = jealousyTracker.getJealousyLevel()
        val anger = jealousyTracker.getAngerLevel()

        val moveDesc = describeMove(gameType, player, position, gameState, detail)
        val boardDesc = describeBoard(gameState)

        val prompt = if (player == "bot") {
            // Marin is announcing HER OWN move — first-person, playful
            """
                Game: ${gameType.name}
                You ($characterName) just played: $moveDesc
                Board: $boardDesc
                Bot wins: ${jealousyTracker.getBotWins()}, Player wins: ${jealousyTracker.getPlayerWins()}
                Mood: ${mood.name}

                Announce YOUR move in first person, in a ${competitiveTone(anger, jealousy)} way. Under 18 words. Sound natural, NOT robotic.
                Example: "Hehe, I placed right there~ bet you didn't see that coming! 😏"
            """.trimIndent()
        } else {
            // Reacting to the player's move
            """
                Game: ${gameType.name}
                Player: $player $moveDesc
                Board: $boardDesc
                Mood: ${mood.name} (Jealousy: ${(jealousy * 100).toInt()}%, Anger: ${(anger * 100).toInt()}%)

                React to this move as $characterName. Be ${competitiveTone(anger, jealousy)}. You may pout or tease, but never insult, threaten or swear. Under 18 words. Don't say 'the player', react naturally.
            """.trimIndent()
        }

        val characterId = character?.id ?: "marin_kitagawa"
        CoroutineScope(Dispatchers.IO).launch {
            val raw = llmRouter.generate(
                characterId = characterId,
                gameContext = "Game: $gameType, Mood: ${mood.name}",
                userPrompt = prompt,
                temperature = 0.85f,
                maxTokens = 96
            )
            val comment = ToneGuard.sanitize(BanglishTranslator.translate(raw, personality))
                .ifBlank { fallbackMoveComment(player) }
            gameComments.tryEmit(comment)
        }
    }

    /**
     * Tone guidance for the model. Deliberately playful: an angry opponent is
     * fine, an abusive one is not. ToneGuard enforces this on the output too.
     */
    private fun competitiveTone(anger: Double, jealousy: Double): String = when {
        anger > 0.6 -> "fierce, pouty and very competitive (but never mean or threatening)"
        jealousy > 0.5 -> "smug and playfully jealous"
        else -> "playful, cat-like"
    }

    private fun fallbackMoveComment(player: String): String =
        if (player == "bot") {
            listOf(
                "Hehe, my turn~ ✨",
                "Watch this move! 😏",
                "Nyahaha, too easy! 🙄",
                "I placed mine~ try to stop me! 😼",
                "Your turn now... if you dare~ 😈"
            ).random()
        } else {
            listOf(
                "Hmm... interesting move 🤔",
                "Hmph, we'll see about that 😤",
                "Oh? You're actually trying! 😏",
                "Bold choice~ let's see if it works 👀",
                "I'm not impressed yet~ 😒"
            ).random()
        }

    private fun triggerReaction(humanWon: Boolean, isDraw: Boolean = false) {
        val character = characterManager.getCurrentCharacter()
        val personality = character?.personality ?: "friendly"
        val jealousy = jealousyTracker.getJealousyLevel()
        val anger = jealousyTracker.getAngerLevel()
        val mood = jealousyTracker.getBotMood()

        // The tracker is updated *after* this call for ending moves, so count the
        // in-flight human win when working out how angry she should be.
        val winStreak = jealousyTracker.consecutivePlayerWins + if (humanWon) 1 else 0

        // Human wins use a curated, escalating Banglish taunt. A 0.5B on-device model
        // cannot be trusted to reliably produce the "angrier every time you win" arc,
        // and these lines are spoken by TTS, so they must be clean and on-message.
        if (humanWon) {
            val sad = anger > 0.75 && winStreak >= 4
            val text = ToneGuard.sanitize(
                if (sad) BotTrashTalk.sadLine(winStreak) else BotTrashTalk.angryLine(winStreak)
            )
            gameReactions.tryEmit(
                GameReaction(
                    text = text,
                    humanWon = true,
                    isDraw = false,
                    mood = if (sad) ReactionMood.SAD else ReactionMood.ANGRY
                )
            )
            return
        }

        val prompt = if (isDraw) {
            "The game ended in a draw. React in character, playfully. Under 20 words. Stay kind — no insults or threats."
        } else {
            "You beat the human at ${gameType?.name?.lowercase()}. You feel ${mood.name.lowercase()}. React in character — smug but friendly. Under 15 words."
        }

        val characterId = character?.id ?: "marin_kitagawa"
        CoroutineScope(Dispatchers.IO).launch {
            val raw = llmRouter.generate(
                characterId = characterId,
                gameContext = "Game: ${gameType?.name}, Human won: $humanWon, Draw: $isDraw, Jealousy: ${(jealousy * 100).toInt()}%, Anger: ${(anger * 100).toInt()}%, Mood: ${mood.name}",
                userPrompt = prompt,
                temperature = 0.8f,
                maxTokens = 96
            )
            val text = ToneGuard.sanitize(BanglishTranslator.translate(raw, personality))
                .ifBlank { if (isDraw) "A draw?! Fine~ next round I win! ✨" else BotTrashTalk.gloatLine() }
            gameReactions.tryEmit(
                GameReaction(
                    text = text,
                    humanWon = false,
                    isDraw = isDraw,
                    mood = if (isDraw) ReactionMood.NEUTRAL else ReactionMood.GLOATING
                )
            )
        }
    }

    private fun fallbackGameOverComment(humanWon: Boolean, isDraw: Boolean, jealousy: Double): String = when {
        isDraw -> "A draw?! No way, next round I'm definitely taking the win!"
        humanWon -> if (jealousy > 0.4) "Kyaa! You totally got me! Hmph, I'm pouting now! 😤" else "No way! Rematch right now~ ✨"
        else -> "Hehe! Look who won! You owe me a cosplay treat! ✨"
    }

    private fun triggerGameReaction(message: String) {
        val character = characterManager.getCurrentCharacter()
        val personality = character?.personality ?: "friendly"
        val characterId = character?.id ?: "marin_kitagawa"

        CoroutineScope(Dispatchers.IO).launch {
            val raw = llmRouter.generate(
                characterId = characterId,
                gameContext = "Game: ${gameType?.name}",
                userPrompt = message,
                temperature = 0.7f,
                maxTokens = 96
            )
            val text = ToneGuard.sanitize(BanglishTranslator.translate(raw, personality))
            if (text.isNotBlank()) {
                gameReactions.tryEmit(GameReaction(text, false, false))
            }
        }
    }

    private fun awardGamePoints(gameType: Game.GameType, won: Boolean, basePoints: Int) {
        pointsManager.awardPoints(basePoints, gameType, won)
    }

    /**
     * Called by the Ping Pong UI each time a point is won. Keeps the authoritative
     * score in the shared game state so wins/points/reactions/adaptive difficulty all
     * behave exactly like the board games.
     */
    fun pingPongPoint(humanScored: Boolean) {
        val game = currentGame ?: return
        if (game.gameType != Game.GameType.PING_PONG) return
        if (game.status != Game.GameStatus.PLAYING) return
        val board = game.board as? Game.PingPongBoard ?: return

        if (humanScored) board.humanScore++ else board.botScore++
        refreshThreat()

        val winner = board.winner
        if (winner != null) {
            currentGame = game.copy(
                board = board,
                currentPlayer = Game.Player.HUMAN,
                status = if (winner == Game.Player.HUMAN) Game.GameStatus.HUMAN_WON else Game.GameStatus.BOT_WON,
                winner = winner
            )
            triggerReaction(winner == Game.Player.HUMAN)
            awardGamePoints(Game.GameType.PING_PONG, winner == Game.Player.HUMAN, if (winner == Game.Player.HUMAN) 250 else 40)
            jealousyTracker.recordGameResult(winner == Game.Player.HUMAN, Game.GameType.PING_PONG)
        } else {
            currentGame = game.copy(board = board)
            gameComments.tryEmit(BotTrashTalk.rallyLine(board.humanScore, board.botScore, humanScored))
        }
        onGameStateChanged?.invoke(currentGame)
    }

    /**
     * Ends the clicker round — either the target was reached or the UI timer expired.
     * One payout for the whole round: coins only when the target was met.
     */
    fun finishClicker(): Result {
        val game = currentGame ?: return Result(false, "No active game")
        if (game.gameType != Game.GameType.CLICKER) return Result(false, "Not a clicker game")
        if (game.status != Game.GameStatus.PLAYING) return Result(false, "Game over")
        val board = game.board as? Game.ClickerBoard ?: return Result(false, "Invalid board")

        board.finished = true
        val won = board.score >= board.target
        currentGame = game.copy(
            board = board,
            status = if (won) Game.GameStatus.HUMAN_WON else Game.GameStatus.BOT_WON,
            winner = if (won) Game.Player.HUMAN else Game.Player.BOT
        )
        triggerReaction(won)
        awardGamePoints(Game.GameType.CLICKER, won, if (won) 120 else 0)
        jealousyTracker.recordGameResult(won, Game.GameType.CLICKER)
        onGameStateChanged?.invoke(currentGame)
        return Result(true, if (won) "Target reached!" else "Time's up!", currentGame)
    }

    // ---- Chess -------------------------------------------------------------------
    /**
     * Human chess move, given from/to squares (0..63) and an optional promotion
     * piece ('Q'/'R'/'B'/'N'). Legality is enforced by [ChessEngine]; the running
     * game state, win/draw detection, points and reactions flow through the same
     * plumbing as every other game. Marin answers on a short delay.
     */
    fun chessHumanMove(from: Int, to: Int, promotion: Char = ' '): Result {
        val game = currentGame ?: return Result(false, "No active game")
        if (game.gameType != Game.GameType.CHESS) return Result(false, "Chess is not the active game")
        if (game.status != Game.GameStatus.PLAYING) return Result(false, "Game over")
        if (game.currentPlayer != Game.Player.HUMAN) return Result(false, "Not your turn")
        val board = game.board as? ChessBoard ?: return Result(false, "Invalid board")

        val candidates = ChessEngine.legalMoves(board.position).filter { it.from == from && it.to == to }
        if (candidates.isEmpty()) return Result(false, "Illegal move")
        val move = if (promotion == ' ') candidates.first()
        else candidates.firstOrNull { it.promotion.equals(promotion, ignoreCase = true) } ?: candidates.first()

        return applyChessMove(game, board, move, Game.Player.HUMAN)
    }

    private fun makeBotMoveChess() {
        val game = currentGame ?: return
        if (game.gameType != Game.GameType.CHESS) return
        if (game.status != Game.GameStatus.PLAYING) return
        val board = game.board as? ChessBoard ?: return

        val move = ChessEngine.bestMove(board.position, jealousyTracker.getAdaptiveDifficulty(difficulty))
            ?: run {
                finishChess(game, board, winner = null, reason = "Stalemate")
                return
            }
        applyChessMove(game, board, move, Game.Player.BOT)
    }

    private fun applyChessMove(
        game: Game.State,
        board: ChessBoard,
        move: ChessEngine.ChessMove,
        mover: Game.Player
    ): Result {
        board.applyPosition(ChessEngine.makeMove(board.position, move))
        if (mover == Game.Player.HUMAN) refreshThreat()
        val encoded = move.from * 64 + move.to
        val newHistory = game.moveHistory + Game.Move(mover, encoded)

        return when (ChessEngine.status(board.position)) {
            ChessEngine.ChessStatus.CHECKMATE -> {
                val humanWon = mover == Game.Player.HUMAN
                finishChess(game, board, winner = if (humanWon) Game.Player.HUMAN else Game.Player.BOT,
                    reason = if (humanWon) "You checkmated Marin!" else "Marin checkmated you!")
            }
            ChessEngine.ChessStatus.STALEMATE -> finishChess(game, board, winner = null, reason = "Stalemate")
            ChessEngine.ChessStatus.DRAW -> finishChess(game, board, winner = null, reason = "Draw")
            else -> {
                // Hand the turn to the opponent.
                val next = if (mover == Game.Player.HUMAN) Game.Player.BOT else Game.Player.HUMAN
                currentGame = game.copy(board = board, currentPlayer = next, moveHistory = newHistory)
                onGameStateChanged?.invoke(currentGame)

                if (next == Game.Player.BOT) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            kotlinx.coroutines.delay(600)
                            makeBotMoveChess()
                        } catch (e: Exception) {
                            android.util.Log.e("GameManager", "Chess bot move failed", e)
                        }
                    }
                } else {
                    // Marin just replied: one LLM reaction per full round, like Tic-Tac-Toe.
                    currentGame?.let { notifyLLMMove(Game.GameType.CHESS, "bot", encoded, it) }
                }
                Result(true, "Move played", currentGame)
            }
        }
    }

    private fun finishChess(game: Game.State, board: ChessBoard, winner: Game.Player?, reason: String): Result {
        board.gameOver = true
        board.result = reason
        val status = when (winner) {
            Game.Player.HUMAN -> Game.GameStatus.HUMAN_WON
            Game.Player.BOT -> Game.GameStatus.BOT_WON
            else -> Game.GameStatus.DRAW
        }
        currentGame = game.copy(
            board = board,
            currentPlayer = Game.Player.HUMAN,
            status = status,
            winner = winner
        )
        triggerReaction(winner == Game.Player.HUMAN, isDraw = winner == null)
        awardGamePoints(Game.GameType.CHESS, winner == Game.Player.HUMAN, if (winner == Game.Player.HUMAN) 500 else 60)
        jealousyTracker.recordGameResult(winner == Game.Player.HUMAN, Game.GameType.CHESS)
        onGameStateChanged?.invoke(currentGame)
        return Result(true, reason, currentGame)
    }

    // ---- Escalating-anger readouts for the game UI banner -------------------------

    /** 0.0 .. 1.0 — drives the "Marin is RAGING" banner. */
    fun getAngerLevel(): Double = jealousyTracker.getAngerLevel()

    /** How close the human is to winning right now, 0..1 (the "threat map" value). */
    fun getThreatLevel(): Double = jealousyTracker.getThreatLevel()

    /** Human-readable threat label, e.g. "ONE MOVE FROM WINNING". */
    fun getClosenessLabel(): String = jealousyTracker.getClosenessLabel()

    /** Read-only snapshot of the bot's live competitive state, for the stats screen. */
    fun getBotStatus(): BotStatus = BotStatus(
        botWins = jealousyTracker.getBotWins(),
        playerWins = jealousyTracker.getPlayerWins(),
        totalGames = jealousyTracker.getTotalGames(),
        jealousy = jealousyTracker.getJealousyLevel(),
        anger = jealousyTracker.getAngerLevel(),
        threat = jealousyTracker.getThreatLevel(),
        mood = jealousyTracker.getBotMood().name,
        difficulty = jealousyTracker.getAdaptiveDifficulty(difficulty).name
    )

    /** Bot side of the head-to-head record, shown next to the human's stats. */
    data class BotStatus(
        val botWins: Int,
        val playerWins: Int,
        val totalGames: Int,
        val jealousy: Double,
        val anger: Double,
        val threat: Double,
        val mood: String,
        val difficulty: String
    )

    /** Recomputes the live board threat and feeds it to the bot's mood model. */
    private fun refreshThreat() {
        jealousyTracker.reportBoardThreat(boardThreat())
    }

    /**
     * Estimates how close the human is to winning on the current board, in 0..1.
     * Deterministic: the same board always yields the same value. Only board games with a
     * spatial win condition (plus the progress-based mini-games) contribute.
     */
    private fun boardThreat(): Double {
        val board = currentGame?.board ?: return 0.0
        return when (board) {
            is Game.TicTacToeBoard -> gridThreat(board.size, rows = 3, cols = 3, run = 3) { board.get(it) }
            is Game.TicTacToe4x4Board -> gridThreat(board.size, rows = 4, cols = 4, run = 4) { board.get(it) }
            is Game.ConnectFourBoard -> gridThreat(board.size, rows = 6, cols = 7, run = 4) { board.get(it) }
            is Game.PingPongBoard -> board.humanScore.toDouble() / board.targetScore.coerceAtLeast(1)
            is Game.MemoryMatchBoard -> board.matchedPairs.size.toDouble() / board.pairs.coerceAtLeast(1)
            is Game.MathQuizBoard -> board.correctAnswers.toDouble() / 10.0
            is Game.NumberBaseBoard -> board.correctAnswers.toDouble() / 10.0
            is Game.GuessNumberBoard ->
                if (board.lastResult == Game.GuessNumberBoard.GuessResult.CORRECT) 1.0 else 0.0
            else -> 0.0
        }.coerceIn(0.0, 1.0)
    }

    /**
     * Biggest count of human pieces in any uncontested window of [run] cells, divided by
     * [run]. A window containing a bot piece is ignored (it can never become a human win).
     */
    private fun gridThreat(
        size: Int,
        rows: Int,
        cols: Int,
        run: Int,
        cell: (Int) -> Game.Player?
    ): Double {
        if (rows * cols != size || run < 2) return 0.0
        var best = 0
        fun score(window: List<Int>) {
            var human = 0
            var bot = 0
            for (i in window) {
                when (cell(i)) {
                    Game.Player.HUMAN -> human++
                    Game.Player.BOT -> bot++
                    null -> Unit
                }
            }
            if (bot == 0) best = maxOf(best, human)
        }
        // Rows
        for (r in 0 until rows) for (c in 0..cols - run) score((0 until run).map { r * cols + c + it })
        // Columns
        for (c in 0 until cols) for (r in 0..rows - run) score((0 until run).map { (r + it) * cols + c })
        // Diagonals ↘
        for (r in 0..rows - run) for (c in 0..cols - run) score((0 until run).map { (r + it) * cols + c + it })
        // Diagonals ↙
        for (r in 0..rows - run) for (c in run - 1 until cols) score((0 until run).map { (r + it) * cols + c - it })
        return (best.toDouble() / run).coerceIn(0.0, 1.0)
    }

    fun getHumanWinStreak(): Int = jealousyTracker.consecutivePlayerWins

    /** "CALM" .. "FURIOUS". */
    fun getMoodRank(): String = BotTrashTalk.rankLabel(jealousyTracker.getAngerLevel())

    /** The difficulty the bot is *actually* playing at right now (post-escalation). */
    fun getAdaptiveDifficultyName(): String =
        jealousyTracker.getAdaptiveDifficulty(difficulty).name.lowercase()

    fun isCheatingMindActive(): Boolean = jealousyTracker.isCheatingEnabled()

    // Callback for state changes that happen off the calling thread (bot moves)
    var onGameStateChanged: ((Game.State?) -> Unit)? = null

    fun getCurrentState(): Game.State? = currentGame

    data class Result(
        val success: Boolean,
        val message: String,
        val state: Game.State? = null
    )
}

/**
 * Human-readable description of a move for the bot's LLM prompt.
 * Covers every [Game.GameType] so the bot always knows exactly what happened on the board.
 *
 * @param player "human" or "bot"
 * @param detail extra text for games without board positions (typed answers, color guesses)
 */
internal fun describeMove(
    gameType: Game.GameType,
    player: String,
    position: Int,
    gameState: Game.State,
    detail: String? = null
): String = when (gameType) {
    Game.GameType.TIC_TAC_TOE ->
        "placed ${if (player == "bot") "O" else "X"} at cell ${position + 1}"
    Game.GameType.TIC_TAC_TOE_4X4 ->
        "placed ${if (player == "bot") "O" else "X"} at cell ${position + 1} of the 4x4 board"
    Game.GameType.CHESS ->
        "moved ${ChessEngine.squareName(position / 64)} to ${ChessEngine.squareName(position % 64)}"
    Game.GameType.ROCK_PAPER_SCISSORS ->
        "chose ${when (position) { 0 -> "Rock"; 1 -> "Paper"; 2 -> "Scissors"; else -> "Unknown" }}"
    Game.GameType.GUESS_NUMBER -> "guessed ${position + 1}"
    Game.GameType.MATH_QUIZ -> "answered $position"
    Game.GameType.CLICKER -> "tapped the cupcake (score: ${(gameState.board as? Game.ClickerBoard)?.score})"
    Game.GameType.MEMORY_MATCH -> "flipped card ${position + 1}"
    Game.GameType.HANGMAN -> "guessed the letter '${'A' + position}'"
    Game.GameType.WORD_SCRAMBLE -> "answered \"${detail ?: "?"}\""
    Game.GameType.NUMBER_BASE -> "answered \"${detail ?: "?"}\" (in base conversion)"
    Game.GameType.REACTION_TIME -> "tapped (reaction round)"
    Game.GameType.CONNECT_FOUR -> "dropped a disc in column ${position % 7 + 1}"
    Game.GameType.MASTERMIND -> "guessed ${detail ?: position}"
    Game.GameType.PING_PONG ->
        "rallied (score You ${(gameState.board as? Game.PingPongBoard)?.humanScore} - ${(gameState.board as? Game.PingPongBoard)?.botScore} Marin)"
}

/**
 * Compact, human-readable snapshot of the live board for the bot's prompt.
 * The bot has no memory of the board, so every move message carries the full
 * position — otherwise it reacts to moves it cannot actually see.
 */
internal fun describeBoard(gameState: Game.State): String = when (val board = gameState.board) {
    is Game.GuessNumberBoard ->
        "guesses=${board.guesses}"
    is Game.TicTacToeBoard ->
        board.cells.joinToString("") { it?.symbol ?: "." }
    is Game.TicTacToe4x4Board ->
        "[" + (0..3).joinToString("/") { r -> (0..3).joinToString("") { c -> board.get(r * 4 + c)?.symbol ?: "." } } + "]"
    is Game.ConnectFourBoard ->
        "[" + (0..5).joinToString("/") { r -> (0..6).joinToString("") { c -> board.get(r * 7 + c)?.symbol ?: "." } } + "]"
    is Game.MemoryMatchBoard ->
        "pairs=${board.matchedPairs.size}/${board.pairs} moves=${board.moves}"
    is Game.HangmanBoard ->
        "${board.getDisplayWord()} (misses ${board.wrongGuesses}/${board.maxWrongGuesses})"
    is Game.WordScrambleBoard ->
        "scrambled=\"${board.scrambledWord}\" attempts=${board.attempts}/${board.maxAttempts}"
    is Game.NumberBaseBoard ->
        board.currentQuestion?.let { "convert ${it.number} from base ${it.fromBase} to base ${it.toBase}" } ?: "finished"
    is Game.MastermindBoard ->
        if (board.guesses.isEmpty()) "no guesses yet"
        else board.guesses.takeLast(3).joinToString("; ") { "${it.guess}=>exact${it.exactMatches},color${it.colorMatches}" }
    is Game.ReactionTimeBoard ->
        "rounds=${board.rounds.size}"
    is Game.MathQuizBoard ->
        board.currentQuestion?.let { "${it.expression} = ?" } ?: "finished"
    is Game.RpsBoard ->
        "human=${board.humanChoice ?: "-"} bot=${board.botChoice ?: "-"}"
    is Game.ClickerBoard ->
        "score=${board.score}"
    is ChessBoard ->
        board.currentFen
    else -> ""
}
