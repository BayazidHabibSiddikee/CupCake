package com.cupcake.ui.screen.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ==========================================
// Tic Tac Toe 4x4
// ==========================================

private val TIC_TAC_TOE_4X4_LINES: List<List<Int>> = buildList {
    for (row in 0..3) add((0..3).map { row * 4 + it })
    for (col in 0..3) add((0..3).map { it * 4 + col })
    add(listOf(0, 5, 10, 15))
    add(listOf(3, 6, 9, 12))
}

private fun isWinningCell4x4(board: com.cupcake.game.Game.TicTacToe4x4Board, pos: Int, winner: com.cupcake.game.Game.Player?): Boolean {
    if (winner == null) return false
    return TIC_TAC_TOE_4X4_LINES.any { line ->
        pos in line && line.all { board.get(it) == winner }
    }
}

@Composable
fun TicTacToe4x4BoardUI(
    state: com.cupcake.game.Game.State,
    onCellClick: (Int) -> Unit,
    onRestart: () -> Unit
) {
    val board = state.board as? com.cupcake.game.Game.TicTacToe4x4Board ?: return

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = when (state.status) {
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> "You won! 🎉"
                com.cupcake.game.Game.GameStatus.BOT_WON -> "Bot won! 🤖"
                com.cupcake.game.Game.GameStatus.DRAW -> "Draw! 🤝"
                com.cupcake.game.Game.GameStatus.CHEATING_DETECTED -> "Cheating detected! 😤"
                else -> if (state.currentPlayer == com.cupcake.game.Game.Player.BOT) "Bot is thinking… 🤔" else "Your turn (X) — 4 in a row to win!"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = when (state.status) {
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                com.cupcake.game.Game.GameStatus.BOT_WON -> androidx.compose.material3.MaterialTheme.colorScheme.error
                else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            }
        )

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (row in 0..3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (col in 0..3) {
                            val pos = row * 4 + col
                            TicTacToeCell(
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                                value = board.get(pos)?.symbol ?: "",
                                isWinningCell = isWinningCell4x4(board, pos, state.winner),
                                onClick = {
                                    if (state.status == com.cupcake.game.Game.GameStatus.PLAYING &&
                                        state.currentPlayer == com.cupcake.game.Game.Player.HUMAN
                                    ) {
                                        onCellClick(pos)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (state.status != com.cupcake.game.Game.GameStatus.PLAYING) {
            Button(
                onClick = onRestart,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Play Again 🔄", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// Connect Four
// ==========================================

@Composable
fun ConnectFourBoardUI(
    state: com.cupcake.game.Game.State,
    onColumnClick: (Int) -> Unit,
    onRestart: () -> Unit
) {
    val board = state.board as? com.cupcake.game.Game.ConnectFourBoard ?: return
    val humanColor = Color(0xFFE53935)
    val botColor = Color(0xFFFDD835)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = when {
                state.status == com.cupcake.game.Game.GameStatus.HUMAN_WON -> "You connected 4! 🎉"
                state.status == com.cupcake.game.Game.GameStatus.BOT_WON -> "Bot connected 4! 🤖"
                state.status == com.cupcake.game.Game.GameStatus.DRAW -> "Board full — draw! 🤝"
                state.currentPlayer == com.cupcake.game.Game.Player.BOT -> "Bot is thinking… 🤔"
                else -> "Your turn (🔴)"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = when (state.status) {
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                com.cupcake.game.Game.GameStatus.BOT_WON -> androidx.compose.material3.MaterialTheme.colorScheme.error
                else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            }
        )

        Text(
            text = "Tap a column to drop your disc",
            fontSize = 14.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 6 rows x 7 columns (top row first)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in 0..5) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (col in 0..6) {
                        val cell = board.get(row * 7 + col)
                        androidx.compose.material3.Surface(
                            onClick = {
                                if (state.status == com.cupcake.game.Game.GameStatus.PLAYING &&
                                    state.currentPlayer == com.cupcake.game.Game.Player.HUMAN
                                ) {
                                    onColumnClick(col)
                                }
                            },
                            shape = CircleShape,
                            color = when (cell) {
                                com.cupcake.game.Game.Player.HUMAN -> humanColor
                                com.cupcake.game.Game.Player.BOT -> botColor
                                null -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.weight(1f).aspectRatio(1f)
                        ) { }
                    }
                }
            }
        }

        if (state.status != com.cupcake.game.Game.GameStatus.PLAYING) {
            Button(onClick = onRestart) { Text("Play Again") }
        }
    }
}

// ==========================================
// Memory Match
// ==========================================

@Composable
fun MemoryMatchBoardUI(
    state: com.cupcake.game.Game.State,
    onCardClick: (Int) -> Unit,
    onRestart: () -> Unit
) {
    val board = state.board as? com.cupcake.game.Game.MemoryMatchBoard ?: return

    // Tick while the cards are face-up at the start of the round.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(board.startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= board.startedAt + board.previewMillis) break
            delay(200)
        }
    }
    val previewLeftMs = (board.startedAt + board.previewMillis - now).coerceAtLeast(0L)
    val previewing = previewLeftMs > 0L &&
        state.status == com.cupcake.game.Game.GameStatus.PLAYING

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = when {
                previewing -> "Memorise! ${(previewLeftMs / 1000.0).let { "%.0f".format(it) }}s 🧠"
                state.status == com.cupcake.game.Game.GameStatus.PLAYING -> "Find all the pairs! 🃏"
                state.status == com.cupcake.game.Game.GameStatus.HUMAN_WON -> "You found them all! 🎉"
                else -> "Game Over"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Moves: ${board.moves}  •  Pairs: ${board.matchedPairs.size}/${board.pairs}",
            fontSize = 16.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 4x4 card grid
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in 0..3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (col in 0..3) {
                        val index = row * 4 + col
                        val card = board.cards.getOrNull(index)
                        val revealed = previewing || (card != null && (card.isFlipped || card.isMatched))
                        androidx.compose.material3.Surface(
                            onClick = {
                                if (state.status == com.cupcake.game.Game.GameStatus.PLAYING &&
                                    !previewing && !revealed
                                ) {
                                    onCardClick(index)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                card == null -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                                card.isMatched -> androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                previewing -> androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                                revealed -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                                else -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            modifier = Modifier.weight(1f).aspectRatio(1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (revealed && card != null) card.value else "❔",
                                    fontSize = 28.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.status != com.cupcake.game.Game.GameStatus.PLAYING) {
            Button(onClick = onRestart) { Text("Play Again") }
        }
    }
}

// ==========================================
// Hangman
// ==========================================

@Composable
fun HangmanBoardUI(
    state: com.cupcake.game.Game.State,
    onGuess: (Int) -> Unit,
    onRestart: () -> Unit
) {
    val board = state.board as? com.cupcake.game.Game.HangmanBoard ?: return

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = when (state.status) {
                com.cupcake.game.Game.GameStatus.PLAYING -> "Guess the word! 🎯"
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> "You got it! 🎉"
                com.cupcake.game.Game.GameStatus.BOT_WON -> "Out of guesses! The word was ${board.word}"
                else -> "Game Over"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = when (state.status) {
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                com.cupcake.game.Game.GameStatus.BOT_WON -> androidx.compose.material3.MaterialTheme.colorScheme.error
                else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            }
        )

        Text(
            text = com.cupcake.game.HangmanEngine.getHangmanArt(board.wrongGuesses),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Wrong guesses: ${board.wrongGuesses} / ${board.maxWrongGuesses}",
            fontSize = 14.sp,
            color = if (board.wrongGuesses >= board.maxWrongGuesses - 1)
                androidx.compose.material3.MaterialTheme.colorScheme.error
            else
                androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = board.getDisplayWord(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )

        if (board.guessedLetters.any { it !in board.word }) {
            Text(
                text = "Missed: ${board.getWrongLetters()}",
                fontSize = 14.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error
            )
        }

        if (state.status == com.cupcake.game.Game.GameStatus.PLAYING) {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (letter in 'A'..'Z') {
                    val guessed = letter in board.guessedLetters
                    Button(
                        onClick = { onGuess(letter - 'A') },
                        enabled = !guessed,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("$letter", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }

        if (state.status != com.cupcake.game.Game.GameStatus.PLAYING) {
            Button(onClick = onRestart) { Text("Play Again") }
        }
    }
}

// ==========================================
// Word Scramble
// ==========================================

@Composable
fun WordScrambleBoardUI(
    state: com.cupcake.game.Game.State,
    onSubmit: (String) -> Unit,
    onRestart: () -> Unit
) {
    val board = state.board as? com.cupcake.game.Game.WordScrambleBoard ?: return
    var answer by remember(state) { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = when (state.status) {
                com.cupcake.game.Game.GameStatus.PLAYING -> "Unscramble the word! 🔤"
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> "Solved in ${board.attempts}! 🎉"
                com.cupcake.game.Game.GameStatus.BOT_WON -> "Out of attempts! It was ${board.originalWord}"
                else -> "Game Over"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = when (state.status) {
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                com.cupcake.game.Game.GameStatus.BOT_WON -> androidx.compose.material3.MaterialTheme.colorScheme.error
                else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            }
        )

        Text(
            text = "Attempt ${board.attempts}/${board.maxAttempts}",
            fontSize = 14.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )

        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = board.scrambledWord,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
            }
        }

        if (state.status == com.cupcake.game.Game.GameStatus.PLAYING) {
            TextField(
                value = answer,
                onValueChange = { answer = it.uppercase() },
                label = { Text("Your answer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (answer.isNotBlank()) {
                        onSubmit(answer.trim())
                        answer = ""
                    }
                },
                enabled = answer.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Submit", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (state.status != com.cupcake.game.Game.GameStatus.PLAYING) {
            Button(onClick = onRestart) { Text("Play Again") }
        }
    }
}

// ==========================================
// Number Base Conversion
// ==========================================

@Composable
fun NumberBaseBoardUI(
    state: com.cupcake.game.Game.State,
    onSubmit: (String) -> Unit,
    onRestart: () -> Unit
) {
    val board = state.board as? com.cupcake.game.Game.NumberBaseBoard ?: return
    val question = board.currentQuestion
    var answer by remember(state) { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = when (state.status) {
                com.cupcake.game.Game.GameStatus.PLAYING -> "Convert the number! 💾"
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> "You nailed it! 🎉"
                com.cupcake.game.Game.GameStatus.BOT_WON -> "Better luck next time! 🤖"
                else -> "Game Over"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = when (state.status) {
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                com.cupcake.game.Game.GameStatus.BOT_WON -> androidx.compose.material3.MaterialTheme.colorScheme.error
                else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            }
        )

        Text(
            text = "Correct: ${board.correctAnswers} / ${board.questionsAnswered}",
            fontSize = 14.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )

        question?.let { q ->
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Convert this number:",
                        fontSize = 14.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = com.cupcake.game.NumberBaseEngine.formatInBase(q.number, q.fromBase),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${com.cupcake.game.NumberBaseEngine.getBaseName(q.fromBase)} → ${com.cupcake.game.NumberBaseEngine.getBaseName(q.toBase)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (state.status == com.cupcake.game.Game.GameStatus.PLAYING) {
                TextField(
                    value = answer,
                    onValueChange = { input -> answer = input.uppercase().filter { it in "0123456789ABCDEF" } },
                    label = { Text("Answer in ${com.cupcake.game.NumberBaseEngine.getBaseName(q.toBase)}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (answer.isNotBlank()) {
                            onSubmit(answer.trim())
                            answer = ""
                        }
                    },
                    enabled = answer.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Submit", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (state.status != com.cupcake.game.Game.GameStatus.PLAYING) {
            Button(onClick = onRestart) { Text("Play Again") }
        }
    }
}

// ==========================================
// Reaction Time
// ==========================================

@Composable
fun ReactionTimeBoardUI(
    state: com.cupcake.game.Game.State,
    onTap: () -> Unit,
    onRestart: () -> Unit
) {
    val board = state.board as? com.cupcake.game.Game.ReactionTimeBoard ?: return
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    // Keep ticking while we're waiting for the signal so the UI can switch to "TAP NOW!"
    LaunchedEffect(board.waitingForTap, state.status) {
        while (board.waitingForTap && state.status == com.cupcake.game.Game.GameStatus.PLAYING) {
            now = System.currentTimeMillis()
            delay(50)
        }
    }

    val signalVisible = board.waitingForTap && now >= board.startTime
    val waitingForSignal = board.waitingForTap && now < board.startTime

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = when (state.status) {
                com.cupcake.game.Game.GameStatus.PLAYING ->
                    "Round ${(board.rounds.size + 1).coerceAtMost(5)} / 5 ⚡"
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> "Done! 🎉"
                else -> "Game Over"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = if (board.bestTime == Long.MAX_VALUE) "Best: —" else "Best: ${board.bestTime}ms",
            fontSize = 16.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

        androidx.compose.material3.Surface(
            onClick = {
                if (state.status == com.cupcake.game.Game.GameStatus.PLAYING) onTap()
            },
            shape = CircleShape,
            color = when {
                state.status != com.cupcake.game.Game.GameStatus.PLAYING ->
                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                signalVisible -> androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer
                waitingForSignal -> androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                else -> androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
            },
            modifier = Modifier.size(220.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = when {
                        state.status != com.cupcake.game.Game.GameStatus.PLAYING -> "✅"
                        signalVisible -> "TAP!"
                        waitingForSignal -> "Wait..."
                        else -> "Tap to start"
                    },
                    fontSize = if (signalVisible) 36.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        signalVisible -> androidx.compose.material3.MaterialTheme.colorScheme.onTertiaryContainer
                        waitingForSignal -> androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                        else -> androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
        }

        if (board.rounds.isNotEmpty()) {
            Text(
                text = "Rounds: " + board.rounds.joinToString("  ") { round ->
                    if (round.isValid) "${round.reactionTimeMs}ms" else "TOO EARLY"
                },
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.status == com.cupcake.game.Game.GameStatus.PLAYING) {
            Text(
                text = when {
                    waitingForSignal -> "Don't tap yet — wait for the signal!"
                    board.rounds.isEmpty() -> "Tap to start. Win 3 of 5 clean rounds."
                    else -> "Tap the circle! Clean rounds: ${board.rounds.count { it.isValid }}/3"
                },
                fontSize = 14.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.status != com.cupcake.game.Game.GameStatus.PLAYING) {
            Button(onClick = onRestart) { Text("Play Again") }
        }
    }
}

// ==========================================
// Mastermind
// ==========================================

private val MASTERMIND_COLORS = listOf(
    Color(0xFFE53935), // red
    Color(0xFF1E88E5), // blue
    Color(0xFF43A047), // green
    Color(0xFFFDD835), // yellow
    Color(0xFF8E24AA), // purple
    Color(0xFFFB8C00)  // orange
)

@Composable
fun MastermindBoardUI(
    state: com.cupcake.game.Game.State,
    onSubmit: (List<Int>) -> Unit,
    onRestart: () -> Unit
) {
    val board = state.board as? com.cupcake.game.Game.MastermindBoard ?: return
    // Reset the in-progress pick whenever the board state changes (submit, restart)
    var pick by remember(state) { mutableStateOf(listOf<Int>()) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = when (state.status) {
                com.cupcake.game.Game.GameStatus.PLAYING -> "Crack the secret code! 🔐"
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> "Code cracked! 🎉"
                com.cupcake.game.Game.GameStatus.BOT_WON -> "Out of guesses! 🤖"
                else -> "Game Over"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = when (state.status) {
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                com.cupcake.game.Game.GameStatus.BOT_WON -> androidx.compose.material3.MaterialTheme.colorScheme.error
                else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            }
        )

        Text(
            text = "Guesses ${board.guesses.size}/${board.maxGuesses} • ⬛ exact position  🟨 wrong position",
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Guess history (newest first)
        if (board.guesses.isNotEmpty()) {
            androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    board.guesses.asReversed().forEach { guess ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            guess.guess.forEach { colorIndex ->
                                androidx.compose.material3.Surface(
                                    shape = CircleShape,
                                    color = MASTERMIND_COLORS[colorIndex],
                                    modifier = Modifier.size(18.dp)
                                ) { }
                            }
                            Text(
                                text = "⬛${guess.exactMatches}  🟨${guess.colorMatches}",
                                fontSize = 13.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (state.status == com.cupcake.game.Game.GameStatus.PLAYING) {
            Text(
                text = "Tap colors to build your guess (${pick.size}/4)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            // Current guess slots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (slot in 0..3) {
                    androidx.compose.material3.Surface(
                        shape = CircleShape,
                        color = if (slot < pick.size) MASTERMIND_COLORS[pick[slot]]
                        else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(40.dp)
                    ) { }
                }
            }

            // Color palette
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MASTERMIND_COLORS.forEachIndexed { index, color ->
                    androidx.compose.material3.Surface(
                        onClick = { if (pick.size < 4) pick = pick + index },
                        shape = CircleShape,
                        color = color,
                        modifier = Modifier.size(44.dp)
                    ) { }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pick = emptyList() }, enabled = pick.isNotEmpty()) {
                    Text("Clear")
                }
                Button(
                    onClick = { onSubmit(pick) },
                    enabled = pick.size == 4,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text("Submit", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Reveal the code when the game ends
        if (state.status != com.cupcake.game.Game.GameStatus.PLAYING) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Code:", fontWeight = FontWeight.Medium)
                board.code.forEach { colorIndex ->
                    androidx.compose.material3.Surface(
                        shape = CircleShape,
                        color = MASTERMIND_COLORS[colorIndex],
                        modifier = Modifier.size(24.dp)
                    ) { }
                }
            }
            Button(onClick = onRestart) { Text("Play Again") }
        }
    }
}
