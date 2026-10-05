package com.cupcake.ui.screen.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Choose a Game", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Play with your bot companion!", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            availableGames.forEach { game ->
                                GameOptionCard(
                                    game = game,
                                    onClick = { viewModel.startGame(game, selectedDifficulty) }
                                )
                            }
                        }
                        
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
                        
                        // Difficulty selector
                        androidx.compose.material3.Card(
                            modifier = Modifier.padding(16.dp).fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Difficulty", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                androidx.compose.foundation.layout.Row(
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
fun GameOptionCard(
    game: GameOption,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .width(280.dp)
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
    value: String,
    isWinningCell: Boolean = false,
    onClick: () -> Unit
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
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
