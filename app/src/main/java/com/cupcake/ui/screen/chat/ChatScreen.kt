package com.cupcake.ui.screen.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.ModelConfig
import com.cupcake.ui.component.ChatMessageItem
import com.cupcake.ui.component.MessageInput
import com.cupcake.ui.theme.CupCakeTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val currentResponse by viewModel.currentResponse.collectAsStateWithLifecycle()
    val modelConfig by viewModel.modelConfig.collectAsStateWithLifecycle()
    val systemPrompt by viewModel.systemPrompt.collectAsStateWithLifecycle()

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
                enabled = !isGenerating,
                onAttachImage = { viewModel.onAttachImage() }
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
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Model indicator
            androidx.compose.material3.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
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
                    androidx.compose.material.icons.Icons.Filled.Settings
                }
            }

            // System prompt indicator
            systemPrompt?.let { prompt ->
                androidx.compose.material3.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                ) {
                    androidx.compose.material3.Text(
                        text = "📋 ${prompt.name} (${prompt.images.size} images)",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(onClick = onSystemPromptClick) {
                        androidx.compose.material.icons.Icons.Filled.Edit
                    }
                }
            }
        }
    }
}