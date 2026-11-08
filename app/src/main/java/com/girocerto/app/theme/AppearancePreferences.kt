package com.girocerto.app.theme

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { LIGHT("Claro"), DARK("Escuro") }
enum class FontScale(val label: String, val factor: Float) {
    SMALL("Pequena", .90f), MEDIUM("Média", 1f), LARGE("Grande", 1.15f)
}
enum class FloatingIconSize(val label: String, val dp: Int) {
    SMALL("Pequeno", 48), MEDIUM("Médio", 56), LARGE("Grande", 68)
}

object AppearancePreferences {
    private const val FILE = "appearance"
    private const val KEY = "theme"
    private const val KEY_FONT = "font_scale"
    private const val KEY_BUBBLE_SIZE = "bubble_size"
    private const val KEY_BUBBLE_OPACITY = "bubble_opacity"

    private val _mode = MutableStateFlow(ThemeMode.LIGHT)
    val mode = _mode.asStateFlow()
    private val _fontScale = MutableStateFlow(FontScale.MEDIUM)
    val fontScale = _fontScale.asStateFlow()
    private val _bubbleSize = MutableStateFlow(FloatingIconSize.MEDIUM)
    val bubbleSize = _bubbleSize.asStateFlow()
    private val _bubbleOpacity = MutableStateFlow(.95f)
    val bubbleOpacity = _bubbleOpacity.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(FILE, 0)
        val stored = runCatching { prefs.getString(KEY, null) }.getOrNull()
        val mode = when (stored) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            // "SYSTEM" (removed) and first run adopt the device setting once.
            else -> deviceMode(context).also { prefs.edit().putString(KEY, it.name).apply() }
        }
        _mode.value = mode
        _fontScale.value = runCatching { FontScale.valueOf(prefs.getString(KEY_FONT, FontScale.MEDIUM.name)!!) }.getOrDefault(FontScale.MEDIUM)
        _bubbleSize.value = runCatching { FloatingIconSize.valueOf(prefs.getString(KEY_BUBBLE_SIZE, FloatingIconSize.MEDIUM.name)!!) }.getOrDefault(FloatingIconSize.MEDIUM)
        _bubbleOpacity.value = prefs.getFloat(KEY_BUBBLE_OPACITY, .95f).coerceIn(.45f, 1f)
    }

    fun set(context: Context, value: ThemeMode) {
        context.getSharedPreferences(FILE, 0).edit().putString(KEY, value.name).apply()
        _mode.value = value
        com.girocerto.overlay.OverlayController.refreshAppearance()
    }

    fun isDark(context: Context): Boolean = _mode.value == ThemeMode.DARK

    fun setFontScale(context: Context, value: FontScale) {
        context.getSharedPreferences(FILE, 0).edit().putString(KEY_FONT, value.name).apply()
        _fontScale.value = value
    }

    fun setBubbleSize(context: Context, value: FloatingIconSize) {
        context.getSharedPreferences(FILE, 0).edit().putString(KEY_BUBBLE_SIZE, value.name).apply()
        _bubbleSize.value = value
        com.girocerto.overlay.OverlayController.refreshAppearance()
    }

    fun setBubbleOpacity(context: Context, value: Float) {
        val safe = value.coerceIn(.45f, 1f)
        context.getSharedPreferences(FILE, 0).edit().putFloat(KEY_BUBBLE_OPACITY, safe).apply()
        _bubbleOpacity.value = safe
        com.girocerto.overlay.OverlayController.refreshAppearance()
    }

    private fun deviceMode(context: Context): ThemeMode =
        if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
            ThemeMode.DARK else ThemeMode.LIGHT
}
