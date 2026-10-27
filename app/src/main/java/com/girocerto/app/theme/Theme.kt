package com.girocerto.app.theme

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFDDE8FA), onPrimary = Navy,
    primaryContainer = Navy, onPrimaryContainer = Color.White,
    secondary = Color(0xFFB8C8DF), onSecondary = Color(0xFF080D15),
    secondaryContainer = Color(0xFF243A58), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFDDE8FA), onTertiary = Navy,
    background = Color(0xFF080D15), onBackground = Color(0xFFF7F9FC),
    surface = Color(0xFF152238), onSurface = Color(0xFFF7F9FC),
    surfaceVariant = Color(0xFF1D304B), onSurfaceVariant = Color(0xFFC4CFDF),
    outline = Color(0xFF8B9CB3), outlineVariant = Color(0xFF3C506B),
    error = Color(0xFFFDA4AF), onError = Color(0xFF3B0610),
    errorContainer = Color(0xFF5C1220), onErrorContainer = Color(0xFFFFE4E8),
    surfaceContainer = Color(0xFF152238), surfaceContainerHigh = Color(0xFF20314A)
)
private val LightScheme = lightColorScheme(
    primary = Navy, onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E8F3), onPrimaryContainer = Navy,
    secondary = Color(0xFF334C6C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EAF4), onSecondaryContainer = Navy,
    tertiary = Navy, onTertiary = Color.White,
    background = Color(0xFFF0F3F8), onBackground = Color(0xFF10151D),
    surface = Color.White, onSurface = Color(0xFF10151D),
    surfaceVariant = Color(0xFFE7EDF5), onSurfaceVariant = Color(0xFF42536B),
    outline = Color(0xFF65758A), outlineVariant = Color(0xFFC6D1DF),
    error = Color(0xFFB42336), onError = Color.White,
    errorContainer = Color(0xFFFDE8EB), onErrorContainer = Color(0xFF3B0610),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFE7EDF5)
)

@Composable
fun GiroCertoTheme(content: @Composable () -> Unit) {
    val mode by AppearancePreferences.mode.collectAsStateWithLifecycle()
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val activity = androidx.activity.compose.LocalActivity.current as? androidx.activity.ComponentActivity
    SideEffect {
        activity?.let {
            val style = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            it.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)), content = content)
}
