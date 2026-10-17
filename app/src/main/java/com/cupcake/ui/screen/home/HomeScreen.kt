package com.cupcake.ui.screen.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cupcake.data.model.Character
import com.cupcake.service.ShimejiPetService
import com.cupcake.ui.component.CharacterAvatar
import com.cupcake.ui.component.MessengerBlue
import com.cupcake.ui.theme.CupCakeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToChat: (String) -> Unit,
    onNavigateToDevice: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCharacters: () -> Unit,
    onNavigateToGame: (String) -> Unit,
    onNavigateToPoints: () -> Unit
) {
    val context = LocalContext.current
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val points by viewModel.points.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    val xpProgress by viewModel.xpProgress.collectAsStateWithLifecycle()
    val totalGamesPlayed by viewModel.totalGamesPlayed.collectAsStateWithLifecycle()
    val totalWins by viewModel.totalWins.collectAsStateWithLifecycle()
    val energy by viewModel.energy.collectAsStateWithLifecycle()
    val companionQuote by viewModel.companionQuote.collectAsStateWithLifecycle()
    val companionEmotion by viewModel.companionEmotion.collectAsStateWithLifecycle()
    val currentDemand by viewModel.currentDemand.collectAsStateWithLifecycle()
    val marinAffection by viewModel.marinAffection.collectAsStateWithLifecycle()

    val activeCompanion = characters.firstOrNull { it.isSelected }
        ?: characters.firstOrNull { it.id == "marin_kitagawa" }
        ?: characters.firstOrNull()
        ?: Character(
            id = "marin_kitagawa",
            name = "Marin Kitagawa",
            description = "Energetic anime & cosplay companion",
            systemPrompt = "You are Marin Kitagawa!",
            avatar = "file:///android_asset/marin/emotions/normal.png",
            personality = "energetic",
            customGif = "file:///android_asset/marin/marin_idle.gif"
        )

    val (currentXp, nextXp) = viewModel.getLevelProgress()

    CupCakeTheme {
        Scaffold(
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onNavigateToCharacters() }
                        ) {
                            Text(
                                text = activeCompanion.name.split(" ").firstOrNull() ?: activeCompanion.name,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "✨",
                                fontSize = 18.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Energy Pill
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Bolt,
                                        contentDescription = "Energy",
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "$energy",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            // Switch Companion / Mascot
                            IconButton(onClick = onNavigateToCharacters) {
                                Text("🎭", fontSize = 20.sp)
                            }

                            // Shimeji Desktop Floating Pet Launcher
                            IconButton(onClick = {
                                if (Settings.canDrawOverlays(context)) {
                                    ShimejiPetService.start(
                                        context = context,
                                        avatar = activeCompanion.avatar,
                                        gif = activeCompanion.customGif,
                                        name = activeCompanion.name
                                    )
                                    ShimejiPetService.showSpeech(context, "Kyaa! ${activeCompanion.name} is on your screen! ✨")
                                } else {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            }) {
                                Text("🐾", fontSize = 20.sp)
                            }

                            // Rewards / Leaderboard
                            IconButton(onClick = onNavigateToPoints) {
                                Icon(
                                    imageVector = Icons.Filled.EmojiEvents,
                                    contentDescription = "Rewards",
                                    tint = MessengerBlue
                                )
                            }

                            // Settings
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // HERO CARD: Interactive Marin Companion Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Companion Avatar with Emotion Glow
                            Box(
                                contentAlignment = Alignment.BottomEnd,
                                modifier = Modifier.clickable {
                                    viewModel.interactWithMarin("pat")
                                }
                            ) {
                                CharacterAvatar(
                                    avatar = activeCompanion.avatar,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .border(3.dp, MessengerBlue, CircleShape),
                                    emojiSize = 48.sp
                                )
                                // Active online heart badge
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(MessengerBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = "Active",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            Text(
                                text = activeCompanion.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Mood & Status Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MessengerBlue.copy(alpha = 0.12f),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = when (companionEmotion) {
                                        "excited" -> "Mood: Excited & Hyped! 💖"
                                        "angry" -> "Mood: Pouting & Jealous! 😤"
                                        else -> "Mood: Loving & Playful 💕"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MessengerBlue,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // Interactive Speech Bubble
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.interactWithMarin("tease")
                                    }
                            ) {
                                Text(
                                    text = "“$companionQuote”",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            // Interactive Micro-Action Chips (Pat, Snack, Pout, Dance)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                AssistChip(
                                    onClick = { viewModel.interactWithMarin("pat") },
                                    label = { Text("Headpat 💕", fontSize = 11.5.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                                AssistChip(
                                    onClick = { viewModel.interactWithMarin("snack") },
                                    label = { Text("Snack 🍰", fontSize = 11.5.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                                AssistChip(
                                    onClick = { viewModel.interactWithMarin("dance") },
                                    label = { Text("Dance 💃", fontSize = 11.5.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                                AssistChip(
                                    onClick = { viewModel.interactWithMarin("pout") },
                                    label = { Text("Tease 💢", fontSize = 11.5.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            // Direct Action Button: Chat with companion
                            Button(
                                onClick = { onNavigateToChat(activeCompanion.id) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MessengerBlue)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Chat,
                                    contentDescription = "Chat",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Chat with ${activeCompanion.name.split(" ").firstOrNull() ?: activeCompanion.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }

                // BOND & SCORE SECTION
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.EmojiEvents,
                                        contentDescription = "Score",
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Bond Level $level",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Text(
                                    text = "$points PTS",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = MessengerBlue
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { xpProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MessengerBlue,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "XP: $currentXp / $nextXp",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Record: $totalWins W / $totalGamesPlayed P",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // MARIN'S DEMAND (costs points to satisfy)
                currentDemand?.let { demand ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(demand.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = "💗 $marinAffection",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(demand.message, fontSize = 14.sp)
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { viewModel.fulfillDemand() },
                                        enabled = points >= demand.cost,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MessengerBlue)
                                    ) {
                                        Text("Give in · ${demand.cost} pts", fontSize = 13.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.ignoreDemand() },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Not now", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // GAMES WITH MARIN SECTION
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.VideogameAsset,
                                contentDescription = "Games",
                                tint = MessengerBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Games with Marin",
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "View All",
                            color = MessengerBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { onNavigateToGame("") }
                        )
                    }
                }

                // Quick Play Game Grid — all 15 games
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GameLaunchCard(
                            emoji = "❌⭕",
                            title = "Tic-Tac-Toe",
                            description = "Play 3×3 against Marin. Watch out for her pouting when she loses!",
                            xpReward = "+100 XP",
                            onClick = { onNavigateToGame("TIC_TAC_TOE") }
                        )
                        GameLaunchCard(
                            emoji = "⭕❌",
                            title = "Tic-Tac-Toe 4×4",
                            description = "Bigger board, bigger brain battle. Can you keep up with Marin?",
                            xpReward = "+150 XP",
                            onClick = { onNavigateToGame("TIC_TAC_TOE_4X4") }
                        )
                        GameLaunchCard(
                            emoji = "🔴🟡",
                            title = "Connect Four",
                            description = "Drop discs and outsmart Marin's sneaky combos!",
                            xpReward = "+150 XP",
                            onClick = { onNavigateToGame("CONNECT_FOUR") }
                        )
                        GameLaunchCard(
                            emoji = "🏓",
                            title = "Ping Pong",
                            description = "Real-time rally! The more you win, the angrier (and faster) Marin gets.",
                            xpReward = "+150 XP",
                            onClick = { onNavigateToGame("PING_PONG") }
                        )
                        GameLaunchCard(
                            emoji = "♟️",
                            title = "Chess Challenge",
                            description = "Tactical chess duel. Can you outsmart Marin's brain?",
                            xpReward = "+200 XP",
                            onClick = { onNavigateToGame("CHESS") }
                        )
                        GameLaunchCard(
                            emoji = "✊✋✌️",
                            title = "Rock Paper Scissors",
                            description = "Fast hands battle! Test your intuition against Marin's strategy.",
                            xpReward = "+80 XP",
                            onClick = { onNavigateToGame("ROCK_PAPER_SCISSORS") }
                        )
                        GameLaunchCard(
                            emoji = "🔢",
                            title = "Guess the Number",
                            description = "Marin picked a number. How quickly can you find it?",
                            xpReward = "+60 XP",
                            onClick = { onNavigateToGame("GUESS_NUMBER") }
                        )
                        GameLaunchCard(
                            emoji = "➕➖✖️➗",
                            title = "Math Quiz",
                            description = "Race Marin through rapid-fire math. Smarter than you think?",
                            xpReward = "+100 XP",
                            onClick = { onNavigateToGame("MATH_QUIZ") }
                        )
                        GameLaunchCard(
                            emoji = "💾",
                            title = "Number Base",
                            description = "Binary, octal, hex — flex your geek muscles with Marin!",
                            xpReward = "+120 XP",
                            onClick = { onNavigateToGame("NUMBER_BASE") }
                        )
                        GameLaunchCard(
                            emoji = "🃏",
                            title = "Memory Match",
                            description = "Match the hidden cards! Win high affection bonuses.",
                            xpReward = "+120 XP",
                            onClick = { onNavigateToGame("MEMORY_MATCH") }
                        )
                        GameLaunchCard(
                            emoji = "🎯",
                            title = "Hangman",
                            description = "Guess letters before the stick figure is complete!",
                            xpReward = "+90 XP",
                            onClick = { onNavigateToGame("HANGMAN") }
                        )
                        GameLaunchCard(
                            emoji = "🔤",
                            title = "Word Scramble",
                            description = "Unscramble Marin's jumbled words as fast as you can!",
                            xpReward = "+90 XP",
                            onClick = { onNavigateToGame("WORD_SCRAMBLE") }
                        )
                        GameLaunchCard(
                            emoji = "🔐",
                            title = "Mastermind",
                            description = "Crack the secret code before your chances run out.",
                            xpReward = "+130 XP",
                            onClick = { onNavigateToGame("MASTERMIND") }
                        )
                        GameLaunchCard(
                            emoji = "⚡",
                            title = "Reaction Time",
                            description = "Tap as fast as you can! Marin loves teasing slowpokes.",
                            xpReward = "+70 XP",
                            onClick = { onNavigateToGame("REACTION_TIME") }
                        )
                        GameLaunchCard(
                            emoji = "🧁",
                            title = "Clicker",
                            description = "Just tap! Simple, addictive, and Marin cheers every click.",
                            xpReward = "+50 XP",
                            onClick = { onNavigateToGame("CLICKER") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GameLaunchCard(
    emoji: String,
    title: String,
    description: String,
    xpReward: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 22.sp)
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MessengerBlue.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = xpReward,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MessengerBlue,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = MessengerBlue,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}