package com.ytdroid.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF9A3F00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCA),
    onPrimaryContainer = Color(0xFF321000),
    secondary = Color(0xFF755847),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBC9),
    onSecondaryContainer = Color(0xFF2B170C),
    tertiary = Color(0xFF6A5D2F),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF3DED3),
    onSurfaceVariant = Color(0xFF53443B),
    outline = Color(0xFF85736A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB68C),
    onPrimary = Color(0xFF552000),
    primaryContainer = Color(0xFF753000),
    onPrimaryContainer = Color(0xFFFFDBCA),
    secondary = Color(0xFFE7BFAE),
    onSecondary = Color(0xFF442B1E),
    secondaryContainer = Color(0xFF5D4133),
    onSecondaryContainer = Color(0xFFFFDBC9),
    tertiary = Color(0xFFD0C191),
    background = Color(0xFF1E1512),
    onBackground = Color(0xFFF0DFD8),
    surface = Color(0xFF1E1512),
    onSurface = Color(0xFFF0DFD8),
    surfaceVariant = Color(0xFF53443B),
    onSurfaceVariant = Color(0xFFD8C2B8),
    outline = Color(0xFFA08C82),
)

@Composable
fun YtDroidTheme(
    themeMode: String,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
