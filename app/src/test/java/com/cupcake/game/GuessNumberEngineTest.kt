package com.cupcake.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GuessNumberEngineTest {

    private fun boardOf(target: Int, vararg guesses: Int): Game.GuessNumberBoard {
        val board = Game.GuessNumberBoard(targetNumber = target)
        guesses.forEach { board.guesses.add(it) }
        if (guesses.isNotEmpty()) {
            board.lastGuess = guesses.last()
            board.lastResult = evaluateLastGuess(board)
        }
        return board
    }

    private fun evaluateLastGuess(board: Game.GuessNumberBoard): Game.GuessNumberBoard.GuessResult {
        val lastGuess = board.lastGuess!!
        return when {
            lastGuess < board.targetNumber -> Game.GuessNumberBoard.GuessResult.TOO_LOW
            lastGuess > board.targetNumber -> Game.GuessNumberBoard.GuessResult.TOO_HIGH
            else -> Game.GuessNumberBoard.GuessResult.CORRECT
        }
    }

    @Test
    fun `evaluates correct guess`() {
        val board = Game.GuessNumberBoard(targetNumber = 50)
        val result = GuessNumberEngine.evaluateGuess(board, 50)
        assertThat(result).isEqualTo(Game.GuessNumberBoard.GuessResult.CORRECT)
    }

    @Test
    fun `evaluates too high guess`() {
        val board = Game.GuessNumberBoard(targetNumber = 50)
        val result = GuessNumberEngine.evaluateGuess(board, 75)
        assertThat(result).isEqualTo(Game.GuessNumberBoard.GuessResult.TOO_HIGH)
    }

    @Test
    fun `evaluates too low guess`() {
        val board = Game.GuessNumberBoard(targetNumber = 50)
        val result = GuessNumberEngine.evaluateGuess(board, 25)
        assertThat(result).isEqualTo(Game.GuessNumberBoard.GuessResult.TOO_LOW)
    }

    @Test
    fun `hint shows initial message`() {
        val board = Game.GuessNumberBoard(targetNumber = 50)
        val hint = GuessNumberEngine.getHint(board)
        assertThat(hint).contains("1 and 100")
        assertThat(hint).contains("10 guesses")
    }

    @Test
    fun `hint shows too high feedback`() {
        val board = boardOf(50, 75)
        val hint = GuessNumberEngine.getHint(board)
        assertThat(hint).contains("Too high")
        assertThat(hint).contains("1-74")
    }

    @Test
    fun `hint shows too low feedback`() {
        val board = boardOf(50, 25)
        val hint = GuessNumberEngine.getHint(board)
        assertThat(hint).contains("Too low")
        assertThat(hint).contains("26-100")
    }

    @Test
    fun `hint shows correct feedback`() {
        val board = boardOf(50, 50)
        val hint = GuessNumberEngine.getHint(board)
        assertThat(hint).contains("You got it")
        assertThat(hint).contains("50")
    }
}