package com.cupcake.ui.screen.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cupcake.data.model.ChatMessage
import com.cupcake.ui.component.ChatMessageItem
import com.cupcake.ui.component.MessageInput
import com.cupcake.ui.theme.CupCakeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    characterId: String,
    onSwitchCharacter: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val currentResponse by viewModel.currentResponse.collectAsStateWithLifecycle()
    val currentCharacter by viewModel.currentCharacter.collectAsStateWithLifecycle()
    val modelLoadState by viewModel.modelLoadState.collectAsStateWithLifecycle()
    val isModelLoading = modelLoadState is ChatViewModel.ModelLoadState.Loading

    var scrollToBottom by remember { mutableStateOf(false) }
    var showCharacterPicker by remember { mutableStateOf(false) }

    CupCakeTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Character header (no model details shown)
            CharacterHeader(
                characterName = currentCharacter?.name ?: "Companion",
                characterAvatar = currentCharacter?.avatar ?: "🤖",
                onSwitchClick = { showCharacterPicker = true }
            )

            // Load status without technical details
            when (val state = modelLoadState) {
                is ChatViewModel.ModelLoadState.Loading -> {
                    val detail = if (state.detail.isNotBlank()) " ${state.detail}" else ""
                    Text(
                        text = "⏳ Getting ready…$detail",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                is ChatViewModel.ModelLoadState.Error -> {
                    Text(
                        text = "❌ Something went wrong. Please restart the app.",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                else -> {}
            }

            if (showCharacterPicker) {
                CharacterPickerDialog(
                    characters = viewModel.getAvailableCharacters(),
                    selectedId = viewModel.characterId,
                    onSelect = { id ->
                        if (id != viewModel.characterId) onSwitchCharacter(id)
                        showCharacterPicker = false
                    },
                    onDismiss = { showCharacterPicker = false }
                )
            }

            // Messages list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                reverseLayout = true,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Current streaming response at bottom
                if (currentResponse.isNotEmpty()) {
                    item {
                        ChatMessageItem(
                            message = ChatMessage.assistant(currentResponse, viewModel.conversationId),
                            isStreaming = true
                        )
                    }
                }

                items(messages.reversed()) { message ->
                    ChatMessageItem(message = message)
                }
            }

            // Input area
            MessageInput(
                onSend = { text ->
                    viewModel.sendMessage(text)
                },
                enabled = !isGenerating && !isModelLoading
            )
        }
    }
}

@Composable
fun CharacterHeader(
    characterName: String,
    characterAvatar: String,
    onSwitchClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$characterAvatar $characterName",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            androidx.compose.material3.TextButton(onClick = onSwitchClick) {
                Text("Switch")
            }
        }
    }
}

@Composable
fun CharacterPickerDialog(
    characters: List<com.cupcake.data.model.Character>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("Choose companion") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(characters) { character ->
                    androidx.compose.material3.Card(
                        onClick = { onSelect(character.id) },
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = if (character.id == selectedId)
                                androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                            else
                                androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${character.avatar} ${character.name}",
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = character.description,
                                fontSize = 12.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    )
}