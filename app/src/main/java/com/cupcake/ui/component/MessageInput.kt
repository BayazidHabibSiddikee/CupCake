package com.cupcake.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInput(
    onSend: (String) -> Unit,
    enabled: Boolean = true
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
        // Text field
        androidx.compose.material3.TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp, max = 120.dp),
            enabled = enabled,
            singleLine = false,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSend = {
                    if (text.isNotBlank() && enabled) {
                        onSend(text.trim())
                        text = ""
                    }
                }
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
                IconButton(
                    onClick = {
                        onSend(text.trim())
                        text = ""
                    },
                    enabled = text.isNotBlank() && enabled
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}