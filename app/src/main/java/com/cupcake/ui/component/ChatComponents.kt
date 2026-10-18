package com.cupcake.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cupcake.data.model.ChatMessage

// Signature Messenger Blue
val MessengerBlue = Color(0xFF0084FF)
val MessengerBlueDark = Color(0xFF0072E5)

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    characterAvatar: String? = null,
    isStreaming: Boolean = false
) {
    val isUser = message.role == ChatMessage.MessageRole.USER
    val isSystem = message.role == ChatMessage.MessageRole.SYSTEM

    when {
        isSystem -> {
            // Messenger centered system notice pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = message.content,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        isUser -> {
            // Messenger User message: Blue gradient bubble, right aligned, white text
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 290.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = 18.dp,
                                bottomEnd = 4.dp
                            )
                        )
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(MessengerBlue, MessengerBlueDark)
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.content,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = Color.White
                    )
                }
            }
        }

        else -> {
            // Messenger Bot message: Left aligned with character avatar, clean grey bubble
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 56.dp, top = 2.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                if (characterAvatar != null) {
                    CharacterAvatar(
                        avatar = characterAvatar,
                        modifier = Modifier.size(28.dp),
                        emojiSize = 16.sp
                    )
                    Spacer(Modifier.size(8.dp))
                } else {
                    Spacer(Modifier.size(4.dp))
                }

                Column(modifier = Modifier.widthIn(max = 285.dp)) {
                    val displayText = remember(message.content) {
                        message.content.replaceFirst(Regex("^\\s*\\[[a-zA-Z_]+\\]\\s*"), "")
                    }

                    Box(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = 4.dp,
                                    bottomEnd = 18.dp
                                )
                            )
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = displayText,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isStreaming) {
                        Box(modifier = Modifier.padding(top = 4.dp, start = 4.dp)) {
                            TypingIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessengerTypingBubble(
    characterAvatar: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 60.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (characterAvatar != null) {
            CharacterAvatar(
                avatar = characterAvatar,
                modifier = Modifier.size(28.dp),
                emojiSize = 16.sp
            )
            Spacer(Modifier.size(8.dp))
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            TypingIndicator()
        }
    }
}

@Composable
fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "messenger_typing")

    val bounce1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce1"
    )
    val bounce2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce2"
    )
    val bounce3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce3"
    )

    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .offset(y = bounce1.dp)
                .size(7.dp)
                .background(dotColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .offset(y = bounce2.dp)
                .size(7.dp)
                .background(dotColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .offset(y = bounce3.dp)
                .size(7.dp)
                .background(dotColor, CircleShape)
        )
    }
}