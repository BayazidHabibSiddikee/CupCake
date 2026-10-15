package com.cupcake.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Regression tests for the shallow-copy bug that made every look-ahead AI
 * corrupt the live board. A board copy must be fully independent: mutating the
 * copy must never touch the original.
 */
class BoardCopyTest {

    @Test
    fun `tic tac toe copy is independent`() {
        val board = Game.TicTacToeBoard()
        board.set(0, Game.Player.HUMAN)

        val copy = board.copy()
        copy.set(4, Game.Player.BOT)
        copy.cells[0] = Game.Player.BOT

        assertThat(board.get(0)).isEqualTo(Game.Player.HUMAN)
        assertThat(board.get(4)).isNull()
    }

    @Test
    fun `connect four copy is independent`() {
        val board = Game.ConnectFourBoard()
        board.set(40, Game.Player.HUMAN)

        val copy = board.copy()
        copy.set(41, Game.Player.BOT)

        assertThat(board.get(40)).isEqualTo(Game.Player.HUMAN)
        assertThat(board.get(41)).isNull()
    }

    @Test
    fun `four by four copy is independent`() {
        val board = Game.TicTacToe4x4Board()
        board.set(5, Game.Player.HUMAN)

        val copy = board.copy()
        copy.set(6, Game.Player.BOT)

        assertThat(board.get(5)).isEqualTo(Game.Player.HUMAN)
        assertThat(board.get(6)).isNull()
    }

    @Test
    fun `copies compare by value and differ by content`() {
        val a = Game.TicTacToeBoard()
        a.set(0, Game.Player.HUMAN)
        val b = a.copy()
        assertThat(b).isEqualTo(a)

        b.set(1, Game.Player.BOT)
        assertThat(b).isNotEqualTo(a)
    }

    @Test
    fun `connect four engine wins when it can`() {
        val board = Game.ConnectFourBoard()
        // Bot already owns the bottom three of column 0.
        board.set(35, Game.Player.BOT)
        board.set(28, Game.Player.BOT)
        board.set(21, Game.Player.BOT)

        val move = ConnectFourEngine.getBestMove(board, TicTacToeEngine.Difficulty.HARD)
        assertThat(move).isEqualTo(14) // next empty cell in column 0
    }

    @Test
    fun `connect four engine blocks a human win`() {
        val board = Game.ConnectFourBoard()
        board.set(38, Game.Player.HUMAN)
        board.set(31, Game.Player.HUMAN)
        board.set(24, Game.Player.HUMAN)

        val move = ConnectFourEngine.getBestMove(board, TicTacToeEngine.Difficulty.HARD)
        assertThat(move).isEqualTo(17) // next empty cell in column 3
    }

    @Test
    fun `rock paper scissors learns repeated patterns`() {
        RockPaperScissorsEngine.reset()
        var lossesToBot = 0
        // Player always follows Rock -> Paper; the opponent should learn to
        // counter Paper with Scissors.
        repeat(20) {
            val human = if (it == 0) 0 else 1
            val bot = RockPaperScissorsEngine.getBotMove(human)
            if (bot == 2) lossesToBot++
        }
        assertThat(lossesToBot).isGreaterThan(0)
    }
}
