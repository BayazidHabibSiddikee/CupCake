package com.cupcake.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cupcake.data.model.ChatMessage
import com.cupcake.ui.component.CharacterAvatar
import com.cupcake.ui.component.ChatMessageItem
import com.cupcake.ui.component.MessageInput
import com.cupcake.ui.component.MessengerBlue
import com.cupcake.ui.component.MessengerTypingBubble
import com.cupcake.ui.theme.CupCakeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    characterId: String,
    onSwitchCharacter: (String) -> Unit,
    onBack: () -> Unit,
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
    var showVrm3dView by remember { mutableStateOf(false) }

    CupCakeTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Character header
            CharacterHeader(
                characterName = currentCharacter?.name ?: "Companion",
                characterAvatar = currentCharacter?.avatar ?: "🤖",
                isVrmActive = showVrm3dView,
                onToggleVrm = { showVrm3dView = !showVrm3dView },
                onSwitchClick = { showCharacterPicker = true },
                onBackClick = onBack
            )

            // 3D VRM Model Viewport (Toggled directly from header)
            if (showVrm3dView) {
                var isWalkingMode by remember { mutableStateOf(false) }
                val detectedEmotion = remember(currentResponse, messages.size) {
                    val companionText = if (currentResponse.isNotBlank()) {
                        currentResponse
                    } else {
                        messages.lastOrNull { it.role == com.cupcake.data.model.ChatMessage.MessageRole.ASSISTANT }?.content
                            ?: messages.lastOrNull()?.content
                            ?: ""
                    }
                    com.cupcake.ai.EmotionClassifier.detectEmotion(companionText)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.cupcake.ui.component.VrmViewer(
                            modifier = Modifier.fillMaxSize(),
                            isGenerating = isGenerating,
                            emotion = detectedEmotion.vrmExpression,
                            isWalking = isWalkingMode,
                            audioBeat = if (isGenerating) 0.5f else 0.0f
                        )

                        // 3D Motion Toolbar Overlay (Walk / Roam / Emotion badge)
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Emotion: ${detectedEmotion.name.lowercase().capitalize()}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            androidx.compose.material3.FilledTonalButton(
                                onClick = { isWalkingMode = !isWalkingMode },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(if (isWalkingMode) "Walk: ON 🐾" else "Walk: OFF 🛑", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️ Ollama: ${state.message}",
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.TextButton(
                            onClick = { viewModel.ensureModelLoaded() }
                        ) {
                            Text("Retry", fontSize = 12.sp)
                        }
                    }
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // When generating and no tokens received yet -> show bouncing 3-dot Messenger typing indicator
                if (isGenerating && currentResponse.isEmpty()) {
                    item(key = "typing_indicator") {
                        com.cupcake.ui.component.MessengerTypingBubble(
                            characterAvatar = currentCharacter?.avatar
                        )
                    }
                }

                // Current streaming response at bottom
                if (currentResponse.isNotEmpty()) {
                    item(key = "streaming_response") {
                        ChatMessageItem(
                            message = ChatMessage.assistant(currentResponse, viewModel.conversationId),
                            characterAvatar = currentCharacter?.avatar,
                            isStreaming = true
                        )
                    }
                }

                items(
                    count = messages.size,
                    key = { i ->
                        val msg = messages[messages.lastIndex - i]
                        if (msg.id > 0) msg.id else "${msg.timestamp}_${messages.lastIndex - i}"
                    }
                ) { i ->
                    val message = messages[messages.lastIndex - i]
                    val isAssistant = message.role == ChatMessage.MessageRole.ASSISTANT
                    val emotionImage = if (isAssistant) {
                        remember(message.content) {
                            com.cupcake.ai.EmotionAssets.assetForText(message.content)
                        }
                    } else null
                    ChatMessageItem(
                        message = message,
                        characterAvatar = currentCharacter?.avatar,
                        emotionImage = emotionImage
                    )
                }
            }

            // Input area
            MessageInput(
                onSend = { text ->
                    viewModel.sendMessage(text)
                },
                enabled = !isGenerating
            )
        }
    }
}

@Composable
fun CharacterHeader(
    characterName: String,
    characterAvatar: String,
    isVrmActive: Boolean = false,
    onToggleVrm: () -> Unit = {},
    onSwitchClick: () -> Unit,
    onBackClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                androidx.compose.material3.IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = com.cupcake.ui.component.MessengerBlue
                    )
                }
                
                Box(contentAlignment = Alignment.BottomEnd) {
                    CharacterAvatar(
                        avatar = characterAvatar,
                        modifier = Modifier.size(40.dp),
                        emojiSize = 22.sp
                    )
                    // Online Green Dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(1.5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF31A24C))
                    )
                }
                
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(
                        text = characterName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF31A24C))
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = "Active now",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 3D VRM Model Viewport Toggle Button
                androidx.compose.material3.IconButton(
                    onClick = onToggleVrm,
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(if (isVrmActive) "👗" else "🧊", fontSize = 18.sp)
                }

                val context = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.material3.IconButton(
                    onClick = {
                        if (android.provider.Settings.canDrawOverlays(context)) {
                            // If running, we can start with current character or toggle
                            com.cupcake.service.ShimejiPetService.start(
                                context = context,
                                avatar = characterAvatar,
                                gif = if (characterName.contains("Marin", ignoreCase = true)) "file:///android_asset/marin/marin_idle.gif" else null,
                                name = characterName
                            )
                            com.cupcake.service.ShimejiPetService.showSpeech(context, "✨ Activated on screen!")
                        } else {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("🐾", fontSize = 18.sp)
                }
                androidx.compose.material3.IconButton(
                    onClick = onSwitchClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.PeopleAlt,
                        contentDescription = "Switch Character",
                        tint = com.cupcake.ui.component.MessengerBlue
                    )
                }
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
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            com.cupcake.ui.component.CharacterAvatar(
                                avatar = character.avatar,
                                modifier = Modifier.size(36.dp),
                                emojiSize = 20.sp
                            )
                            Column {
                                Text(
                                    text = character.name,
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
        }
    )
}