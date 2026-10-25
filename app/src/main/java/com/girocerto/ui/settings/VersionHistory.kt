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
val versionHistory = listOf(
    VersionChanges("1.0.8", "Ícones oficiais dos apps e suporte ao pacote atual do Keeta. Leitura mais conservadora de valores e medidas repetidas. Proteção do histórico, rascunhos por data, saldo real na bolha, leitura com limites, alinhamento explícito, lembrete de atualização adiado por 24 horas, regras de backup e testes Android antes da publicação."),
    VersionChanges("1.0.7", "Temas claro e escuro, visual azul-marinho com relevo, nova bolha e lixeira para ocultar a janela."),
    VersionChanges("1.0.6", "Leitura de ofertas parcialmente encobertas, diagnóstico de OCR e download de atualizações pelo app."),
    VersionChanges("1.0.5", "Janela flutuante vinculada ao leitor e seleção dos apps usados no diário de ganhos."),
    VersionChanges("1.0.4", "Ícone próprio do aplicativo e redução das permissões solicitadas."),
    VersionChanges("1.0.3", "Primeira release publicada: copiloto, perfil da moto elétrica, metas e registro diário de ganhos.")
)

@Composable
fun VersionHistorySection() {
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
