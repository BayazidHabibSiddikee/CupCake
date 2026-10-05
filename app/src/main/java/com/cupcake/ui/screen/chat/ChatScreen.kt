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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.cupcake.data.model.ModelConfig
import com.cupcake.data.model.SystemPrompt
import com.cupcake.ui.component.ChatMessageItem
import com.cupcake.ui.component.MessageInput
import com.cupcake.ui.theme.CupCakeTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val currentResponse by viewModel.currentResponse.collectAsStateWithLifecycle()
    val modelConfig by viewModel.modelConfig.collectAsStateWithLifecycle()
    val systemPrompt by viewModel.systemPrompt.collectAsStateWithLifecycle()
    val modelLoadState by viewModel.modelLoadState.collectAsStateWithLifecycle()
    val isModelLoading = modelLoadState is ChatViewModel.ModelLoadState.Loading

    var scrollToBottom by remember { mutableStateOf(false) }

    CupCakeTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with model info
            ChatHeader(
                modelConfig = modelConfig,
                systemPrompt = systemPrompt,
                onModelConfigClick = { viewModel.onModelConfigClick() },
                onSystemPromptClick = { viewModel.onSystemPromptClick() }
            )

            // Model load status (first run copies ~500MB from assets)
            when (val state = modelLoadState) {
                is ChatViewModel.ModelLoadState.Loading -> {
                    Text(
                        text = "⏳ Loading on-device model…",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                is ChatViewModel.ModelLoadState.Error -> {
                    Text(
                        text = "❌ Model: ${state.message}",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                else -> {}
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
                            message = ChatMessage.assistant(currentResponse, conversationId),
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
fun ChatHeader(
    modelConfig: ModelConfig,
    systemPrompt: SystemPrompt?,
    onModelConfigClick: () -> Unit,
    onSystemPromptClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Model indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                androidx.compose.material3.Text(
                    text = when (modelConfig.provider) {
                        ModelConfig.ModelProvider.LOCAL_QWEN -> "📱 Local: ${modelConfig.modelName}"
                        ModelConfig.ModelProvider.CUSTOM_OPENAI -> "☁️ OpenAI: ${modelConfig.modelName}"
                        ModelConfig.ModelProvider.CUSTOM_OLLAMA -> "🦙 Ollama: ${modelConfig.modelName}"
                        ModelConfig.ModelProvider.CUSTOM_VLLM -> "⚡ vLLM: ${modelConfig.modelName}"
                        else -> "🔗 Custom: ${modelConfig.modelName}"
                    },
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(onClick = onModelConfigClick) {
                    Icon(imageVector = Icons.Filled.Settings, contentDescription = "Model config")
                }
            }

            // System prompt indicator
            systemPrompt?.let { prompt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    androidx.compose.material3.Text(
                        text = "📋 ${prompt.name} (${prompt.images.size} images)",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(onClick = onSystemPromptClick) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = "Edit prompt")
                    }
                }
            }
        }
    }
}