package com.cupcake.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cupcake.data.model.Conversation
import com.cupcake.ui.theme.CupCakeTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToChat: (String) -> Unit,
    onNavigateToDevice: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCharacters: () -> Unit,
    onNavigateToGames: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val showNewChatDialog by viewModel.showNewChatDialog.collectAsStateWithLifecycle()
    var newChatTitle by remember { mutableStateOf("") }

    CupCakeTheme {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("CupCake") },
                actions = {
                    androidx.compose.material3.IconButton(onClick = onNavigateToCharacters) {
                        Icon(painterResource(androidx.compose.material.icons.Icons.Filled.Psychology), contentDescription = "Characters")
                    }
                    androidx.compose.material3.IconButton(onClick = onNavigateToGames) {
                        Icon(painterResource(androidx.compose.material.icons.Icons.Filled.VideogameAsset), contentDescription = "Games")
                    }
                    androidx.compose.material3.IconButton(onClick = onNavigateToSettings) {
                        Icon(painterResource(androidx.compose.material.icons.Icons.Filled.Settings), contentDescription = "Settings")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
                )
            )

            Box(Modifier.fillMaxSize()) {
                if (conversations.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                painter = painterResource(androidx.compose.material.icons.Icons.Filled.ChatBubbleOutline),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("No conversations yet", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            Text("Start a new chat or connect a device", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(onClick = { viewModel.showNewChatDialog.value = true }) {
                                    Text("New Chat")
                                }
                                androidx.compose.material3.OutlinedButton(onClick = onNavigateToCharacters) {
                                    Icon(painterResource(androidx.compose.material.icons.Icons.Filled.Psychology), contentDescription = null)
                                    Text("Choose Character")
                                }
                                androidx.compose.material3.OutlinedButton(onClick = onNavigateToGames) {
                                    Icon(painterResource(androidx.compose.material.icons.Icons.Filled.VideogameAsset), contentDescription = null)
                                    Text("Play Games")
                                }
                            }
                        }
                    }
                } else {
                    // Conversations list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(conversations) { conversation ->
                            ConversationCard(
                                conversation = conversation,
                                onClick = { onNavigateToChat(conversation.id) },
                                onLongClick = { /* show options */ }
                            )
                        }
                    }
                }

                // FAB for new chat
                androidx.compose.material3.FloatingActionButton(
                    onClick = { viewModel.showNewChatDialog.value = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Icon(painterResource(androidx.compose.material.icons.Icons.Filled.Add), contentDescription = "New chat")
                }
            }
        }

        // New chat dialog
        if (showNewChatDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.showNewChatDialog.value = false },
                confirmButton = {
                    Button(onClick = {
                        viewModel.createConversation(newChatTitle.ifBlank { "New Chat" })
                        viewModel.showNewChatDialog.value = false
                        newChatTitle = ""
                    }, enabled = newChatTitle.isNotBlank()) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    Button(onClick = { viewModel.showNewChatDialog.value = false; newChatTitle = "" }) {
                        Text("Cancel")
                    }
                },
                title = { Text("New Conversation") },
                text = {
                    androidx.compose.material3.TextField(
                        value = newChatTitle,
                        onValueChange = { newChatTitle = it },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        label = { Text("Title (optional)") },
                        singleLine = true
                    )
                }
            )
        }
    }
}

@Composable
fun ConversationCard(
    conversation: Conversation,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxWidth(),
        onClick = onClick
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(conversation.title, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(conversation.updatedAt.toString(), fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            if (conversation.systemPrompt != null) {
                Text(
                    "📋 ${conversation.systemPrompt!!.name}",
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Text(
                "${conversation.messageCount} messages • ${conversation.modelConfig.provider.value}",
                fontSize = 12.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}