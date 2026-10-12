package com.cupcake.game

import com.cupcake.ai.CharacterManager
import com.cupcake.ai.OllamaEngine
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class GameManagerGamesTest {

    private lateinit var gameManager: GameManager
    private lateinit var pointsManager: PointsManager

    @Before
    fun setUp() {
        gameManager = GameManager(
            mockk<OllamaEngine>(relaxed = true),
            CharacterManager,
            PointsManager().also { pointsManager = it }
        )
    }

    // ------------------------------------------
    // Word Scramble
    // ------------------------------------------

    @Test
    fun `word scramble correct answer wins`() {
        gameManager.startGame(Game.GameType.WORD_SCRAMBLE)
        val board = gameManager.getCurrentState()!!.board as Game.WordScrambleBoard

        val result = gameManager.submitTextAnswer(board.originalWord)

        assertThat(result.success).isTrue()
        assertThat(gameManager.getCurrentState()!!.status).isEqualTo(Game.GameStatus.HUMAN_WON)
        assertThat(board.originalWord).isNotEmpty()
    }

    @Test
    fun `word scramble wrong answers lose after max attempts`() {
        gameManager.startGame(Game.GameType.WORD_SCRAMBLE)

        repeat(3) { gameManager.submitTextAnswer("ZZZZZZZZ") }

        val state = gameManager.getCurrentState()!!
        val board = state.board as Game.WordScrambleBoard
        assertThat(board.attempts).isEqualTo(3)
        assertThat(state.status).isEqualTo(Game.GameStatus.BOT_WON)
    }

    @Test
    fun `text answers rejected for games that do not use them`() {
        gameManager.startGame(Game.GameType.TIC_TAC_TOE)
        val result = gameManager.submitTextAnswer("HELLO")
        assertThat(result.success).isFalse()
    }

    // ------------------------------------------
    // Number Base
    // ------------------------------------------

    @Test
    fun `number base ten correct answers wins`() {
        gameManager.startGame(Game.GameType.NUMBER_BASE)

        repeat(10) {
            val board = gameManager.getCurrentState()!!.board as Game.NumberBaseBoard
            val question = board.currentQuestion
            assertThat(question).isNotNull()
            gameManager.submitTextAnswer(question!!.correctAnswer)
        }

        val state = gameManager.getCurrentState()!!
        val board = state.board as Game.NumberBaseBoard
        assertThat(board.questionsAnswered).isEqualTo(10)
        assertThat(board.correctAnswers).isEqualTo(10)
        assertThat(state.status).isEqualTo(Game.GameStatus.HUMAN_WON)
    }

    @Test
    fun `number base all wrong answers loses`() {
        gameManager.startGame(Game.GameType.NUMBER_BASE)

        repeat(10) { gameManager.submitTextAnswer("NOT_A_NUMBER!!") }

        val state = gameManager.getCurrentState()!!
        val board = state.board as Game.NumberBaseBoard
        assertThat(board.correctAnswers).isEqualTo(0)
        assertThat(state.status).isEqualTo(Game.GameStatus.BOT_WON)
    }

    // ------------------------------------------
    // Mastermind
    // ------------------------------------------

    @Test
    fun `mastermind exact code guess wins`() {
        gameManager.startGame(Game.GameType.MASTERMIND)
        val board = gameManager.getCurrentState()!!.board as Game.MastermindBoard

        val result = gameManager.submitMastermindGuess(board.code)

        assertThat(result.success).isTrue()
        val state = gameManager.getCurrentState()!!
        assertThat(state.status).isEqualTo(Game.GameStatus.HUMAN_WON)
        assertThat((state.board as Game.MastermindBoard).guesses).hasSize(1)
    }

    @Test
    fun `mastermind rejects malformed guess`() {
        gameManager.startGame(Game.GameType.MASTERMIND)

        assertThat(gameManager.submitMastermindGuess(listOf(0, 1)).success).isFalse()
        assertThat(gameManager.submitMastermindGuess(listOf(0, 1, 2, 9)).success).isFalse()
        assertThat(gameManager.submitMastermindGuess(emptyList()).success).isFalse()
    }

    @Test
    fun `mastermind wrong guess records feedback and keeps playing`() {
        gameManager.startGame(Game.GameType.MASTERMIND)
        val board = gameManager.getCurrentState()!!.board as Game.MastermindBoard
        val wrongGuess = board.code.map { (it + 1) % 6 }

        val result = gameManager.submitMastermindGuess(wrongGuess)

        assertThat(result.success).isTrue()
        val state = gameManager.getCurrentState()!!
        assertThat(state.status).isEqualTo(Game.GameStatus.PLAYING)
        val newBoard = state.board as Game.MastermindBoard
        assertThat(newBoard.guesses).hasSize(1)
        assertThat(newBoard.guesses[0].exactMatches).isLessThan(4)
    }

    // ------------------------------------------
    // Memory Match
    // ------------------------------------------

    @Test
    fun `memory mismatch blocks moves then flips cards back`() {
        gameManager.startGame(Game.GameType.MEMORY_MATCH)
        val board = gameManager.getCurrentState()!!.board as Game.MemoryMatchBoard
        val mismatched = findMismatchPair(board)

        gameManager.makeHumanMove(mismatched.first)
        gameManager.makeHumanMove(mismatched.second)

        // While the pair is showing, further moves are rejected
        val during = gameManager.getCurrentState()!!.board as Game.MemoryMatchBoard
        assertThat(during.cards.count { it.isFlipped && !it.isMatched }).isEqualTo(2)
        val blocked = gameManager.makeHumanMove(mismatched.third)
        assertThat(blocked.success).isFalse()

        // After the delay the cards flip back down
        val after = waitForMemoryUnflip()
        assertThat(after.cards.count { it.isFlipped && !it.isMatched }).isEqualTo(0)
        assertThat(after.cards[mismatched.first].isFlipped).isFalse()
        assertThat(after.cards[mismatched.second].isFlipped).isFalse()
    }

    @Test
    fun `memory matching pair stays revealed`() {
        gameManager.startGame(Game.GameType.MEMORY_MATCH)
        val board = gameManager.getCurrentState()!!.board as Game.MemoryMatchBoard
        val (first, second) = findMatchingPair(board)

        gameManager.makeHumanMove(first)
        gameManager.makeHumanMove(second)

        Thread.sleep(1500) // longer than the mismatch unflip delay
        val after = gameManager.getCurrentState()!!.board as Game.MemoryMatchBoard
        assertThat(after.cards[first].isMatched).isTrue()
        assertThat(after.cards[second].isMatched).isTrue()
    }

    // ------------------------------------------
    // Helpers
    // ------------------------------------------

    private fun findMismatchPair(board: Game.MemoryMatchBoard): Triple<Int, Int, Int> {
        for (i in board.cards.indices) {
            for (j in i + 1 until board.cards.size) {
                if (board.cards[i].value != board.cards[j].value) {
                    val other = board.cards.indices.first { it != i && it != j }
                    return Triple(i, j, other)
                }
            }
        }
        throw IllegalStateException("Board has no mismatched pair")
    }

    private fun findMatchingPair(board: Game.MemoryMatchBoard): Pair<Int, Int> {
        for (i in board.cards.indices) {
            for (j in i + 1 until board.cards.size) {
                if (board.cards[i].value == board.cards[j].value) return i to j
            }
        }
        throw IllegalStateException("Board has no matching pair")
    }

    private fun waitForMemoryUnflip(maxWaitMs: Long = 5000): Game.MemoryMatchBoard {
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            val board = gameManager.getCurrentState()!!.board as Game.MemoryMatchBoard
            if (board.cards.none { it.isFlipped && !it.isMatched }) return board
            Thread.sleep(100)
        }
        return gameManager.getCurrentState()!!.board as Game.MemoryMatchBoard
    }
}