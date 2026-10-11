package com.cupcake.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CreamyPink = Color(0xFFF8BBD0)
private val CreamyPinkLight = Color(0xFFFCE4EC)
private val CreamyPinkDark = Color(0xFFEC407A)
private val SoftPeach = Color(0xFFFFCCBC)
private val SoftPeachLight = Color(0xFFFBE9E7)
private val MintGreen = Color(0xFFA5D6A7)
private val MintGreenLight = Color(0xFFE8F5E9)
private val Lavender = Color(0xFFCE93D8)
private val LavenderLight = Color(0xFFF3E5F5)
private val CreamWhite = Color(0xFFFEFEFE)
private val WarmWhite = Color(0xFFFDFAF5)
private val SoftGray = Color(0xFFBCAAA4)
private val DarkGray = Color(0xFF4E342E)
val GoldAccent = Color(0xFFFFD54F)
private val CoralAccent = Color(0xFFFF8A65)

private val DarkCreamyPink = Color(0xFFAD1457)
private val DarkMintGreen = Color(0xFF2E7D32)
private val DarkLavender = Color(0xFF6A1B9A)
private val DarkSurface = Color(0xFF2D1B2E)
private val DarkSurfaceContainer = Color(0xFF3D2B3E)

private val LightColorScheme = lightColorScheme(
    primary = CreamyPinkDark,
    primaryContainer = CreamyPinkLight,
    secondary = Color(0xFFD84315),
    secondaryContainer = SoftPeachLight,
    tertiary = Color(0xFF6A1B9A),
    tertiaryContainer = LavenderLight,
    surface = CreamWhite,
    surfaceContainerLow = WarmWhite,
    surfaceContainerHigh = Color(0xFFF5E6EC),
    surfaceContainerHighest = Color(0xFFE8D5DE),
    onSurface = DarkGray,
    onSurfaceVariant = Color(0xFF6D4C41),
    outline = Color(0xFFD7BDB4),
    outlineVariant = Color(0xFFE0C8C0),
    error = Color(0xFFE53935),
    errorContainer = Color(0xFFFFEBEE),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFFB71C1C)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF8BBD0),
    primaryContainer = DarkCreamyPink,
    secondary = CoralAccent,
    secondaryContainer = Color(0xFFBF360C),
    tertiary = Color(0xFFE1BEE7),
    tertiaryContainer = DarkLavender,
    surface = DarkSurface,
    surfaceContainerLow = DarkSurfaceContainer,
    surfaceContainerHigh = Color(0xFF4A354B),
    surfaceContainerHighest = Color(0xFF5D405E),
    onSurface = Color(0xFFFCE4EC),
    onSurfaceVariant = Color(0xFFD8BFD8),
    outline = Color(0xFF8D6E8E),
    outlineVariant = Color(0xFF7D5A7D),
    error = CoralAccent,
    errorContainer = Color(0xFFBF360C),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFFFFCCBC)
)

val CupCakeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        letterSpacing = -0.25.sp,
        lineHeight = 64.sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        letterSpacing = 0.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = 0.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = 0.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 0.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 0.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 0.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun CupCakeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CupCakeTypography,
        content = content
    )
}

@Composable
fun CuteCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        onClick = onClick ?: {},
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun CuteButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ),
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        enabled = enabled,
        colors = colors,
        shape = RoundedCornerShape(16.dp)
    ) {
        content()
    }
}

@Composable
fun CuteIconButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}

@Composable
fun CuteTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    singleLine: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
        placeholder = { Text(text = placeholder, color = colorScheme.onSurfaceVariant) },
        singleLine = singleLine,
        leadingIcon = icon?.let { { Icon(imageVector = it, contentDescription = null, tint = colorScheme.primary) } },
        shape = RoundedCornerShape(16.dp)
    )
}