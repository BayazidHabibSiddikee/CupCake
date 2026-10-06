package com.cupcake.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cupcake.data.model.ChatMessage
import com.cupcake.ui.theme.CupCakeTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SmartToy

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    isStreaming: Boolean = false
) {
    val isUser = message.role == ChatMessage.MessageRole.USER
    val isSystem = message.role == ChatMessage.MessageRole.SYSTEM

    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = when {
                    isUser -> androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                    isSystem -> androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
                    else -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            )
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
                        imageVector = when {
                            isSystem -> Icons.Filled.Description
                            else -> Icons.Filled.SmartToy
                        },
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
                },
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
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 0),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha3"
    )

    val dotColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = dotColor.copy(alpha = alpha1), radius = 4f)
        }
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = dotColor.copy(alpha = alpha2), radius = 4f)
        }
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = dotColor.copy(alpha = alpha3), radius = 4f)
        }
    }
}