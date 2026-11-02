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
// Dark violet scheme with lighter accent text for contrast
// ============================================================================
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF1591EA),
    onPrimary = NeobrutalWhite,
    primaryContainer = Color(0xFF0000FF),
    onPrimaryContainer = NeobrutalWhite,
    secondary = Color(0xFF1591EA),
    onSecondary = NeobrutalWhite,
    secondaryContainer = Color(0xFF193754),
    onSecondaryContainer = NeobrutalWhite,
    tertiary = Color(0xFF8BCBFF),
    onTertiary = Color(0xFF0000FF),
    background = NeobrutalDarkBackground,       // Deep navy background
    onBackground = NeobrutalWhite,             // Pure white text
    surface = NeobrutalDarkSurface,            // Raised dark surface
    onSurface = NeobrutalWhite,                // Pure white text
    surfaceVariant = Color(0xFF19334F),
    onSurfaceVariant = Color(0xFF94A3B8),       // Slate-400 clear contrast for secondary labels
    outline = Color(0xFF4B7298),                  // Soft outline for dark cards
    outlineVariant = Color(0xFF594D73),
    error = Color(0xFFFDA4AF),
    onError = NeobrutalBlack,
    errorContainer = Color(0xFF5C1220),
    onErrorContainer = Color(0xFFFFE4E8),
    surfaceContainer = NeobrutalDarkSurface,
    surfaceContainerHigh = Color(0xFF193754)
)

// ============================================================================
// Light scheme with #0000FF brand accents
// ============================================================================
private val LightScheme = lightColorScheme(
    primary = NeobrutalDarkBlue,               // Brand violet
    onPrimary = NeobrutalWhite,                // Pure white
    primaryContainer = NeobrutalLightBlueTint, // Pale violet
    onPrimaryContainer = NeobrutalDarkBlue,
    secondary = Color(0xFF1591EA),
    onSecondary = NeobrutalWhite,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = NeobrutalBlack,
    tertiary = NeobrutalDarkBlue,
    onTertiary = NeobrutalWhite,
    background = NeobrutalLightGray,           // Soft light background
    onBackground = NeobrutalBlack,             // Pure black text (#000000)
    surface = NeobrutalWhite,                  // #FFFFFF
    onSurface = NeobrutalBlack,                // Pure black text (#000000)
    surfaceVariant = NeobrutalLightGray,
    onSurfaceVariant = Color(0xFF475569),      // Refined slate-600 secondary text
    outline = Color(0xFFBCD2E8),                  // Subtle surface outlines
    outlineVariant = Color(0xFFD8E8F6),           // Card outline
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
