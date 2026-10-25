package com.girocerto.ui.settings

import com.girocerto.ui.components.AppText as Text

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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)
        Text("O essencial para trabalhar do seu jeito.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsLink(Screen.COPILOT, "Copiloto e permissões", "Leitura de ofertas, janela flutuante e alertas", onCopilot)
        SettingsLink(Screen.GOALS, "Minha moto e metas", "Contrato semanal, custo por km e planejamento", onProfile)
        SettingsLink(Screen.SETTINGS, "Cartão e voz", "Duração, velocidade da fala e bolha flutuante", onAdvanced)
        AppearancePicker()
        com.girocerto.update.AppUpdateSettings()
        VersionHistorySection()
        DataExportSection()
        Text("Seu perfil e os fechamentos ficam no aparelho e podem participar do backup do Android, conforme suas configurações. Ofertas e estados de permissões não entram nesse backup. A leitura de ofertas não registra ganhos realizados automaticamente.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CardAndVoiceScreen() {
    val repository = com.girocerto.app.GiroCertoApp.instance.settingsRepository
    val settings by repository.settings.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text("Cartão e voz", style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Ouvir análise das ofertas", Modifier.weight(1f))
            Switch(settings.isVoiceEnabled, { repository.updateSettings(settings.copy(isVoiceEnabled = it)) })
        }
        Text("Velocidade da voz: %.1fx".format(settings.speechRate))
        Slider(settings.speechRate, { repository.updateSettings(settings.copy(speechRate = it)) }, valueRange = 0.5f..2f)
        Text("Mostrar cartão por ${settings.cardDurationSeconds} segundos")
        Slider(settings.cardDurationSeconds.toFloat(), { repository.updateSettings(settings.copy(cardDurationSeconds = it.toInt())) }, valueRange = 5f..60f, steps = 54)
        Text("Com o copiloto ativo, o cartão volta à bolha ao fechar. Arraste a bolha até a lixeira para ocultar a janela; para voltar, use Mostrar botão flutuante no Copiloto.")
        Text("Alterações salvas automaticamente. Para mudar os custos da moto e os valores do semáforo, abra Metas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsLink(icon: Screen, title: String, subtitle: String, action: () -> Unit) {
    OutlinedCard(onClick = action, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NavigationGlyph(icon, true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AppearancePicker() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mode by com.girocerto.app.theme.AppearancePreferences.mode.collectAsState()
    com.girocerto.ui.components.TactileCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text("Aparência", style = MaterialTheme.typography.titleMedium)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.girocerto.app.theme.ThemeMode.entries.forEach { option ->
                    FilterChip(selected = mode == option, onClick = { com.girocerto.app.theme.AppearancePreferences.set(context, option) },
                        label = { Text(option.label, maxLines = 1) })
                }
            }
        }
    }
}
