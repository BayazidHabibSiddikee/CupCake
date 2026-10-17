package com.cupcake.ui.screen.points

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cupcake.game.PointsManager
import com.cupcake.ui.theme.CupCakeTheme
import com.cupcake.ui.theme.CuteCard
import com.cupcake.ui.theme.GoldAccent

data class AchievementData(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val rarity: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointsScreen(
    onClose: () -> Unit,
    viewModel: PointsViewModel = hiltViewModel()
) {
    val points by viewModel.points.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    val xpProgress by viewModel.xpProgress.collectAsStateWithLifecycle()
    val totalGamesPlayed by viewModel.totalGamesPlayed.collectAsStateWithLifecycle()
    val totalWins by viewModel.totalWins.collectAsStateWithLifecycle()
    val gameStats by viewModel.gameStats.collectAsStateWithLifecycle()
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val affection by viewModel.affection.collectAsStateWithLifecycle()
    val currentDemand by viewModel.currentDemand.collectAsStateWithLifecycle()
    val botStatus = viewModel.botStatus()
    val (currentXp, nextXp) = viewModel.getLevelProgress()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Profile", "Achievements", "Stats")

    CupCakeTheme {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Rewards") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tab Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = selectedTab == index
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .height(56.dp),
                            onClick = { selectedTab = index },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                when (selectedTab) {
                    0 -> ProfileTab(
                        points = points,
                        level = level,
                        xpProgress = xpProgress,
                        currentXp = currentXp,
                        nextXp = nextXp,
                        totalGamesPlayed = totalGamesPlayed,
                        totalWins = totalWins,
                        botStatus = botStatus,
                        affection = affection,
                        demandTitle = currentDemand?.title,
                        demandCost = currentDemand?.cost,
                        onFulfill = { viewModel.fulfillDemand() }
                    )
                    1 -> AchievementsTab(achievements = achievements)
                    else -> StatsTab(
                        gameStats = gameStats,
                        totalGamesPlayed = totalGamesPlayed,
                        totalWins = totalWins
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileTab(
    points: Long,
    level: Int,
    xpProgress: Float,
    currentXp: Long,
    nextXp: Long,
    totalGamesPlayed: Int,
    totalWins: Int,
    botStatus: com.cupcake.game.GameManager.BotStatus,
    affection: Int,
    demandTitle: String?,
    demandCost: Long?,
    onFulfill: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CuteCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🏆", fontSize = 64.sp)
                Text(
                    text = "Level $level",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "💖 $points total points",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // XP Progress Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("XP to next level", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("$currentXp / $nextXp XP", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = xpProgress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(6.dp)
                                )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Level ${level + 1} needs ${nextXp - currentXp} more XP",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Quick Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                emoji = "🎮",
                label = "Games",
                value = totalGamesPlayed.toString(),
                color = MaterialTheme.colorScheme.primary
            )
            StatCard(
                emoji = "⭐",
                label = "Wins",
                value = totalWins.toString(),
                color = MaterialTheme.colorScheme.secondary
            )
            StatCard(
                emoji = "🔥",
                label = "Win Rate",
                value = if (totalGamesPlayed > 0) "${((totalWins.toFloat() / totalGamesPlayed) * 100).toInt()}%" else "0%",
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        // Marin vs You
        CuteCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("💢 Marin vs You", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text("Marin: ${botStatus.botWins} wins", fontWeight = FontWeight.SemiBold)
                    Text("You: ${botStatus.playerWins} wins", fontWeight = FontWeight.SemiBold)
                }
                val headToHead = botStatus.botWins + botStatus.playerWins
                Text(
                    text = if (headToHead == 0)
                        "No games settled yet — challenge her!"
                    else if (botStatus.playerWins > botStatus.botWins)
                        "You're ahead by ${botStatus.playerWins - botStatus.botWins}. She is NOT happy about it."
                    else if (botStatus.botWins > botStatus.playerWins)
                        "Marin leads by ${botStatus.botWins - botStatus.playerWins}. Smug Marin noises."
                    else
                        "Dead even. She wants a rematch right now.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Mood: ${botStatus.mood.lowercase().replaceFirstChar { it.uppercase() }} • Skill: ${botStatus.difficulty.lowercase()}",
                    fontSize = 13.sp
                )
                Text("Anger ${(botStatus.anger * 100).toInt()}% • Jealousy ${(botStatus.jealousy * 100).toInt()}%", fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = { botStatus.anger.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // Hunger / cravings
        CuteCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🍰 Hunger & Cravings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Affection: $affection", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (demandTitle != null && demandCost != null) {
                    Text("$demandTitle — $demandCost coins", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = onFulfill,
                        enabled = points >= demandCost
                    ) {
                        Text(if (points >= demandCost) "Feed her ($demandCost)" else "Need ${demandCost - points} more coins")
                    }
                } else {
                    Text("She's content for now. Win games to earn coins before she gets hungry.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Level Rewards Preview
        CuteCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("✨ Level Rewards", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Unlock cute rewards as you level up!",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LevelRewardIcon(level = 1, title = "Newbie", emoji = "🏫", unlocked = level >= 1)
                    LevelRewardIcon(level = 5, title = "Rising", emoji = "🧠", unlocked = level >= 5)
                    LevelRewardIcon(level = 10, title = "Pro", emoji = "💼", unlocked = level >= 10)
                    LevelRewardIcon(level = 25, title = "Master", emoji = "🏆", unlocked = level >= 25)
                }
            }
        }
    }
}

@Composable
fun RowScope.StatCard(
    emoji: String,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = emoji, fontSize = 28.sp)
            Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = label, fontSize = 12.sp, color = color.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun RowScope.LevelRewardIcon(
    level: Int,
    title: String,
    emoji: String,
    unlocked: Boolean
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = emoji, fontSize = 28.sp)
            Text(
                text = if (unlocked) "✨ $title" else "🔒 $title",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (unlocked)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "Level $level",
                fontSize = 11.sp,
                color = if (unlocked)
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun AchievementsTab(achievements: Set<String>) {
    val achievementData = remember {
        listOf(
            AchievementData("first_win", "First Victory", "Win your first game", Icons.Filled.EmojiEvents, "common"),
            AchievementData("casual_player", "Casual Player", "Play 10 games", Icons.Filled.Star, "common"),
            AchievementData("regular_player", "Regular Player", "Play 50 games", Icons.Filled.Star, "rare"),
            AchievementData("dedicated_player", "Dedicated Player", "Play 100 games", Icons.Filled.EmojiEvents, "rare"),
            AchievementData("hardcore_player", "Hardcore Player", "Play 500 games", Icons.Filled.EmojiEvents, "epic"),
            AchievementData("legendary_player", "Legendary", "Play 1000 games", Icons.Filled.EmojiEvents, "legendary"),
            AchievementData("point_collector", "Point Collector", "Earn 1,000 points", Icons.Filled.Star, "common"),
            AchievementData("point_hoarder", "Point Hoarder", "Earn 10,000 points", Icons.Filled.Star, "rare"),
            AchievementData("point_millionaire", "Point Millionaire", "Earn 100,000 points", Icons.Filled.EmojiEvents, "legendary"),
            AchievementData("polymath", "Polymath", "Try all 10 games", Icons.Filled.EmojiEvents, "epic")
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        items(achievementData) { data ->
            AchievementCard(
                data = data,
                unlocked = achievements.contains(data.id)
            )
        }
    }
}

@Composable
fun AchievementCard(data: AchievementData, unlocked: Boolean) {
    val (bgColor, borderColor, iconColor) = when (data.rarity) {
        "common" -> Triple(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.primary)
        "rare" -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondary)
        "epic" -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiary)
        "legendary" -> Triple(GoldAccent.copy(alpha = 0.2f), GoldAccent, GoldAccent)
        else -> Triple(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.primary)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) bgColor else MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(20.dp),
        border = if (unlocked) BorderStroke(2.dp, borderColor) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = data.icon,
                    contentDescription = null,
                    tint = if (unlocked) iconColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (unlocked) data.title else "???",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    text = if (unlocked) data.description else "Play to unlock!",
                    fontSize = 12.sp,
                    color = if (unlocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }

            if (unlocked) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = "Unlocked",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun StatsTab(
    gameStats: Map<com.cupcake.game.Game.GameType, PointsManager.GameStats>,
    totalGamesPlayed: Int,
    totalWins: Int
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        items(com.cupcake.game.Game.GameType.entries) { gameType ->
            val stats = gameStats[gameType]
            if (stats != null && stats.gamesPlayed > 0) {
                GameStatCard(
                    gameType = gameType,
                    stats = stats,
                    winRate = (stats.wins.toFloat() / stats.gamesPlayed * 100).toInt()
                )
            }
        }
    }
}

@Composable
fun GameStatCard(
    gameType: com.cupcake.game.Game.GameType,
    stats: PointsManager.GameStats,
    winRate: Int
) {
    val (emoji, color) = when (gameType) {
        com.cupcake.game.Game.GameType.TIC_TAC_TOE -> "❌" to MaterialTheme.colorScheme.primary
        com.cupcake.game.Game.GameType.TIC_TAC_TOE_4X4 -> "❌" to MaterialTheme.colorScheme.primary
        com.cupcake.game.Game.GameType.CONNECT_FOUR -> "🔴" to MaterialTheme.colorScheme.secondary
        com.cupcake.game.Game.GameType.CHESS -> "♟️" to MaterialTheme.colorScheme.tertiary
        com.cupcake.game.Game.GameType.ROCK_PAPER_SCISSORS -> "✂️" to MaterialTheme.colorScheme.error
        com.cupcake.game.Game.GameType.GUESS_NUMBER -> "🔢" to MaterialTheme.colorScheme.tertiary
        com.cupcake.game.Game.GameType.MATH_QUIZ -> "🧮" to MaterialTheme.colorScheme.secondary
        com.cupcake.game.Game.GameType.NUMBER_BASE -> "💾" to MaterialTheme.colorScheme.tertiary
        com.cupcake.game.Game.GameType.MEMORY_MATCH -> "🃏" to MaterialTheme.colorScheme.primary
        com.cupcake.game.Game.GameType.HANGMAN -> "🎯" to MaterialTheme.colorScheme.error
        com.cupcake.game.Game.GameType.WORD_SCRAMBLE -> "🔤" to MaterialTheme.colorScheme.secondary
        com.cupcake.game.Game.GameType.MASTERMIND -> "🔐" to MaterialTheme.colorScheme.tertiary
        com.cupcake.game.Game.GameType.REACTION_TIME -> "⚡" to MaterialTheme.colorScheme.error
        com.cupcake.game.Game.GameType.CLICKER -> "🧁" to MaterialTheme.colorScheme.primary
        com.cupcake.game.Game.GameType.PING_PONG -> "🏓" to MaterialTheme.colorScheme.secondary
    }

    CuteCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 28.sp)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = gameType.name.replace("_", " "), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "${stats.wins}/${stats.gamesPlayed} wins • $winRate% win rate",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = stats.bestScore.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
                Text(text = "Best", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
