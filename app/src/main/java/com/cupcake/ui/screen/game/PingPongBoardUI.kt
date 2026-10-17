package com.cupcake.ui.screen.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cupcake.game.Game
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Real-time Pong. The ball/paddles are simulated locally (normalised 0..1 space so
 * it works at any size); the *score* lives in the shared [Game.PingPongBoard] so
 * wins, points, reactions and adaptive difficulty flow through the normal plumbing.
 *
 * @param botSkill 0..1 — how fast/sharply CupCake tracks the ball. Escalates in-game
 *   as her anger rises, so a rematch after losing to you is genuinely harder.
 */
@Composable
fun PingPongBoardUI(
    state: Game.State,
    botSkill: Float,
    onPoint: (Boolean) -> Unit,
    onRestart: () -> Unit
) {
    val board = state.board as? Game.PingPongBoard ?: return
    val playing = state.status == Game.GameStatus.PLAYING

    // Normalised table coordinates (x: 0=left 1=right, y: 0=top 1=bottom).
    var ballX by remember { mutableFloatStateOf(0.5f) }
    var ballY by remember { mutableFloatStateOf(0.5f) }
    var ballVX by remember { mutableFloatStateOf(0.45f) }
    var ballVY by remember { mutableFloatStateOf(0.65f) }
    var humanPaddleX by remember { mutableFloatStateOf(0.5f) }
    var botPaddleX by remember { mutableFloatStateOf(0.5f) }

    // 3‑2‑1 ready countdown before a fresh match starts.
    var countdown by remember { mutableIntStateOf(0) }
    val serving = countdown > 0

    val paddleHalfWidth = 0.15f
    val humanY = 0.92f
    val botY = 0.08f

    fun resetToServe(humanScored: Boolean) {
        ballX = 0.5f
        ballY = 0.5f
        ballVX = if (humanScored) -0.45f else 0.45f
        ballVY = if (humanScored) -0.7f else 0.7f
    }

    LaunchedEffect(state.status, board.humanScore, board.botScore) {
        if (!playing) return@LaunchedEffect
        // Only the opening of a match gets the countdown; later points serve straight away.
        if (board.humanScore == 0 && board.botScore == 0) {
            for (s in 3 downTo 1) {
                countdown = s
                delay(1000)
            }
        }
        countdown = 0
        resetToServe(humanScored = false)
        // Adaptive bot: harder as she gets angrier.
        val botMaxSpeed = 0.35f + botSkill * 1.35f
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (last == 0L) { last = now; continue }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now

            var bx = ballX + ballVX * dt
            var by = ballY + ballVY * dt

            // Side walls
            if (bx < 0.02f) { bx = 0.02f; ballVX = abs(ballVX) }
            if (bx > 0.98f) { bx = 0.98f; ballVX = -abs(ballVX) }

            // Bot paddle tracks the ball, capped by its speed.
            botPaddleX = moveToward(botPaddleX, ballX, botMaxSpeed * dt)

            // Bat collisions (only when the ball is heading toward the bat).
            if (by < botY + 0.02f && ballVY < 0f && abs(bx - botPaddleX) <= paddleHalfWidth + 0.02f) {
                by = botY + 0.02f
                ballVY = abs(ballVY)
                ballVX += (bx - botPaddleX) * 1.2f
            }
            if (by > humanY - 0.02f && ballVY > 0f && abs(bx - humanPaddleX) <= paddleHalfWidth + 0.02f) {
                by = humanY - 0.02f
                ballVY = -abs(ballVY)
                ballVX += (bx - humanPaddleX) * 1.2f
            }

            ballVX = ballVX.coerceIn(-1.6f, 1.6f)
            if (abs(ballVY) < 0.45f) ballVY = if (ballVY < 0f) -0.45f else 0.45f

            when {
                by < -0.06f -> { onPoint(true); resetToServe(true); last = 0L }
                by > 1.06f -> { onPoint(false); resetToServe(false); last = 0L }
                else -> { ballX = bx; ballY = by }
            }
        }
    }

    val surface = MaterialTheme.colorScheme.surfaceVariant
    val humanColor = MaterialTheme.colorScheme.tertiary
    val botColor = MaterialTheme.colorScheme.error
    val ballColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "You ${board.humanScore}   —   ${board.botScore} CupCake",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = when {
                state.status == Game.GameStatus.HUMAN_WON -> "You won the match! 🎉  — nerf incoming…"
                state.status == Game.GameStatus.BOT_WON -> "CupCake won! 👑"
                serving -> "Get ready… the ball drops in $countdown"
                else -> "First to ${board.targetScore}. Drag left/right to move 🏓"
            },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(surface, RoundedCornerShape(20.dp))
                .pointerInput(playing, serving) {
                    if (!playing || serving) return@pointerInput
                    detectDragGestures(
                        onDragStart = { humanPaddleX = (it.x / size.width).coerceIn(paddleHalfWidth, 1f - paddleHalfWidth) }
                    ) { change, _ ->
                        humanPaddleX = (change.position.x / size.width).coerceIn(paddleHalfWidth, 1f - paddleHalfWidth)
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val paddleW = paddleHalfWidth * 2f * w
                val paddleH = 0.03f * h

                // Centre line
                var y = 0f
                while (y < h) {
                    drawRect(Color.Gray.copy(alpha = 0.35f), Offset(w / 2f - 2f, y), Size(4f, h / 24f))
                    y += h / 12f
                }

                // Bats
                drawRoundRect(
                    color = botColor,
                    topLeft = Offset(botPaddleX * w - paddleW / 2f, botY * h - paddleH / 2f),
                    size = Size(paddleW, paddleH),
                    cornerRadius = CornerRadius(paddleH / 2f, paddleH / 2f)
                )
                drawRoundRect(
                    color = humanColor,
                    topLeft = Offset(humanPaddleX * w - paddleW / 2f, humanY * h - paddleH / 2f),
                    size = Size(paddleW, paddleH),
                    cornerRadius = CornerRadius(paddleH / 2f, paddleH / 2f)
                )

                // Ball
                drawCircle(ballColor, radius = 0.022f * w, center = Offset(ballX * w, ballY * h))
            }

            if (serving) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (countdown > 0) countdown.toString() else "GO!",
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Bold,
                        color = ballColor.copy(alpha = 0.85f)
                    )
                }
            }

            if (!playing) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = onRestart, modifier = Modifier.height(48.dp)) {
                        Text(if (state.status == Game.GameStatus.HUMAN_WON) "Rematch (she's angrier)" else "Rematch")
                    }
                }
            }
        }
    }
}

private fun moveToward(current: Float, target: Float, maxStep: Float): Float {
    val delta = target - current
    return when {
        abs(delta) <= maxStep -> target
        delta > 0f -> current + maxStep
        else -> current - maxStep
    }
}
