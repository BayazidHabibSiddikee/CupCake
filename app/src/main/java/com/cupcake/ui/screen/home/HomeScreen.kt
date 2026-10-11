package com.cupcake.ui.screen.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cupcake.data.model.Character
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
    onNavigateToGames: () -> Unit,
    onNavigateToPoints: () -> Unit
) {
    val characters by viewModel.characters.collectAsStateWithLifecycle()

    CupCakeTheme {
        Scaffold(
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chats",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onNavigateToPoints) {
                                Icon(
                                    imageVector = Icons.Filled.EmojiEvents,
                                    contentDescription = "Rewards",
                                    tint = MessengerBlue
                                )
                            }
                            IconButton(onClick = onNavigateToGames) {
                                Icon(
                                    imageVector = Icons.Filled.VideogameAsset,
                                    contentDescription = "Games",
                                    tint = MessengerBlue
                                )
                            }
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
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onNavigateToCharacters,
                    containerColor = MessengerBlue,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "New Chat / Characters"
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (characters.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("💬", fontSize = 64.sp)
                            Text("No chats yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                "Tap the edit button below to create\nor pick a companion!",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        // Messenger Active Stories Carousel
                        item {
                            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Add Character Quick Action Bubble
                                    item {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .width(64.dp)
                                                .clickable { onNavigateToCharacters() }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Add,
                                                    contentDescription = "Add",
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "New",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Active companions
                                    items(characters, key = { "active_${it.id}" }) { character ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .width(64.dp)
                                                .clickable { onNavigateToChat(character.id) }
                                        ) {
                                            Box(contentAlignment = Alignment.BottomEnd) {
                                                CharacterAvatar(
                                                    avatar = character.avatar,
                                                    modifier = Modifier.size(56.dp),
                                                    emojiSize = 28.sp
                                                )
                                                // Green online status indicator
                                                Box(
                                                    modifier = Modifier
                                                        .size(15.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White)
                                                        .padding(2.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF31A24C))
                                                )
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = character.name.split(" ").firstOrNull() ?: character.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Divider line
                        item {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                            )
                        }

                        // Messenger Conversation List Items
                        items(characters, key = { it.id }) { character ->
                            MessengerConversationRow(
                                character = character,
                                onClick = { onNavigateToChat(character.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessengerConversationRow(
    character: Character,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with online badge
        Box(contentAlignment = Alignment.BottomEnd) {
            CharacterAvatar(
                avatar = character.avatar,
                modifier = Modifier.size(56.dp),
                emojiSize = 28.sp
            )
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF31A24C))
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = character.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = character.description.ifBlank { "Active now" },
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}