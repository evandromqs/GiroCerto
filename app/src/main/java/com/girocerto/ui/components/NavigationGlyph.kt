package com.girocerto.ui.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.girocerto.app.R
import com.girocerto.app.theme.PrimaryBlue
import com.girocerto.app.theme.TextSecondary
import com.girocerto.navigation.Screen

/** Native vectors generated from the editable SVG family in design/icons. */
@Composable
fun NavigationGlyph(screen: Screen, selected: Boolean) {
    val icon = when (screen) {
        Screen.HOME -> R.drawable.ic_home
        Screen.DIARY -> R.drawable.ic_diary
        Screen.GOALS -> R.drawable.ic_goals
        Screen.HISTORY -> R.drawable.ic_history
        Screen.PROFILE -> R.drawable.ic_profile
        Screen.SETTINGS -> R.drawable.ic_settings
        Screen.COPILOT -> R.drawable.ic_copilot
        Screen.ADVANCED -> R.drawable.ic_advanced
    }
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp),
        tint = if (selected) PrimaryBlue else TextSecondary)
}
