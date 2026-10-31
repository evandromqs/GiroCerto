package com.girocerto.app.theme

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ============================================================================
// Neobrutalist Dark Scheme: High-contrast pure white text, deep navy background
// ============================================================================
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8BB5FF),
    onPrimary = NeobrutalBlack,
    primaryContainer = NeobrutalDarkBlue,
    onPrimaryContainer = NeobrutalWhite,
    secondary = Color(0xFFB8C8DF),
    onSecondary = NeobrutalBlack,
    secondaryContainer = Color(0xFF1E3250),
    onSecondaryContainer = NeobrutalWhite,
    tertiary = Color(0xFF8BB5FF),
    onTertiary = NeobrutalDarkBlue,
    background = NeobrutalDarkBackground,       // #0A1128
    onBackground = NeobrutalWhite,             // Pure white text
    surface = NeobrutalDarkSurface,            // #152238
    onSurface = NeobrutalWhite,                // Pure white text
    surfaceVariant = Color(0xFF1D304B),
    onSurfaceVariant = NeobrutalWhite,         // High-contrast secondary text
    outline = NeobrutalWhite,                  // Crisp white border for dark cards
    outlineVariant = Color(0xFF3C506B),
    error = Color(0xFFFDA4AF),
    onError = NeobrutalBlack,
    errorContainer = Color(0xFF5C1220),
    onErrorContainer = Color(0xFFFFE4E8),
    surfaceContainer = NeobrutalDarkSurface,
    surfaceContainerHigh = Color(0xFF20314A)
)

// ============================================================================
// Neobrutalist Light Scheme: Dark Blue #102746, Light Gray #F0F3F8, Pure Black #000000 text
// ============================================================================
private val LightScheme = lightColorScheme(
    primary = NeobrutalDarkBlue,               // #102746
    onPrimary = NeobrutalWhite,                // Pure white
    primaryContainer = NeobrutalLightBlueTint, // #DDE8FA
    onPrimaryContainer = NeobrutalDarkBlue,
    secondary = NeobrutalDarkBlue,
    onSecondary = NeobrutalWhite,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = NeobrutalBlack,
    tertiary = NeobrutalDarkBlue,
    onTertiary = NeobrutalWhite,
    background = NeobrutalLightGray,           // #F0F3F8
    onBackground = NeobrutalBlack,             // Pure black text (#000000)
    surface = NeobrutalWhite,                  // #FFFFFF
    onSurface = NeobrutalBlack,                // Pure black text (#000000)
    surfaceVariant = NeobrutalLightGray,
    onSurfaceVariant = NeobrutalBlack,         // Pure black text (#000000)
    outline = NeobrutalBlack,                  // Solid pure black 2.5dp borders (#000000)
    outlineVariant = NeobrutalBlack,           // Card outline
    error = Color(0xFFB42336),
    onError = NeobrutalWhite,
    errorContainer = Color(0xFFFDE8EB),
    onErrorContainer = Color(0xFF3B0610),
    surfaceContainer = NeobrutalWhite,
    surfaceContainerHigh = NeobrutalLightGray
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
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = Typography,
        shapes = Shapes(
            small = RoundedCornerShape(AppSpacing.CornerSmall),
            medium = RoundedCornerShape(AppSpacing.CornerCard),
            large = RoundedCornerShape(16.dp)
        ),
        content = content
    )
}
