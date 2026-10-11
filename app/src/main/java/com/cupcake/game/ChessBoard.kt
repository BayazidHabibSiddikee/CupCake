package com.cupcake.game

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
