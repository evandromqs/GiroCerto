package com.girocerto.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.girocerto.app.theme.AppSpacing
import com.girocerto.ui.components.AppText as Text
import com.girocerto.ui.components.NeobrutalCard
import com.girocerto.ui.components.TactileButton

data class VersionChanges(val version: String, val changes: String)

@Composable
fun VersionHistorySection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val versionHistory = remember {
        val json = org.json.JSONArray(context.assets.open("changelog.json").bufferedReader().use { it.readText() })
        (0 until json.length()).map { index ->
            val item = json.getJSONObject(index)
            val lines = item.getJSONArray("changes")
            VersionChanges(
                item.getString("version"),
                (0 until lines.length()).joinToString("\n\n") { lines.getString(it) }
            )
        }
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    NeobrutalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppSpacing.Content),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TactileButton(
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppSpacing.MinTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(AppSpacing.CornerButton),
                border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = if (expanded) "Ocultar histórico de versões" else "Histórico de versões",
                    fontWeight = FontWeight.Bold
                )
            }
            if (expanded) {
                versionHistory.forEach { release ->
                    Text(
                        text = "Versão ${release.version}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = release.changes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                }
            }
        }
    }
}
