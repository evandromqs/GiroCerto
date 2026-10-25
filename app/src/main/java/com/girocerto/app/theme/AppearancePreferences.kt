package com.girocerto.app.theme

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("Sistema"), LIGHT("Claro"), DARK("Escuro") }
object AppearancePreferences {
    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode = _mode.asStateFlow()
    fun initialize(context: Context) {
        _mode.value = runCatching { ThemeMode.valueOf(context.getSharedPreferences("appearance", 0).getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
    }
    fun set(context: Context, value: ThemeMode) {
        context.getSharedPreferences("appearance", 0).edit().putString("theme", value.name).apply()
        _mode.value = value
        com.girocerto.overlay.OverlayController.refreshAppearance()
    }
    fun isDark(context: Context): Boolean = when (_mode.value) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
}
