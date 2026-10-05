package com.cupcake.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Send

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInput(
    onSend: (String) -> Unit,
    enabled: Boolean = true,
    onAttachImage: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest,
        unfocusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
        disabledContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
        focusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
        focusedPlaceholderColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        focusedLeadingIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedLeadingIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTrailingIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        unfocusedTrailingIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        cursorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Attach image button
        onAttachImage?.let {
            IconButton(onClick = it, enabled = enabled) {
                Icon(
                    imageVector = Icons.Filled.AddPhotoAlternate,
                    contentDescription = "Attach image",
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
            }
        }

        // Text field
        androidx.compose.material3.TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = enabled,
            singleLine = false,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text
            ),
            visualTransformation = VisualTransformation.None,
            colors = textFieldColors,
            placeholder = { Text("Message...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Chat,
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (text.isNotBlank() && enabled) {
                    IconButton(onClick = {
                        onSend(text.trim())
                        text = ""
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Voice input",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}