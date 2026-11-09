package com.girocerto.app.theme

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ============================================================================
// Dark scheme: deep cockpit navy surfaces, Royal Blue structure, Neon Lime action.
// ============================================================================
private val DarkScheme = darkColorScheme(
    primary = BrandNeonLime,
    onPrimary = Color(0xFF05102E),
    primaryContainer = Color(0xFF132247),
    onPrimaryContainer = BrandNeonLime,
    secondary = BrandRoyalBlue,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF162345),
    onSecondaryContainer = Color(0xFFE8EDF9),
    tertiary = BrandNeonLimeDark,
    onTertiary = Color(0xFF05102E),
    tertiaryContainer = Color(0xFF132247),
    onTertiaryContainer = BrandNeonLime,
    background = CockpitDarkBackground,
    onBackground = Color(0xFFFFFFFF),
    surface = CockpitDarkSurface,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = CockpitDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF93A4C8),
    outline = Color(0xFF203362),
    outlineVariant = Color(0xFF172648),
    error = Color(0xFFFF4D6D),
    onError = Color(0xFF3A0710),
    errorContainer = Color(0xFF5A1520),
    onErrorContainer = Color(0xFFFFD9DE),
    surfaceContainer = CockpitDarkSurface,
    surfaceContainerHigh = Color(0xFF162345)
)

// ============================================================================
// Light scheme: clean bright canvas, Royal Blue primary, Neon Lime highlights.
// ============================================================================
private val LightScheme = lightColorScheme(
    primary = BrandRoyalBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8EDF9),
    onPrimaryContainer = BrandRoyalBlue,
    secondary = BrandRoyalBlue,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8EDF9),
    onSecondaryContainer = Color(0xFF05102E),
    tertiary = BrandNeonLimeDark,
    onTertiary = Color(0xFF05102E),
    tertiaryContainer = Color(0xFFF1FED1),
    onTertiaryContainer = Color(0xFF05102E),
    background = CockpitLightBackground,
    onBackground = Color(0xFF05102E),
    surface = CockpitLightSurface,
    onSurface = Color(0xFF05102E),
    surfaceVariant = CockpitLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF4B5878),
    outline = Color(0xFFD1DBEE),
    outlineVariant = Color(0xFFE2EAF8),
    error = Color(0xFFC70D2C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE7EA),
    onErrorContainer = Color(0xFF4A0A13),
    surfaceContainer = CockpitLightSurface,
    surfaceContainerHigh = Color(0xFFF0F4FC)
)

@Composable
fun GiroCertoTheme(content: @Composable () -> Unit) {
    val mode by AppearancePreferences.mode.collectAsStateWithLifecycle()
    val fontScale by AppearancePreferences.fontScale.collectAsStateWithLifecycle()
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
        typography = remember(fontScale) { scaledTypography(Typography, fontScale.factor) },
        shapes = Shapes(
            small = RoundedCornerShape(AppSpacing.CornerSmall),
            medium = RoundedCornerShape(AppSpacing.CornerCard),
            large = RoundedCornerShape(20.dp)
        ),
        content = content
    )
}
