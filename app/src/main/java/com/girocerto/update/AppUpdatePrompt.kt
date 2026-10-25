package com.girocerto.update

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.girocerto.ui.components.AppText as Text

import android.app.DownloadManager
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.girocerto.app.GiroCertoApp

@Composable
internal fun AppUpdatePrompt() {
    val manager = GiroCertoApp.instance.appUpdates
    val state by manager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val release = state.release ?: return
    if (!state.showPrompt) return
    AlertDialog(
        onDismissRequest = manager::dismiss,
        title = { Text("Giro Certo ${release.versionName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("Sua versão: ${manager.installedName}")
                Text(state.message)
                when (state.phase) {
                    DownloadPhase.DOWNLOADING -> {
                        val progress = state.progress
                        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else { LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth()); Text("$progress%") }
                    }
                    DownloadPhase.VERIFYING -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    DownloadPhase.AVAILABLE -> Text("O APK fica na pasta privada do Giro Certo. Após baixar, toque em Instalar atualização. Na primeira vez, o Android pode pedir autorização para instalar por este app.")
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (state.phase) {
                DownloadPhase.AVAILABLE, DownloadPhase.FAILED -> TextButton(onClick = manager::download) {
                    Text(if (state.phase == DownloadPhase.FAILED) "Tentar novamente" else "Baixar atualização")
                }
                DownloadPhase.READY -> TextButton(onClick = manager::install) { Text("Instalar atualização") }
                DownloadPhase.DOWNLOADING -> TextButton(onClick = manager::cancelDownload) { Text("Cancelar download") }
                else -> Unit
            }
        },
        dismissButton = { TextButton(onClick = manager::dismiss) {
            Text(if (state.phase == DownloadPhase.DOWNLOADING) "Continuar usando o app" else "Agora não")
        } }
    )
}

@Composable
internal fun AppUpdateSettings() {
    val manager = GiroCertoApp.instance.appUpdates
    val state by manager.state.collectAsStateWithLifecycle()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Atualizações", style = MaterialTheme.typography.titleMedium)
            Text("Versão instalada: ${manager.installedName}")
            Text(if (state.checking) "Consultando nova versão…" else state.message)
            TextButton(onClick = manager::open, enabled = !state.checking) {
                Text(if (state.release == null) "Verificar atualização" else "Ver atualização")
            }
            Text("A consulta usa o GitHub. Seus ganhos e os dados das ofertas continuam no aparelho.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
