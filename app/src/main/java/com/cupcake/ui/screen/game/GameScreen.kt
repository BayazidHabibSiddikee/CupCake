package com.cupcake.ui.screen.game
import androidx.compose.foundation.layout.aspectRatio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cupcake.game.GameManager
import com.cupcake.ui.theme.CupCakeTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VideogameAsset

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    onClose: () -> Unit,
    viewModel: GameViewModel = hiltViewModel()
) {
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val availableGames by viewModel.availableGames.collectAsStateWithLifecycle()
    val showGameSelector by viewModel.showGameSelector.collectAsStateWithLifecycle()
    val selectedDifficulty by viewModel.selectedDifficulty.collectAsStateWithLifecycle()

    CupCakeTheme {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Games") },
                navigationIcon = { 
                    androidx.compose.material3.IconButton(onClick = onClose) { 
                        Icon(imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") 
                    } 
                },
                actions = {
                    if (gameState != null) {
                        IconButton(onClick = { viewModel.endGame() }) {
                            Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "End game")
                        }
                    } else {
                        IconButton(onClick = { viewModel.setShowGameSelector(true) }) {
                            Icon(imageVector = androidx.compose.material.icons.Icons.Filled.VideogameAsset, contentDescription = "New game")
                        }
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
                )
            )

            val activeGameState = gameState
            if (activeGameState == null) {
                // Game selection screen
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Choose a Game", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Play with your bot companion!", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    // Difficulty selector
                    androidx.compose.material3.Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Difficulty", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                com.cupcake.game.TicTacToeEngine.Difficulty.entries.forEach { diff ->
                                    FilterChip(
                                        selected = (selectedDifficulty == diff),
                                        onClick = { viewModel.selectedDifficulty.value = diff },
                                        label = { Text(diff.name) }
                                    )
                                }
                            }
                        }
                    }

                    // Games Grid
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(availableGames) { game ->
                            GameOptionCard(
                                game = game,
                                onClick = { viewModel.startGame(game, selectedDifficulty) }
                            )
                        }
                    }
                }
            } else {
                // Active game screen
                val boardState = activeGameState.state
                if (boardState == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No active game", fontSize = 18.sp)
                    }
                } else when (boardState.gameType) {
                    com.cupcake.game.Game.GameType.TIC_TAC_TOE -> TicTacToeBoard(
                        state = boardState,
                        onCellClick = { pos -> viewModel.makeMove(pos) },
                        onRestart = { viewModel.restartGame() }
                    )
                    com.cupcake.game.Game.GameType.ROCK_PAPER_SCISSORS -> RpsBoardUI(
                        state = boardState,
                        onChoice = { pos -> viewModel.makeMove(pos) },
                        onRestart = { viewModel.restartGame() }
                    )
                    com.cupcake.game.Game.GameType.CLICKER -> ClickerBoardUI(
                        state = boardState,
                        onTap = { viewModel.makeMove(0) }
                    )
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${boardState.gameType.name} coming soon!", fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RpsBoardUI(
    state: com.cupcake.game.Game.State,
    onChoice: (Int) -> Unit,
    onRestart: () -> Unit
) {
    val board = state.board as? com.cupcake.game.Game.RpsBoard ?: return
    val choices = listOf("🪨" to "Rock", "📄" to "Paper", "✂️" to "Scissors")
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = when (state.status) {
                com.cupcake.game.Game.GameStatus.PLAYING -> "Choose your weapon!"
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> "You won! 🎉"
                com.cupcake.game.Game.GameStatus.BOT_WON -> "Bot won! 🤖"
                else -> "Draw! 🤝"
            },
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        if (state.status == com.cupcake.game.Game.GameStatus.PLAYING) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                choices.forEachIndexed { index, (emoji, _) ->
                    Button(
                        onClick = { onChoice(index) },
                        modifier = Modifier.size(80.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(emoji, fontSize = 32.sp)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("You", fontSize = 16.sp)
                    Text(choices[board.humanChoice ?: 0].first, fontSize = 48.sp)
                }
                Text("VS", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Bot", fontSize = 16.sp)
                    Text(choices[board.botChoice ?: 0].first, fontSize = 48.sp)
                }
            }
            Button(onClick = onRestart) { Text("Play Again") }
        }
    }
}

@Composable
fun GameOptionCard(
    game: GameOption,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = game.icon, fontSize = 36.sp)
            }
            Text(game.name, fontWeight = FontWeight.Medium, fontSize = 18.sp)
            Text(game.description, fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("Personality: ${game.personality}", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun TicTacToeBoard(
    state: com.cupcake.game.Game.State,
    onCellClick: (Int) -> Unit,
    onRestart: () -> Unit = {}
) {
    val board = state.board as? com.cupcake.game.Game.TicTacToeBoard ?: return
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status
        Text(
            text = when (state.status) {
                com.cupcake.game.Game.GameStatus.PLAYING -> "Your turn (X)"
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> "You won! 🎉"
                com.cupcake.game.Game.GameStatus.BOT_WON -> "Bot won! 🤖"
                com.cupcake.game.Game.GameStatus.DRAW -> "Draw! 🤝"
                com.cupcake.game.Game.GameStatus.CHEATING_DETECTED -> "Cheating detected! 😤"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = when (state.status) {
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                com.cupcake.game.Game.GameStatus.BOT_WON -> androidx.compose.material3.MaterialTheme.colorScheme.error
                else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            }
        )

        // Board
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in 0..2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (col in 0..2) {
                        val pos = row * 3 + col
                        val cell = board.get(pos)
                        TicTacToeCell(
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            value = cell?.symbol ?: "",
                            isWinningCell = isWinningCell(board, pos, state.winner),
                            onClick = { 
                                if (state.status == com.cupcake.game.Game.GameStatus.PLAYING) {
                                    onCellClick(pos)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Restart button
        if (state.status != com.cupcake.game.Game.GameStatus.PLAYING) {
            Button(onClick = onRestart) {
                Text("Play Again")
            }
        }
    }
}

@Composable
fun TicTacToeCell(
    modifier: Modifier = Modifier,
    value: String,
    isWinningCell: Boolean = false,
    onClick: () -> Unit
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        enabled = value.isEmpty(),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (isWinningCell)
                androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer
            else
                androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = when (value) {
                    "X" -> androidx.compose.material3.MaterialTheme.colorScheme.primary
                    "O" -> androidx.compose.material3.MaterialTheme.colorScheme.error
                    else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

private fun isWinningCell(board: com.cupcake.game.Game.TicTacToeBoard, pos: Int, winner: com.cupcake.game.Game.Player?): Boolean {
    if (winner == null) return false
    val lines = listOf(
        intArrayOf(0,1,2), intArrayOf(3,4,5), intArrayOf(6,7,8),
        intArrayOf(0,3,6), intArrayOf(1,4,7), intArrayOf(2,5,8),
        intArrayOf(0,4,8), intArrayOf(2,4,6)
    )
    return lines.any { line ->
        pos in line && line.all { board.get(it) == winner }
    }
}

@Composable
fun ClickerBoardUI(
    state: com.cupcake.game.Game.State,
    onTap: () -> Unit
) {
    val board = state.board as? com.cupcake.game.Game.ClickerBoard ?: return
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Score: ${board.score}",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary
        )
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 32.dp))
        
        androidx.compose.material3.Surface(
            onClick = onTap,
            shape = androidx.compose.foundation.shape.CircleShape,
            color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(200.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "🧁",
                    fontSize = 100.sp
                )
            }
        }
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 32.dp))
        
        Text(
            text = "Tap the cupcake as fast as you can!",
            fontSize = 16.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
