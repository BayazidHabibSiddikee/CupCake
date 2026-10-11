package com.cupcake.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun CharacterAvatar(
    avatar: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    emojiSize: TextUnit = 24.sp
) {
    val isImageUri = avatar.startsWith("content://") ||
            avatar.startsWith("file://") ||
            avatar.startsWith("/") ||
            avatar.startsWith("http://") ||
            avatar.startsWith("https://") ||
            avatar.startsWith("android.resource://") ||
            avatar.endsWith(".png", ignoreCase = true) ||
            avatar.endsWith(".jpg", ignoreCase = true) ||
            avatar.endsWith(".jpeg", ignoreCase = true) ||
            avatar.endsWith(".gif", ignoreCase = true) ||
            avatar.endsWith(".webp", ignoreCase = true)

    val imageModel = when {
        avatar == "companion_hero" -> "file:///android_asset/companion_hero.jpg"
        avatar.startsWith("/") -> "file://$avatar"
        else -> avatar
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (isImageUri || avatar == "companion_hero") {
            AsyncImage(
                model = imageModel,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = avatar.ifBlank { "🤖" },
                fontSize = emojiSize
            )
        }
    }
}
