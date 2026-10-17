package com.cupcake.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Perft: counts the legal-move tree to a fixed depth and compares against the
 * published reference values. This is the standard way to prove a move generator
 * handles castling, en passant, promotion, pins and check evasion correctly.
 */
class ChessEngineTest {

    private fun perft(p: ChessEngine.ChessPosition, depth: Int): Long {
        if (depth == 0) return 1
        val moves = ChessEngine.legalMoves(p)
        if (depth == 1) return moves.size.toLong()
        var nodes = 0L
        for (m in moves) nodes += perft(ChessEngine.makeMove(p, m), depth - 1)
        return nodes
    }

    @Test
    fun `initial position perft`() {
        val p = ChessEngine.initial()
        assertThat(perft(p, 1)).isEqualTo(20)
        assertThat(perft(p, 2)).isEqualTo(400)
        assertThat(perft(p, 3)).isEqualTo(8902)
        assertThat(perft(p, 4)).isEqualTo(197281)
    }

    @Test
    fun `kiwipete perft`() {
        val p = ChessEngine.parseFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertThat(perft(p, 1)).isEqualTo(48)
        assertThat(perft(p, 2)).isEqualTo(2039)
        assertThat(perft(p, 3)).isEqualTo(97862)
    }

    @Test
    fun `endgame with en passant perft`() {
        val p = ChessEngine.parseFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertThat(perft(p, 1)).isEqualTo(14)
        assertThat(perft(p, 2)).isEqualTo(191)
        assertThat(perft(p, 3)).isEqualTo(2812)
        assertThat(perft(p, 4)).isEqualTo(43238)
    }

    @Test
    fun `promotions and castling perft`() {
        val p = ChessEngine.parseFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertThat(perft(p, 1)).isEqualTo(6)
        assertThat(perft(p, 2)).isEqualTo(264)
        assertThat(perft(p, 3)).isEqualTo(9467)
    }

    @Test
    fun `fen round trips`() {
        val fen = "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 4 4"
        assertThat(ChessEngine.toFen(ChessEngine.parseFen(fen))).isEqualTo(fen)
    }

    @Test
    fun `promotion generates four options`() {
        val p = ChessEngine.parseFen("8/P6k/8/8/8/8/6K1/8 w - - 0 1")
        val promos = ChessEngine.legalMoves(p).filter { it.from == 8 && it.isPromotion }
        assertThat(promos.map { it.promotion }).containsExactly('Q', 'R', 'B', 'N')
    }

    @Test
    fun `detects checkmate and stalemate`() {
        // Fool's mate — white to move is checkmated.
        val mate = ChessEngine.parseFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")
        assertThat(ChessEngine.status(mate)).isEqualTo(ChessEngine.ChessStatus.CHECKMATE)

        // Classic stalemate: black king a8, white queen c7, white king arbitrary.
        val stale = ChessEngine.parseFen("k7/2Q5/8/8/8/8/8/6K1 b - - 0 1")
        assertThat(ChessEngine.status(stale)).isEqualTo(ChessEngine.ChessStatus.STALEMATE)
    }

    @Test
    fun `ai returns a legal move and prefers capturing a queen`() {
        // White to move can play Rxe8+? Here simply assert legality of the choice.
        val p = ChessEngine.initial()
        val move = ChessEngine.bestMove(p, TicTacToeEngine.Difficulty.NORMAL)
        assertThat(move).isNotNull()
        assertThat(ChessEngine.legalMoves(p)).contains(move)

        // White rook on d1 can capture a hanging black queen on d8.
        val capturePos = ChessEngine.parseFen("3q3k/8/8/8/8/8/8/3R2K1 w - - 0 1")
        val capture = ChessEngine.bestMove(capturePos, TicTacToeEngine.Difficulty.HARD)
        assertThat(capture?.to).isEqualTo(ChessEngine.squareIndex("d8"))
    }
}
