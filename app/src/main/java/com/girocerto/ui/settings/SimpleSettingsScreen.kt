package com.girocerto.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.girocerto.navigation.Screen
import com.girocerto.ui.components.NavigationGlyph

@Composable
fun SimpleSettingsScreen(onCopilot: () -> Unit, onProfile: () -> Unit, onAdvanced: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)
        Text("O essencial para trabalhar do seu jeito.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsLink(Screen.COPILOT, "Copiloto e permissões", "Leitura de ofertas, janela flutuante e alertas", onCopilot)
        SettingsLink(Screen.GOALS, "Minha moto e metas", "Contrato semanal, custo por km e planejamento", onProfile)
        SettingsLink(Screen.SETTINGS, "Cartão e voz", "Duração, velocidade da fala e bolha flutuante", onAdvanced)
        Text("Seu perfil e os fechamentos ficam neste aparelho. A leitura de ofertas não registra ganhos realizados automaticamente.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CardAndVoiceScreen() {
    val repository = com.girocerto.app.GiroCertoApp.instance.settingsRepository
    val settings by repository.settings.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Cartão e voz", style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Ouvir análise das ofertas", Modifier.weight(1f))
            Switch(settings.isVoiceEnabled, { repository.updateSettings(settings.copy(isVoiceEnabled = it)) })
        }
        Text("Velocidade da voz: %.1fx".format(settings.speechRate))
        Slider(settings.speechRate, { repository.updateSettings(settings.copy(speechRate = it)) }, valueRange = 0.5f..2f)
        Text("Mostrar cartão por ${settings.cardDurationSeconds} segundos")
        Slider(settings.cardDurationSeconds.toFloat(), { repository.updateSettings(settings.copy(cardDurationSeconds = it.toInt())) }, valueRange = 5f..60f, steps = 54)
        Text("O botão flutuante permanece visível enquanto o copiloto estiver ativo e a sobreposição estiver autorizada. Ao fechar o cartão de cálculo, ele volta ao botão.")
        Text("Alterações salvas automaticamente. Para mudar os custos da moto e os valores do semáforo, abra Metas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsLink(icon: Screen, title: String, subtitle: String, action: () -> Unit) {
    OutlinedCard(onClick = action, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NavigationGlyph(icon, true)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
