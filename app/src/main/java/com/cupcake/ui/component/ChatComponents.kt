package com.cupcake.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cupcake.data.model.ChatMessage
import com.cupcake.ui.theme.CupCakeTheme

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    isStreaming: Boolean = false
) {
    val isUser = message.role == ChatMessage.MessageRole.USER
    val isSystem = message.role == ChatMessage.MessageRole.SYSTEM

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = when {
                isUser -> androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                isSystem -> androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
                else -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Role indicator
            if (!isUser) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        painter = painterResource(id = when {
                            isSystem -> androidx.compose.material.icons.Icons.Filled.Description
                            else -> androidx.compose.material.icons.Icons.Filled.SmartToy
                        }),
                        contentDescription = null,
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when {
                            isSystem -> "System Prompt"
                            else -> "Assistant"
                        },
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                }
            }

            // Message content
            Text(
                text = message.content,
                fontSize = 14.sp,
                color = when {
                    isUser -> androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                    isSystem -> androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
                    else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                ),
                textAlign = if (isUser) TextAlign.End else TextAlign.Start
            )

            // Metadata
            if (message.tokenCount > 0 || message.latencyMs > 0) {
                Text(
                    text = "${message.tokenCount} tokens • ${message.latencyMs}ms • ${message.modelUsed}",
                    fontSize = 10.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Streaming indicator
            if (isStreaming) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TypingIndicator()
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val alpha1 by infiniteTransition.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        animation = androidx.compose.animation.core.tween(600, delayMillis = 0),
        repeatMode = androidx.compose.animation.core.RepeatMode.REVERSE
    ))
    val alpha2 by infiniteTransition.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        animation = androidx.compose.animation.core.tween(600, delayMillis = 200),
        repeatMode = androidx.compose.animation.core.RepeatMode.REVERSE
    ))
    val alpha3 by infiniteTransition.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        animation = androidx.compose.animation.core.tween(600, delayMillis = 400),
        repeatMode = androidx.compose.animation.core.RepeatMode.REVERSE
    ))

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
            val color = androidx.compose.material3.MaterialTheme.colorScheme.primary
            drawCircle(color = color.copy(alpha = alpha1), radius = 4f)
        }
        androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
            val color = androidx.compose.material3.MaterialTheme.colorScheme.primary
            drawCircle(color = color.copy(alpha = alpha2), radius = 4f)
        }
        androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
            val color = androidx.compose.material3.MaterialTheme.colorScheme.primary
            drawCircle(color = color.copy(alpha = alpha3), radius = 4f)
        }
    }
}