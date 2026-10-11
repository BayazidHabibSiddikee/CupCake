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
        val cells: Array<Player?> = Array(16) { null }
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
