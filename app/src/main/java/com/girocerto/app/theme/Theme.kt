package com.girocerto.app.theme

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ============================================================================
// Single-accent dark scheme: neutral navy surfaces, one brand hue.
// secondary and tertiary mirror the primary family so no second accent appears.
// ============================================================================
private val DarkScheme = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = Color(0xFF08142B),
    primaryContainer = Color(0xFF1A263A),
    onPrimaryContainer = Color(0xFFF4F7FD),
    secondary = BrandBlueDark,
    onSecondary = Color(0xFF08142B),
    secondaryContainer = Color(0xFF1A263A),
    onSecondaryContainer = Color(0xFFF4F7FD),
    tertiary = BrandBlueDark,
    onTertiary = Color(0xFF08142B),
    tertiaryContainer = Color(0xFF1A263A),
    onTertiaryContainer = Color(0xFFF4F7FD),
    background = NeobrutalDarkBackground,
    onBackground = Color(0xFFF4F7FD),
    surface = NeobrutalDarkSurface,
    onSurface = Color(0xFFF4F7FD),
    surfaceVariant = Color(0xFF1A263A),
    onSurfaceVariant = Color(0xFF9BA9BE),
    outline = Color(0xFF2B3A52),
    outlineVariant = Color(0xFF223047),
    error = Color(0xFFFFB3BE),
    onError = Color(0xFF3A0710),
    errorContainer = Color(0xFF5A1520),
    onErrorContainer = Color(0xFFFFD9DE),
    surfaceContainer = NeobrutalDarkSurface,
    surfaceContainerHigh = Color(0xFF1A263A)
)

// ============================================================================
// Single-accent light scheme: soft neutral canvas, one brand hue.
// ============================================================================
private val LightScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = NeobrutalWhite,
    primaryContainer = NeobrutalLightBlueTint,
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = BrandBlue,
    onSecondary = NeobrutalWhite,
    secondaryContainer = NeobrutalLightBlueTint,
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = BrandBlue,
    onTertiary = NeobrutalWhite,
    tertiaryContainer = NeobrutalLightBlueTint,
    onTertiaryContainer = Color(0xFF0F172A),
    background = NeobrutalLightGray,
    onBackground = Color(0xFF0F172A),
    surface = NeobrutalWhite,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = NeobrutalLightBlueTint,
    onSurfaceVariant = Color(0xFF5B6678),
    outline = Color(0xFFD6DEE9),
    outlineVariant = Color(0xFFE6EBF2),
    error = Color(0xFFB42336),
    onError = NeobrutalWhite,
    errorContainer = Color(0xFFFBE7EA),
    onErrorContainer = Color(0xFF4A0A13),
    surfaceContainer = NeobrutalWhite,
    surfaceContainerHigh = Color(0xFFF1F4F9)
)

@Composable
fun GiroCertoTheme(content: @Composable () -> Unit) {
    val mode by AppearancePreferences.mode.collectAsStateWithLifecycle()
    val dark = mode == ThemeMode.DARK
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
            large = RoundedCornerShape(20.dp)
        ),
        content = content
    )
}
