package com.girocerto.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.girocerto.app.GiroCertoApp
import com.girocerto.data.repository.WorkExport
import com.girocerto.ui.components.AppText as Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DataExportSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            message = try {
                val snapshot = GiroCertoApp.instance.workRepository.days.value
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri) ?: error("Arquivo indisponível")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write("\uFEFF" + WorkExport.csv(snapshot)) }
                }
                "Ganhos exportados. Cada linha corresponde a um dia."
            } catch (_: Exception) { "Não foi possível exportar. Tente outro local." }
        }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            TextButton(onClick = { export.launch("GiroCerto-ganhos.csv") }) { Text("Exportar meus ganhos") }
            message?.let { Text(it) }
        }
    }
}
