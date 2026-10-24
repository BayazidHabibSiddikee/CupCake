package com.cupcake.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cupcake.chat.MessagingBus
import com.cupcake.stt.SpeechToTextManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Composable
fun MessageInput(
    onSend: (String) -> Unit,
    enabled: Boolean = true,
    /** Optional speech-to-text callback. When provided, a mic button is shown and the
     * finalized transcript is published to the caller (e.g. MessagingBus). */
    onSpeechResult: ((String) -> Unit)? = null
) {
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sttManager = remember(context) { SpeechToTextManager(context) }
    DisposableEffect(sttManager) {
        onDispose { sttManager.destroy() }
    }

    val listening by sttManager.isListening.collectAsState(initial = false)
    val transcript by sttManager.transcript.collectAsState(initial = null as String?)
    var lastPublished by remember { mutableStateOf("") }

    // Publish a finalized transcript to the caller once a listening burst ends.
    // Partials (listening == true) are intentionally not published — only text that
    // survived a final result / end-of-speech is sent. Errors preserve the text so
    // nothing the user said is silently dropped.
    val speechState = combine(sttManager.isListening, sttManager.transcript) { listening, text -> listening to text }
    LaunchedEffect(Unit) {
        speechState.collect { (listening, text) ->
            val nonBlank = text?.takeIf { it.isNotBlank() }
            if (!listening && nonBlank != null && nonBlank != lastPublished) {
                lastPublished = nonBlank
                onSpeechResult?.invoke(nonBlank)
                sttManager.clearTranscript()
                lastPublished = ""
            }
        }
    }

    fun toggleMic() {
        if (listening) {
            sttManager.stopListening()
        } else {
            sttManager.startListening()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Action button (Media / Add icon)
            IconButton(
                onClick = { /* Media attachment hook */ },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AddCircle,
                    contentDescription = "Actions",
                    tint = MessengerBlue,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Mic / voice input (speech-to-text)
            if (onSpeechResult != null && enabled) {
                IconButton(
                    onClick = { toggleMic() },
                    enabled = enabled,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = if (listening) "Stop voice input" else "Start voice input",
                        tint = if (listening) MessengerBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Messenger Input Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Aa",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 20.dp, max = 100.dp),
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MessengerBlue),
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        keyboardType = KeyboardType.Text
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (text.isNotBlank() && enabled) {
                                onSend(text.trim())
                                text = ""
                            }
                        }
                    )
                )
            }

            // Right Action: Messenger Thumbs Up 👍 when empty, Blue Send button when text typed
            if (text.isBlank()) {
                IconButton(
                    onClick = {
                        if (enabled) {
                            onSend("👍")
                        }
                    },
                    modifier = Modifier.size(38.dp),
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = Icons.Filled.ThumbUp,
                        contentDescription = "Like",
                        tint = MessengerBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        if (text.isNotBlank() && enabled) {
                            onSend(text.trim())
                            text = ""
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MessengerBlue),
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
