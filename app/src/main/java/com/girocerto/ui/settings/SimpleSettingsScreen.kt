package com.girocerto.ui.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.girocerto.app.theme.AppSpacing
import com.girocerto.app.theme.ThemeMode
import com.girocerto.navigation.Screen
import com.girocerto.ui.components.AppText
import com.girocerto.ui.components.NavigationGlyph
import com.girocerto.ui.components.NeobrutalCard

@Composable
fun SimpleSettingsScreen(onCopilot: () -> Unit, onProfile: () -> Unit, onAdvanced: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(AppSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Section),
        horizontalAlignment = Alignment.Start
    ) {
        AppText(
            text = "Ajustes",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        AppText(
            text = "O essencial para trabalhar do seu jeito.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SettingsLink(Screen.COPILOT, "Copiloto e permissões", "Leitura de ofertas, janela flutuante e alertas", onCopilot)
        SettingsLink(Screen.GOALS, "Minha moto e metas", "Contrato semanal, custo por km e planejamento", onProfile)
        SettingsLink(Screen.SETTINGS, "Cartão e voz", "Duração, velocidade da fala e bolha flutuante", onAdvanced)
        AppearancePicker()
        com.girocerto.update.AppUpdateSettings()
        VersionHistorySection()
        DataExportSection()
        AppText(
            text = "Seu perfil e os fechamentos ficam no aparelho e podem participar do backup do Android, conforme suas configurações. Ofertas e estados de permissões não entram nesse backup. A leitura de ofertas não registra ganhos realizados automaticamente.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CardAndVoiceScreen() {
    val repository = com.girocerto.app.GiroCertoApp.instance.settingsRepository
    val settings by repository.settings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(AppSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Section),
        horizontalAlignment = Alignment.Start
    ) {
        AppText(
            text = "Cartão e voz",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        NeobrutalCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surface,
            borderColor = MaterialTheme.colorScheme.outline,
            borderWidth = AppSpacing.BorderThick,
            shadowOffset = AppSpacing.ShadowOffset,
            shape = RoundedCornerShape(AppSpacing.CornerCard)
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.Content),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppText("Aviso por Voz", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    AppText("Ouvir análise das ofertas", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = settings.isVoiceEnabled, onCheckedChange = { repository.updateSettings(settings.copy(isVoiceEnabled = it)) })
                }
                AppText("Velocidade da voz: %.1fx".format(settings.speechRate), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = settings.speechRate,
                    onValueChange = { repository.updateSettings(settings.copy(speechRate = it)) },
                    valueRange = 0.5f..2f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }

        NeobrutalCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surface,
            borderColor = MaterialTheme.colorScheme.outline,
            borderWidth = AppSpacing.BorderThick,
            shadowOffset = AppSpacing.ShadowOffset,
            shape = RoundedCornerShape(AppSpacing.CornerCard)
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.Content),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppText("Duração do Cartão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                AppText("Mostrar cartão por ${settings.cardDurationSeconds} segundos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 30, 45, settings.cardDurationSeconds).distinct().sorted().forEach { seconds ->
                        FilterChip(
                            selected = settings.cardDurationSeconds == seconds,
                            onClick = { repository.updateSettings(settings.copy(cardDurationSeconds = seconds)) },
                            label = { AppText("${seconds}s", fontWeight = if (settings.cardDurationSeconds == seconds) FontWeight.Bold else FontWeight.Normal) },
                            shape = RoundedCornerShape(AppSpacing.CornerSmall),
                            border = BorderStroke(AppSpacing.BorderThin, MaterialTheme.colorScheme.outline)
                        )
                    }
                }
            }
        }

        AppText(
            text = "Com o copiloto ativo, o cartão volta à bolha ao fechar. Arraste a bolha até a lixeira para ocultar a janela; para voltar, use Mostrar botão flutuante no Copiloto.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppText(
            text = "Alterações salvas automaticamente. Para mudar os custos da moto e os valores do semáforo, abra Metas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsLink(icon: Screen, title: String, subtitle: String, action: () -> Unit) {
    NeobrutalCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = action),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline,
        borderWidth = AppSpacing.BorderThick,
        shadowOffset = AppSpacing.ShadowOffset,
        shape = RoundedCornerShape(AppSpacing.CornerCard)
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.Content),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Content),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationGlyph(icon, true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.Start) {
                AppText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                AppText(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AppearancePicker() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mode by com.girocerto.app.theme.AppearancePreferences.mode.collectAsStateWithLifecycle()
    NeobrutalCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline,
        borderWidth = AppSpacing.BorderThick,
        shadowOffset = AppSpacing.ShadowOffset,
        shape = RoundedCornerShape(AppSpacing.CornerCard)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.Content),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            AppText("Aparência", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { option ->
                    FilterChip(
                        selected = mode == option,
                        onClick = { com.girocerto.app.theme.AppearancePreferences.set(context, option) },
                        label = { AppText(option.label, maxLines = 1, fontWeight = if (mode == option) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(AppSpacing.CornerSmall),
                        border = BorderStroke(AppSpacing.BorderThin, MaterialTheme.colorScheme.outline)
                    )
                }
            }
        }
    }
}
