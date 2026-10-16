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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cupcake.game.ChessBoard
import com.cupcake.game.GameManager
import com.cupcake.ui.theme.CupCakeTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.SportsEsports

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    onClose: () -> Unit,
    startGameType: String? = null,
    viewModel: GameViewModel = hiltViewModel()
) {
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val availableGames by viewModel.availableGames.collectAsStateWithLifecycle()
    val selectedDifficulty by viewModel.selectedDifficulty.collectAsStateWithLifecycle()
    val botComment by viewModel.botComment.collectAsStateWithLifecycle()

    // Auto-launch a specific game when arriving from the home screen
    LaunchedEffect(startGameType) {
        if (!startGameType.isNullOrBlank()) {
            val target = availableGames.firstOrNull { it.type.name == startGameType }
            if (target != null) {
                viewModel.startGame(target, selectedDifficulty)
            }
        }
    }

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

            // Live bot commentary — Marin's reaction to your move / game-ending
            botComment?.let { comment ->
                androidx.compose.material3.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🌸", fontSize = 20.sp)
                        Text(
                            text = comment,
                            modifier = Modifier.weight(1f),
                            fontSize = 15.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }


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
                        modifier = Modifier.fillMaxWidth().weight(1f)
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
                    com.cupcake.game.Game.GameType.GUESS_NUMBER -> GuessNumberBoardUI(
                        state = boardState,
                        onGuess = { pos -> viewModel.makeMove(pos) },
                        onRestart = { viewModel.restartGame() }
                    )
                    com.cupcake.game.Game.GameType.MATH_QUIZ -> MathQuizBoardUI(
                        state = boardState,
                        onAnswer = { pos -> viewModel.makeMove(pos) },
                        onRestart = { viewModel.restartGame() }
                    )
                    com.cupcake.game.Game.GameType.CHESS -> ChessBoardUI(
                        state = boardState,
                        onRestart = { viewModel.restartGame() }
                    )
                    com.cupcake.game.Game.GameType.TIC_TAC_TOE_4X4 -> TicTacToe4x4BoardUI(
                        state = boardState,
                        onCellClick = { pos -> viewModel.makeMove(pos) },
                        onRestart = { viewModel.restartGame() }
                    )
                    com.cupcake.game.Game.GameType.CONNECT_FOUR -> ConnectFourBoardUI(
                        state = boardState,
                        onColumnClick = { col -> viewModel.makeMove(col) },
                        onRestart = { viewModel.restartGame() }
                    )
                    com.cupcake.game.Game.GameType.MEMORY_MATCH -> MemoryMatchBoardUI(
                        state = boardState,
                        onCardClick = { pos -> viewModel.makeMove(pos) },
                        onRestart = { viewModel.restartGame() }
                    )
                    com.cupcake.game.Game.GameType.HANGMAN -> HangmanBoardUI(
                        state = boardState,
                        onGuess = { pos -> viewModel.makeMove(pos) },
                        onRestart = { viewModel.restartGame() }
                    )
                    com.cupcake.game.Game.GameType.WORD_SCRAMBLE -> WordScrambleBoardUI(
                        state = boardState,
                        onSubmit = { answer -> viewModel.submitTextAnswer(answer) },
                        onRestart = { viewModel.restartGame() }
                    )
                    com.cupcake.game.Game.GameType.NUMBER_BASE -> NumberBaseBoardUI(
                        state = boardState,
                        onSubmit = { answer -> viewModel.submitTextAnswer(answer) },
                        onRestart = { viewModel.restartGame() }
                    )
                    com.cupcake.game.Game.GameType.REACTION_TIME -> ReactionTimeBoardUI(
                        state = boardState,
                        onTap = { viewModel.makeMove(0) },
                        onRestart = { viewModel.restartGame() }
                    )
                    com.cupcake.game.Game.GameType.MASTERMIND -> MastermindBoardUI(
                        state = boardState,
                        onSubmit = { guess -> viewModel.submitMastermindGuess(guess) },
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
            .height(175.dp),
        shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = game.icon, fontSize = 28.sp)
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = game.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = game.description,
                    fontSize = 11.sp,
                    maxLines = 2,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    text = game.personality,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
            }
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
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Status
        Text(
            text = when (state.status) {
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> "You won! 🎉"
                com.cupcake.game.Game.GameStatus.BOT_WON -> "Bot won! 🤖"
                com.cupcake.game.Game.GameStatus.DRAW -> "Draw! 🤝"
                com.cupcake.game.Game.GameStatus.CHEATING_DETECTED -> "Cheating detected! 😤"
                else -> if (state.currentPlayer == com.cupcake.game.Game.Player.BOT) "Bot is thinking… 🤔" else "Your turn (X)"
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = when (state.status) {
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                com.cupcake.game.Game.GameStatus.BOT_WON -> androidx.compose.material3.MaterialTheme.colorScheme.error
                else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            }
        )

        // Board
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 330.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (row in 0..2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0..2) {
                            val pos = row * 3 + col
                            val cell = board.get(pos)
                            TicTacToeCell(
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                                value = cell?.symbol ?: "",
                                isWinningCell = isWinningCell(board, pos, state.winner),
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

        // Restart button
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

@Composable
fun TicTacToeCell(
    modifier: Modifier = Modifier,
    value: String,
    isWinningCell: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = when {
        isWinningCell -> androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer
        value.isNotEmpty() -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
        else -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val contentColor = when (value) {
        "X" -> androidx.compose.material3.MaterialTheme.colorScheme.primary
        "O" -> androidx.compose.material3.MaterialTheme.colorScheme.error
        else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        enabled = value.isEmpty(),
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        shadowElevation = if (isWinningCell) 8.dp else 2.dp,
        border = if (isWinningCell) androidx.compose.foundation.BorderStroke(2.dp, androidx.compose.material3.MaterialTheme.colorScheme.tertiary) else null
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = contentColor
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

@Composable
fun GuessNumberBoardUI(
    state: com.cupcake.game.Game.State,
    onGuess: (Int) -> Unit,
    onRestart: () -> Unit
) {
    val board = state.board as? com.cupcake.game.Game.GuessNumberBoard ?: return

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status
        Text(
            text = when (state.status) {
                com.cupcake.game.Game.GameStatus.PLAYING -> "Guess the Number!"
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> "You won! 🎉 Number was ${board.targetNumber}"
                com.cupcake.game.Game.GameStatus.BOT_WON -> "Out of guesses! 🤖 Number was ${board.targetNumber}"
                else -> "Game Over"
            },
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = when (state.status) {
                com.cupcake.game.Game.GameStatus.HUMAN_WON -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                com.cupcake.game.Game.GameStatus.BOT_WON -> androidx.compose.material3.MaterialTheme.colorScheme.error
                else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            }
        )

        // Hint
        val hintText = when {
            board.guesses.isEmpty() -> "I'm thinking of a number between 1 and 100. You have ${10 - board.guesses.size} guesses!"
            board.lastResult == com.cupcake.game.Game.GuessNumberBoard.GuessResult.TOO_HIGH -> "Too high! 📉 ${10 - board.guesses.size} guesses left. Range: 1-${(board.lastGuess ?: 100) - 1}"
            board.lastResult == com.cupcake.game.Game.GuessNumberBoard.GuessResult.TOO_LOW -> "Too low! 📈 ${10 - board.guesses.size} guesses left. Range: ${(board.lastGuess ?: 1) + 1}-100"
            board.lastResult == com.cupcake.game.Game.GuessNumberBoard.GuessResult.CORRECT -> "You got it! 🎉 The number was ${board.targetNumber}"
            else -> "Guess a number between 1-100"
        }
        Text(
            text = hintText,
            fontSize = 16.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )

        // Previous guesses
        if (board.guesses.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your guesses: ${board.guesses.joinToString(", ")}", fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Number picker (0-99 grid, maps to 1-100)
        if (state.status == com.cupcake.game.Game.GameStatus.PLAYING) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..9).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (0..9).forEach { col ->
                            val guess = row * 10 + col + 1
                            val isGuessed = board.guesses.contains(guess)
                            androidx.compose.material3.Button(
                                onClick = { 
                                    if (!isGuessed) onGuess(guess - 1) // Position is 0-indexed
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                enabled = !isGuessed,
                                shape = RoundedCornerShape(8.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = if (isGuessed) 
                                        androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                                    else 
                                        androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Text(text = "$guess", fontSize = 16.sp, fontWeight = FontWeight.Medium,
                                    color = if (isGuessed) androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    else androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        } else {
            Button(onClick = onRestart) { Text("Play Again") }
        }
    }
}

@Composable
fun MathQuizBoardUI(
    state: com.cupcake.game.Game.State,
    onAnswer: (Int) -> Unit,
    onRestart: () -> Unit
) {
    val board = state.board as? com.cupcake.game.Game.MathQuizBoard ?: return
    var currentAnswer by remember { mutableStateOf("") }
    var isNegative by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Score
        Text(
            text = "Score: ${board.correctAnswers}/${board.questionsAnswered}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary
        )

        // Current question
        board.currentQuestion?.let { question ->
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "${question.expression} = ?",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
                
                if (state.status == com.cupcake.game.Game.GameStatus.PLAYING) {
                    // Answer display
                    androidx.compose.material3.TextField(
                        value = if (isNegative) "-$currentAnswer" else currentAnswer,
                        onValueChange = { currentAnswer = it.filter { it.isDigit() } },
                        modifier = Modifier.fillMaxWidth().width(200.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                        readOnly = true
                    )
                    
                    // Number pad
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Button(
                                onClick = { isNegative = !isNegative },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text(if (isNegative) "+/−" else "−", fontSize = 20.sp) }
                            (1..3).forEach { digit ->
                                androidx.compose.material3.Button(
                                    onClick = { currentAnswer = "$currentAnswer$digit" },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("$digit", fontSize = 24.sp, fontWeight = FontWeight.Medium) }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (4..6).forEach { digit ->
                                androidx.compose.material3.Button(
                                    onClick = { currentAnswer = "$currentAnswer$digit" },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("$digit", fontSize = 24.sp, fontWeight = FontWeight.Medium) }
                            }
                            androidx.compose.material3.Button(
                                onClick = { 
                                    if (currentAnswer.isNotEmpty()) {
                                        val answer = (if (isNegative) "-$currentAnswer" else currentAnswer).toIntOrNull() ?: 0
                                        onAnswer(answer)
                                        currentAnswer = ""
                                        isNegative = false
                                    }
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) { Text("✓", fontSize = 24.sp) }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (7..9).forEach { digit ->
                                androidx.compose.material3.Button(
                                    onClick = { currentAnswer = "$currentAnswer$digit" },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("$digit", fontSize = 24.sp, fontWeight = FontWeight.Medium) }
                            }
                            androidx.compose.material3.Button(
                                onClick = { 
                                    if (currentAnswer.isNotEmpty()) {
                                        currentAnswer = currentAnswer.dropLast(1)
                                    } else if (isNegative) {
                                        isNegative = false
                                    }
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("⌫", fontSize = 24.sp) }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Button(
                                onClick = { currentAnswer = "${currentAnswer}0" },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("0", fontSize = 24.sp, fontWeight = FontWeight.Medium) }
                            androidx.compose.material3.Button(
                                onClick = { currentAnswer = "" },
                                modifier = Modifier.weight(2f).height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Clear", fontSize = 18.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
            }
        }

        // Feedback
        board.lastResult?.let { isCorrect ->
            Text(
                text = if (isCorrect) "Correct! 😒" else "Wrong! 😤",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = if (isCorrect) androidx.compose.material3.MaterialTheme.colorScheme.tertiary else androidx.compose.material3.MaterialTheme.colorScheme.error
            )
        }

        // Game over
        if (state.status != com.cupcake.game.Game.GameStatus.PLAYING) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (state.status == com.cupcake.game.Game.GameStatus.HUMAN_WON) "You won! 🎉" else "Bot won! 🤖",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("Correct: ${board.correctAnswers} | Wrong: ${board.wrongAnswers}", fontSize = 16.sp)
                Button(onClick = onRestart) { Text("Play Again") }
            }
        }
    }
}

@Composable
fun ChessBoardUI(
    state: com.cupcake.game.Game.State,
    onRestart: () -> Unit
) {
    val board = state.board as? ChessBoard ?: return
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        androidx.compose.material3.Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.SportsEsports,
            contentDescription = "Chess",
            modifier = Modifier.size(64.dp),
            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "Chess",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Chess engine integration coming soon!\nCurrently shows FEN: ${board.currentFen.take(50)}...",
            fontSize = 16.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        
        if (state.status != com.cupcake.game.Game.GameStatus.PLAYING) {
            Button(onClick = onRestart) { Text("Restart") }
        }
    }
}
