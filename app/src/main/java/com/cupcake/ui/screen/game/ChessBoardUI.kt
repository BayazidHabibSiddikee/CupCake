package com.cupcake.ui.screen.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cupcake.game.ChessBoard
import com.cupcake.game.ChessEngine
import com.cupcake.game.Game

/**
 * Interactive chess board. Human plays white; tap a piece to see its legal moves,
 * tap a destination to play. Castling, en passant and promotion are all handled by
 * [ChessEngine], so the UI only has to collect from/to (+ promotion choice).
 */
@Composable
fun ChessBoardUI(
    state: Game.State,
    onMove: (Int, Int, Char) -> Unit,
    onRestart: () -> Unit
) {
    val board = state.board as? ChessBoard ?: return
    val position = board.position
    val humanTurn = state.status == Game.GameStatus.PLAYING && state.currentPlayer == Game.Player.HUMAN

    val legal = remember(position, state.status) { ChessEngine.legalMoves(position) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var pendingPromotion by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Fresh position -> drop the old selection.
    LaunchedEffect(position) {
        selected = null
        pendingPromotion = null
    }

    val targets = remember(selected, legal) {
        selected?.let { s -> legal.filter { it.from == s }.map { it.to }.toSet() } ?: emptySet()
    }
    val lastMove = state.moveHistory.lastOrNull()
        ?.let { it.position / 64 to it.position % 64 }
    val checkSquare = if (ChessEngine.isInCheck(position, position.whiteToMove)) position.kingSquare(position.whiteToMove) else -1

    val statusText = when (state.status) {
        Game.GameStatus.HUMAN_WON -> board.result ?: "You won! 🎉"
        Game.GameStatus.BOT_WON -> board.result ?: "Marin won! 👑"
        Game.GameStatus.DRAW -> board.result ?: "Draw 🤝"
        else -> when {
            state.currentPlayer == Game.Player.BOT -> "Marin is thinking…"
            checkSquare >= 0 -> "You're in check! 😬"
            else -> "Your move"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(statusText, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            for (row in 0 until 8) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (file in 0 until 8) {
                        val sq = row * 8 + file
                        val piece = position.pieceAt(sq)
                        val light = (row + file) % 2 == 0
                        val base = if (light) Color(0xFFEDD6B0) else Color(0xFFB58863)
                        val overlay = when {
                            sq == selected -> Color(0x88FFEB3B)
                            checkSquare == sq -> Color(0x99F44336)
                            lastMove != null && (sq == lastMove.first || sq == lastMove.second) -> Color(0x44FFC107)
                            else -> Color.Transparent
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(base)
                                .background(overlay)
                                .clickable(enabled = humanTurn) {
                                    val sel = selected
                                    when {
                                        sel == null -> if (isHumanPiece(piece)) selected = sq
                                        sq in targets -> {
                                            val promo = legal.filter { it.from == sel && it.to == sq }.any { it.isPromotion }
                                            if (promo) pendingPromotion = sel to sq
                                            else { onMove(sel, sq, ' '); selected = null }
                                        }
                                        isHumanPiece(piece) -> selected = sq
                                        else -> selected = null
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Legal-target dot
                            if (sq in targets && piece == '.') {
                                Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(Color(0x66000000)))
                            }
                            if (piece != '.') {
                                Text(
                                    text = glyph(piece),
                                    fontSize = 30.sp,
                                    color = if (piece.isUpperCase()) Color.White else Color(0xFF121212),
                                    style = TextStyle(
                                        shadow = if (piece.isUpperCase())
                                            Shadow(Color(0xAA000000), Offset(0f, 1f), 2f) else null
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.status != Game.GameStatus.PLAYING) {
            Button(onClick = onRestart) { Text("Rematch") }
        }
    }

    pendingPromotion?.let { (from, to) ->
        AlertDialog(
            onDismissRequest = { pendingPromotion = null },
            title = { Text("Promote your pawn") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf('Q' to "♕", 'R' to "♖", 'B' to "♗", 'N' to "♘").forEach { (piece, glyphText) ->
                        TextButton(onClick = {
                            onMove(from, to, piece)
                            pendingPromotion = null
                        }) { Text(glyphText, fontSize = 28.sp) }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

private fun isHumanPiece(piece: Char): Boolean = piece in 'A'..'Z'

private fun glyph(piece: Char): String = when (piece) {
    'K' -> "♚"; 'Q' -> "♛"; 'R' -> "♜"; 'B' -> "♝"; 'N' -> "♞"; 'P' -> "♟"
    'k' -> "♚"; 'q' -> "♛"; 'r' -> "♜"; 'b' -> "♝"; 'n' -> "♞"; 'p' -> "♟"
    else -> ""
}
