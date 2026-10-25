package com.girocerto.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.girocerto.ui.components.AppText as Text

data class VersionChanges(val version: String, val changes: String)

@Composable
fun VersionHistorySection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val versionHistory = remember {
        val json = org.json.JSONArray(context.assets.open("changelog.json").bufferedReader().use { it.readText() })
        (0 until json.length()).map { index -> val item = json.getJSONObject(index); val lines = item.getJSONArray("changes")
            VersionChanges(item.getString("version"), (0 until lines.length()).joinToString("\n\n") { lines.getString(it) }) }
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(if (expanded) "Ocultar histórico de versões" else "Histórico de versões")
            }
            if (expanded) versionHistory.forEach { release ->
                Text("Versão ${release.version}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text(release.changes, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Justify)
                HorizontalDivider()
            }
        }
    }
}
