package com.cupcake.game

import kotlin.math.abs
import kotlin.random.Random

/**
 * Complete, self-contained chess engine (no native dependency — there is no
 * Stockfish binary in the project).
 *
 * Board indexing follows FEN order: square 0 = a8 (top-left, black's back rank)
 * through square 63 = h1 (bottom-right, white's). `squares[i]` holds a FEN piece
 * letter ('P','N','B','R','Q','K' white / lowercase black) or '.' for empty.
 *
 * Rules implemented: all piece movement, captures, double pawn push, en passant,
 * castling (with path/attack checks), promotion, check, checkmate, stalemate,
 * insufficient-material and 50-move draws.
 *
 * The AI is negamax with alpha-beta pruning, MVV-LVA move ordering and a
 * material + piece-square-table evaluation. Difficulty maps to search depth and a
 * little randomness, so Marin gets genuinely sharper as her anger escalates.
 */
object ChessEngine {

    const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    private const val MATE = 1_000_000
    private const val EMPTY = '.'

    enum class ChessStatus { PLAYING, CHECK, CHECKMATE, STALEMATE, DRAW }

    // ------------------------------------------------------------------ position

    class ChessPosition(
        val squares: CharArray,
        val whiteToMove: Boolean,
        val castling: String,
        val epSquare: Int,
        val halfmove: Int,
        val fullmove: Int
    ) {
        fun copy(): ChessPosition =
            ChessPosition(squares.copyOf(), whiteToMove, castling, epSquare, halfmove, fullmove)

        fun pieceAt(sq: Int): Char = squares[sq]

        fun kingSquare(white: Boolean): Int {
            val k = if (white) 'K' else 'k'
            for (i in 0 until 64) if (squares[i] == k) return i
            return -1
        }
    }

    // ---------------------------------------------------------------------- move

    /** promoted-to piece letter (uppercase) or ' ' when not a promotion. */
    data class ChessMove(val from: Int, val to: Int, val promotion: Char = ' ') {
        val isPromotion: Boolean get() = promotion != ' '
    }

    // ---------------------------------------------------------------------- FEN

    fun initial(): ChessPosition = parseFen(START_FEN)

    fun parseFen(fen: String): ChessPosition {
        val parts = fen.trim().split(Regex("\\s+"))
        val squares = CharArray(64) { EMPTY }
        val rows = parts[0].split('/')
        for (r in 0 until 8) {
            var file = 0
            for (ch in rows[r]) {
                if (ch.isDigit()) file += ch - '0'
                else { squares[r * 8 + file] = ch; file++ }
            }
        }
        val whiteToMove = parts.getOrNull(1)?.startsWith("w") ?: true
        val castling = parts.getOrNull(2)?.takeUnless { it == "-" } ?: ""
        val epRaw = parts.getOrNull(3) ?: "-"
        val epSquare = if (epRaw == "-" || epRaw.length < 2) -1 else squareIndex(epRaw)
        val halfmove = parts.getOrNull(4)?.toIntOrNull() ?: 0
        val fullmove = parts.getOrNull(5)?.toIntOrNull() ?: 1
        return ChessPosition(squares, whiteToMove, castling, epSquare, halfmove, fullmove)
    }

