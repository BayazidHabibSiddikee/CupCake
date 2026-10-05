package com.cupcake.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TicTacToeEngineTest {

    private fun boardOf(vararg cells: Game.Player?): Game.TicTacToeBoard {
        val board = Game.TicTacToeBoard()
        cells.forEachIndexed { i, player ->
            if (player != null) board.set(i, player)
        }
        return board
    }

    @Test
    fun `detects row win for human`() {
        val board = boardOf(
            Game.Player.HUMAN, Game.Player.HUMAN, Game.Player.HUMAN,
            null, null, null,
            null, null, null
        )
        assertThat(TicTacToeEngine.checkWinner(board)).isEqualTo(Game.Player.HUMAN)
    }

    @Test
    fun `detects diagonal win for bot`() {
        val board = boardOf(
            Game.Player.BOT, null, null,
            null, Game.Player.BOT, null,
            null, null, Game.Player.BOT
        )
        assertThat(TicTacToeEngine.checkWinner(board)).isEqualTo(Game.Player.BOT)
    }

    @Test
    fun `returns null when no winner`() {
        val board = boardOf(
            Game.Player.HUMAN, null, null,
            null, Game.Player.BOT, null,
            null, null, null
        )
        assertThat(TicTacToeEngine.checkWinner(board)).isNull()
    }

    @Test
    fun `smart move blocks human win`() {
        val board = boardOf(
            Game.Player.HUMAN, Game.Player.HUMAN, null,
            null, Game.Player.BOT, null,
            null, null, null
        )
        val move = TicTacToeEngine.getBestMove(board, TicTacToeEngine.Difficulty.NORMAL)
        assertThat(move).isEqualTo(2)
    }

    @Test
    fun `smart move takes winning move`() {
        val board = boardOf(
            Game.Player.BOT, Game.Player.BOT, null,
            Game.Player.HUMAN, Game.Player.HUMAN, null,
            null, null, null
        )
        val move = TicTacToeEngine.getBestMove(board, TicTacToeEngine.Difficulty.NORMAL)
        assertThat(move).isEqualTo(2)
    }

    @Test
    fun `random move returns empty cell`() {
        val board = boardOf(
            Game.Player.HUMAN, Game.Player.BOT, Game.Player.HUMAN,
            Game.Player.BOT, Game.Player.HUMAN, Game.Player.BOT,
            Game.Player.HUMAN, Game.Player.BOT, null
        )
        val move = TicTacToeEngine.getBestMove(board, TicTacToeEngine.Difficulty.EASY)
        assertThat(move).isEqualTo(8)
    }

    @Test
    fun `full board reports no moves`() {
        val board = boardOf(
            Game.Player.HUMAN, Game.Player.BOT, Game.Player.HUMAN,
            Game.Player.HUMAN, Game.Player.BOT, Game.Player.BOT,
            Game.Player.BOT, Game.Player.HUMAN, Game.Player.HUMAN
        )
        assertThat(board.isFull()).isTrue()
        assertThat(TicTacToeEngine.getBestMove(board, TicTacToeEngine.Difficulty.EASY))
            .isEqualTo(-1)
    }
}
