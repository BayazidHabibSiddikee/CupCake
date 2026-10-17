package com.cupcake.game

/**
 * Chess board state for [Game.State]. Thin wrapper around [ChessEngine.ChessPosition]
 * so the generic game plumbing (win detection, reactions, move history) works the
 * same as every other game. Move legality lives in [ChessEngine].
 */
class ChessBoard(startFen: String = ChessEngine.START_FEN) : Game.Board {

    var position: ChessEngine.ChessPosition = ChessEngine.parseFen(startFen)
        private set

    var gameOver: Boolean = false
    var result: String? = null

    override val size: Int = 64
    override fun get(position: Int): Game.Player? = null // chess state is read via [pieceAt]
    override fun set(position: Int, player: Game.Player): Boolean = false
    override fun isFull(): Boolean = gameOver

    val currentFen: String get() = ChessEngine.toFen(position)

    fun pieceAt(square: Int): Char = position.squares[square]

    fun applyPosition(next: ChessEngine.ChessPosition) {
        position = next
    }
}