    fun toFen(p: ChessPosition): String {
        val sb = StringBuilder()
        for (r in 0 until 8) {
            var empty = 0
            for (f in 0 until 8) {
                val c = p.squares[r * 8 + f]
                if (c == EMPTY) empty++ else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(c)
                }
            }
            if (empty > 0) sb.append(empty)
            if (r < 7) sb.append('/')
        }
        sb.append(' ').append(if (p.whiteToMove) 'w' else 'b')
        sb.append(' ').append(p.castling.ifEmpty { "-" })
        sb.append(' ').append(if (p.epSquare >= 0) squareName(p.epSquare) else "-")
        sb.append(' ').append(p.halfmove).append(' ').append(p.fullmove)
        return sb.toString()
    }

    fun squareIndex(name: String): Int {
        if (name.length < 2) return -1
        val f = name[0] - 'a'
        val r = 8 - (name[1] - '0')
        if (f !in 0..7 || r !in 0..7) return -1
        return r * 8 + f
    }

    fun squareName(sq: Int): String = "${'a' + fileOf(sq)}${8 - rankOf(sq)}"

    private fun fileOf(sq: Int) = sq % 8
    private fun rankOf(sq: Int) = sq / 8
    private fun isWhitePiece(c: Char) = c in 'A'..'Z'

    /** Step by (df, dr) from `sq`; -1 when it leaves the board. */
    private fun offset(sq: Int, df: Int, dr: Int): Int {
        val f = fileOf(sq) + df
        val r = rankOf(sq) + dr
        if (f !in 0..7 || r !in 0..7) return -1
        return r * 8 + f
    }

    // ------------------------------------------------------------------ attacks

    fun isInCheck(p: ChessPosition, white: Boolean): Boolean {
        val king = p.kingSquare(white)
        if (king < 0) return false
        return isAttacked(p, king, byWhite = !white)
    }

    /** Is `target` attacked by any piece of the given colour? */
    fun isAttacked(p: ChessPosition, target: Int, byWhite: Boolean): Boolean {
        val eq = if (byWhite) 'P' else 'p'
        val kn = if (byWhite) 'N' else 'n'
        val kg = if (byWhite) 'K' else 'k'
        val bi = if (byWhite) 'B' else 'b'
        val rk = if (byWhite) 'R' else 'r'
        val qu = if (byWhite) 'Q' else 'q'

        // Pawns: a white pawn attacks "upward", so it sits one rank below the target.
        val pawnDr = if (byWhite) 1 else -1
        for (df in intArrayOf(-1, 1)) {
            val s = offset(target, df, pawnDr)
            if (s >= 0 && p.squares[s] == eq) return true
        }
        // Knights
        for ((df, dr) in KNIGHT) {
            val s = offset(target, df, dr)
            if (s >= 0 && p.squares[s] == kn) return true
        }
        // King
        for ((df, dr) in KING) {
            val s = offset(target, df, dr)
            if (s >= 0 && p.squares[s] == kg) return true
        }
        // Sliding: diagonals (bishop/queen)
        for ((df, dr) in DIAG) {
            var s = offset(target, df, dr)
            while (s >= 0) {
                val c = p.squares[s]
                if (c != EMPTY) { if (c == bi || c == qu) return true; break }
                s = offset(s, df, dr)
            }
        }
        // Sliding: orthogonals (rook/queen)
        for ((df, dr) in ORTHO) {
            var s = offset(target, df, dr)
            while (s >= 0) {
                val c = p.squares[s]
                if (c != EMPTY) { if (c == rk || c == qu) return true; break }
                s = offset(s, df, dr)
            }
        }
        return false
    }

    // -------------------------------------------------------------- move gen

    fun legalMoves(p: ChessPosition): List<ChessMove> {
        val moves = pseudoLegalMoves(p)
        val legal = ArrayList<ChessMove>(moves.size)
        val moving = p.whiteToMove
        for (m in moves) {
            val next = makeMoveUnchecked(p, m)
            if (!isInCheck(next, moving)) legal.add(m)
        }
        return legal
    }

    private fun pseudoLegalMoves(p: ChessPosition): List<ChessMove> {
        val moves = ArrayList<ChessMove>(48)
        val white = p.whiteToMove
        for (sq in 0 until 64) {
            val c = p.squares[sq]
            if (c == EMPTY) continue
            if (isWhitePiece(c) != white) continue
            when (c.lowercaseChar()) {
                'p' -> pawnMoves(p, sq, white, moves)
                'n' -> stepMoves(p, sq, white, KNIGHT, moves)
                'k' -> { stepMoves(p, sq, white, KING, moves); castlingMoves(p, sq, white, moves) }
                'b' -> slideMoves(p, sq, white, DIAG, moves)
                'r' -> slideMoves(p, sq, white, ORTHO, moves)
                'q' -> { slideMoves(p, sq, white, DIAG, moves); slideMoves(p, sq, white, ORTHO, moves) }
            }
        }
        return moves
    }

    private fun stepMoves(p: ChessPosition, sq: Int, white: Boolean, deltas: Array<IntArray>, out: MutableList<ChessMove>) {
        for ((df, dr) in deltas) {
            val t = offset(sq, df, dr)
            if (t < 0) continue
            val c = p.squares[t]
            if (c == EMPTY || isWhitePiece(c) != white) out.add(ChessMove(sq, t))
        }
    }

    private fun slideMoves(p: ChessPosition, sq: Int, white: Boolean, dirs: Array<IntArray>, out: MutableList<ChessMove>) {
        for ((df, dr) in dirs) {
            var t = offset(sq, df, dr)
            while (t >= 0) {
                val c = p.squares[t]
                if (c == EMPTY) out.add(ChessMove(sq, t))
                else { if (isWhitePiece(c) != white) out.add(ChessMove(sq, t)); break }
                t = offset(t, df, dr)
            }
        }
    }

    private fun pawnMoves(p: ChessPosition, sq: Int, white: Boolean, out: MutableList<ChessMove>) {
        val dr = if (white) -1 else 1
        val startRank = if (white) 6 else 1
        val promoRank = if (white) 0 else 7

        val one = offset(sq, 0, dr)
        if (one >= 0 && p.squares[one] == EMPTY) {
            addPawnMove(sq, one, promoRank, white, out)
            if (rankOf(sq) == startRank) {
                val two = offset(sq, 0, dr * 2)
                if (two >= 0 && p.squares[two] == EMPTY) out.add(ChessMove(sq, two))
            }
        }
        for (df in intArrayOf(-1, 1)) {
            val t = offset(sq, df, dr)
            if (t < 0) continue
            val c = p.squares[t]
            if (c != EMPTY && isWhitePiece(c) != white) {
                addPawnMove(sq, t, promoRank, white, out)
            } else if (c == EMPTY && t == p.epSquare) {
                out.add(ChessMove(sq, t))
            }
        }
    }

    private fun addPawnMove(from: Int, to: Int, promoRank: Int, white: Boolean, out: MutableList<ChessMove>) {
        if (rankOf(to) == promoRank) {
            for (promo in charArrayOf('Q', 'R', 'B', 'N')) {
                out.add(ChessMove(from, to, if (white) promo else promo.lowercaseChar()))
            }
        } else {
            out.add(ChessMove(from, to))
        }
    }

    private fun castlingMoves(p: ChessPosition, kingSq: Int, white: Boolean, out: MutableList<ChessMove>) {
        if (white) {
            if (kingSq != 60) return
            if (p.squares[60] != 'K') return
            if (p.castling.contains('K') && p.squares[61] == EMPTY && p.squares[62] == EMPTY &&
                !isAttacked(p, 60, byWhite = false) && !isAttacked(p, 61, byWhite = false) && !isAttacked(p, 62, byWhite = false)
            ) out.add(ChessMove(60, 62))
            if (p.castling.contains('Q') && p.squares[59] == EMPTY && p.squares[58] == EMPTY && p.squares[57] == EMPTY &&
                !isAttacked(p, 60, byWhite = false) && !isAttacked(p, 59, byWhite = false) && !isAttacked(p, 58, byWhite = false)
            ) out.add(ChessMove(60, 58))
        } else {
            if (kingSq != 4) return
            if (p.squares[4] != 'k') return
            if (p.castling.contains('k') && p.squares[5] == EMPTY && p.squares[6] == EMPTY &&
                !isAttacked(p, 4, byWhite = true) && !isAttacked(p, 5, byWhite = true) && !isAttacked(p, 6, byWhite = true)
            ) out.add(ChessMove(4, 6))
            if (p.castling.contains('q') && p.squares[3] == EMPTY && p.squares[2] == EMPTY && p.squares[1] == EMPTY &&
                !isAttacked(p, 4, byWhite = true) && !isAttacked(p, 3, byWhite = true) && !isAttacked(p, 2, byWhite = true)
            ) out.add(ChessMove(4, 2))
        }
    }

    // ------------------------------------------------------------------ make

    fun makeMove(p: ChessPosition, move: ChessMove): ChessPosition = makeMoveUnchecked(p, move)

    private fun makeMoveUnchecked(p: ChessPosition, move: ChessMove): ChessPosition {
        val sq = p.squares.copyOf()
        val white = p.whiteToMove
        val from = move.from
        val to = move.to
        val piece = sq[from]
        val captured = sq[to]

        sq[to] = piece
        sq[from] = EMPTY

        // En passant capture
        if (piece.lowercaseChar() == 'p' && to == p.epSquare && captured == EMPTY && fileOf(from) != fileOf(to)) {
            val capSq = to + if (white) 8 else -8
            sq[capSq] = EMPTY
        }

        // Promotion
        if (move.isPromotion) {
            sq[to] = if (white) move.promotion.uppercaseChar() else move.promotion.lowercaseChar()
        }

        // Castling rook move
        if (piece.lowercaseChar() == 'k' && abs(fileOf(to) - fileOf(from)) == 2) {
            when (to) {
                62 -> { sq[61] = sq[63]; sq[63] = EMPTY } // white O-O
                58 -> { sq[59] = sq[56]; sq[56] = EMPTY } // white O-O-O
                6 -> { sq[5] = sq[7]; sq[7] = EMPTY }     // black O-O
                2 -> { sq[3] = sq[0]; sq[0] = EMPTY }     // black O-O-O
            }
        }

        // Castling rights
        var rights = p.castling
        if (piece == 'K') rights = rights.replace("K", "").replace("Q", "")
        if (piece == 'k') rights = rights.replace("k", "").replace("q", "")
        if (from == 63 || to == 63) rights = rights.replace("K", "")
        if (from == 56 || to == 56) rights = rights.replace("Q", "")
        if (from == 7 || to == 7) rights = rights.replace("k", "")
        if (from == 0 || to == 0) rights = rights.replace("q", "")

        // En passant target
        val ep = if (piece.lowercaseChar() == 'p' && abs(rankOf(to) - rankOf(from)) == 2) {
            (from + to) / 2
        } else -1

        val isPawn = piece.lowercaseChar() == 'p'
        val isEp = isPawn && to == p.epSquare && captured == EMPTY && fileOf(from) != fileOf(to)
        val halfmove = if (isPawn || captured != EMPTY || isEp) 0 else p.halfmove + 1

        val fullmove = if (!white) p.fullmove + 1 else p.fullmove

        return ChessPosition(sq, !white, rights, ep, halfmove, fullmove)
    }

    // ----------------------------------------------------------------- status

    fun status(p: ChessPosition): ChessStatus {
        val hasMoves = legalMoves(p).isNotEmpty()
        if (!hasMoves) return if (isInCheck(p, p.whiteToMove)) ChessStatus.CHECKMATE else ChessStatus.STALEMATE
        if (p.halfmove >= 100 || insufficientMaterial(p)) return ChessStatus.DRAW
        return if (isInCheck(p, p.whiteToMove)) ChessStatus.CHECK else ChessStatus.PLAYING
    }

    fun insufficientMaterial(p: ChessPosition): Boolean {
        var minors = 0
        for (c in p.squares) {
            when (c.lowercaseChar()) {
                'p', 'r', 'q' -> return false
                'n', 'b' -> minors++
                else -> {}
            }
        }
        return minors <= 1
    }

    // --------------------------------------------------------------------- AI

    private val PIECE_VALUE = mapOf(
        'p' to 100, 'n' to 320, 'b' to 330, 'r' to 500, 'q' to 900, 'k' to 20000
    )

    private val PAWN_PST = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5, 5, 10, 25, 25, 10, 5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, -5, -10, 0, 0, -10, -5, 5,
        5, 10, 10, -20, -20, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0
    )

    private val KNIGHT_PST = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50
    )

    /** Depth + jitter (centipawns) for a [TicTacToeEngine.Difficulty]. */
    private fun tuningFor(difficulty: TicTacToeEngine.Difficulty): Pair<Int, Double> = when (difficulty) {
        TicTacToeEngine.Difficulty.EASY -> 1 to 90.0
        TicTacToeEngine.Difficulty.NORMAL -> 2 to 35.0
        TicTacToeEngine.Difficulty.HARD -> 3 to 10.0
        TicTacToeEngine.Difficulty.CHEATING -> 4 to 0.0
    }

    fun bestMove(p: ChessPosition, difficulty: TicTacToeEngine.Difficulty): ChessMove? {
        val moves = legalMoves(p)
        if (moves.isEmpty()) return null
        val (depth, jitter) = tuningFor(difficulty)
        val ordered = orderMoves(p, moves)
        var best = ordered.first()
        var bestScore = Int.MIN_VALUE
        for (m in ordered) {
            val score = -negamax(makeMoveUnchecked(p, m), depth - 1, -MATE, MATE)
            val s = if (jitter > 0) score + Random.nextDouble(-jitter, jitter).toInt() else score
            if (s > bestScore) { bestScore = s; best = m }
        }
        return best
    }

    fun evaluate(p: ChessPosition): Int {
        var score = 0
        for (sq in 0 until 64) {
            val c = p.squares[sq]
            if (c == EMPTY) continue
            val white = isWhitePiece(c)
            val lc = c.lowercaseChar()
            val value = PIECE_VALUE[lc] ?: 0
            val pst = pstFor(lc, sq, white)
            score += if (white) value + pst else -(value + pst)
        }
        return if (p.whiteToMove) score else -score
    }

    /** Mirror the black PST index so both colours share one table. */
    private fun pstFor(piece: Char, sq: Int, white: Boolean): Int {
        val idx = if (white) sq else mirrorIndex(sq)
        return when (piece) {
            'p' -> PAWN_PST[idx]
            'n' -> KNIGHT_PST[idx]
            else -> 0
        }
    }

    private fun mirrorIndex(sq: Int): Int {
        val f = fileOf(sq)
        val r = rankOf(sq)
        return (7 - r) * 8 + f
    }

    private fun negamax(p: ChessPosition, depth: Int, alphaIn: Int, betaIn: Int): Int {
        var alpha = alphaIn
        val beta = betaIn
        if (depth <= 0) return evaluate(p)

        val moves = legalMoves(p)
        if (moves.isEmpty()) return if (isInCheck(p, p.whiteToMove)) -MATE else 0

        var best = Int.MIN_VALUE
        for (m in orderMoves(p, moves)) {
            val score = -negamax(makeMoveUnchecked(p, m), depth - 1, -beta, -alpha)
            if (score > best) best = score
            if (score > alpha) alpha = score
            if (alpha >= beta) break
        }
        return best
    }

    /** Captures first (MVV-LVA), which makes alpha-beta cut far more often. */
    private fun orderMoves(p: ChessPosition, moves: List<ChessMove>): List<ChessMove> =
        moves.sortedByDescending { m ->
            val victim = p.squares[m.to]
            if (victim == EMPTY) 0
            else (PIECE_VALUE[victim.lowercaseChar()] ?: 0) * 10 -
                (PIECE_VALUE[p.squares[m.from].lowercaseChar()] ?: 0)
        }

    // ------------------------------------------------------------------ tables

    private val KNIGHT = arrayOf(
        intArrayOf(1, 2), intArrayOf(2, 1), intArrayOf(2, -1), intArrayOf(1, -2),
        intArrayOf(-1, -2), intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, 2)
    )
    private val KING = arrayOf(
        intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(1, 0), intArrayOf(1, -1),
        intArrayOf(0, -1), intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1)
    )
    private val DIAG = arrayOf(
        intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1)
    )
    private val ORTHO = arrayOf(
        intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(-1, 0)
    )
}
