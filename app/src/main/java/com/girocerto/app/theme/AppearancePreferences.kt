package com.girocerto.app.theme

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { LIGHT("Claro"), DARK("Escuro") }

object AppearancePreferences {
    private const val FILE = "appearance"
    private const val KEY = "theme"

    private val _mode = MutableStateFlow(ThemeMode.LIGHT)
    val mode = _mode.asStateFlow()

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
    }

    fun set(context: Context, value: ThemeMode) {
        context.getSharedPreferences(FILE, 0).edit().putString(KEY, value.name).apply()
        _mode.value = value
        com.girocerto.overlay.OverlayController.refreshAppearance()
    }

    fun isDark(context: Context): Boolean = _mode.value == ThemeMode.DARK

    private fun deviceMode(context: Context): ThemeMode =
        if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
            ThemeMode.DARK else ThemeMode.LIGHT
}
