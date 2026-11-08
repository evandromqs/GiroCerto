package com.girocerto.ui.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import com.girocerto.app.theme.FontScale
import com.girocerto.app.theme.FloatingIconSize
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
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        AppText(
            text = "O essencial para trabalhar do seu jeito.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SettingsLink(Screen.COPILOT, "Copiloto e permissões", "Leitura de ofertas, janela flutuante e alertas", onCopilot)
        SettingsLink(Screen.GOALS, "Meu veículo e metas", "Contrato, combustível, custos por km e planejamento", onProfile)
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
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                AppText("Aviso por Voz", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                AppText("Duração do Cartão", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                AppText("Tempo de cada oferta na tela", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30).forEach { seconds ->
                        FilterChip(selected = settings.cardDurationSeconds == seconds,
                            onClick = { repository.updateSettings(settings.copy(cardDurationSeconds = seconds)) },
                            label = { AppText("${seconds}s") })
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
            text = "Alterações salvas automaticamente. Para mudar os custos do veículo e os valores do semáforo, abra Metas.",
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
                AppText(
                    title,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                AppText(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AppearancePicker() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mode by com.girocerto.app.theme.AppearancePreferences.mode.collectAsStateWithLifecycle()
    val fontScale by com.girocerto.app.theme.AppearancePreferences.fontScale.collectAsStateWithLifecycle()
    val bubbleSize by com.girocerto.app.theme.AppearancePreferences.bubbleSize.collectAsStateWithLifecycle()
    val bubbleOpacity by com.girocerto.app.theme.AppearancePreferences.bubbleOpacity.collectAsStateWithLifecycle()
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppText(
                "Aparência",
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThemeChoice(
                    label = "Claro",
                    icon = com.girocerto.app.R.drawable.ic_sun,
                    selected = mode == ThemeMode.LIGHT,
                    modifier = Modifier.weight(1f),
                    onClick = { com.girocerto.app.theme.AppearancePreferences.set(context, ThemeMode.LIGHT) }
                )
                ThemeChoice(
                    label = "Escuro",
                    icon = com.girocerto.app.R.drawable.ic_moon,
                    selected = mode == ThemeMode.DARK,
                    modifier = Modifier.weight(1f),
                    onClick = { com.girocerto.app.theme.AppearancePreferences.set(context, ThemeMode.DARK) }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AppText("Tamanho da fonte", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FontScale.entries.forEach { option ->
                    FilterChip(
                        selected = fontScale == option,
                        onClick = { com.girocerto.app.theme.AppearancePreferences.setFontScale(context, option) },
                        label = { AppText(option.label, maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            AppText("Ícone flutuante", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FloatingIconSize.entries.forEach { option ->
                    FilterChip(
                        selected = bubbleSize == option,
                        onClick = { com.girocerto.app.theme.AppearancePreferences.setBubbleSize(context, option) },
                        label = { AppText(option.label, maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            AppText("Opacidade da bolha: ${(bubbleOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = bubbleOpacity,
                onValueChange = { com.girocerto.app.theme.AppearancePreferences.setBubbleOpacity(context, it) },
                valueRange = .45f..1f,
                steps = 10,
            )
        }
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    icon: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(180),
        label = "theme_choice_container"
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "theme_choice_content"
    )
    val border by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(180),
        label = "theme_choice_border"
    )
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(AppSpacing.CornerSmall),
        color = container,
        contentColor = content,
        border = BorderStroke(AppSpacing.BorderAction, border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(6.dp))
            AppText(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
