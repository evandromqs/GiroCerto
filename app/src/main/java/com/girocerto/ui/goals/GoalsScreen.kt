package com.girocerto.ui.goals

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.girocerto.app.GiroCertoApp
import com.girocerto.app.theme.AppSpacing
import com.girocerto.domain.calculator.EarningsGoalCalculator
import com.girocerto.ui.components.AppText
import com.girocerto.ui.components.Formatters
import com.girocerto.ui.components.NeobrutalButton
import com.girocerto.ui.components.NeobrutalCard

@Composable
fun GoalsScreen(modifier: Modifier = Modifier) {
    val repository = GiroCertoApp.instance.settingsRepository
    val settings by repository.settings.collectAsStateWithLifecycle()
    var goal by rememberSaveable { mutableStateOf("1000") }
    var days by rememberSaveable { mutableStateOf("5") }
    var hours by rememberSaveable { mutableStateOf("8") }
    var kilometres by rememberSaveable { mutableStateOf("150") }
    var rides by rememberSaveable { mutableStateOf("15") }
    var message by rememberSaveable { mutableStateOf("") }
    fun decimal(text: String) = text.trim().replace(",", ".").toDoubleOrNull() ?: Double.NaN
    val calculation = runCatching {
        EarningsGoalCalculator.calculate(
            EarningsGoalCalculator.Plan(
                decimal(goal), days.toIntOrNull() ?: 0,
                decimal(hours), decimal(kilometres), rides.toIntOrNull() ?: 0
            ), settings
        )
    }
    val estimate = calculation.getOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(AppSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Gap)
    ) {
        AppText(
            text = "Planejar ganhos",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        AppText(
            text = "Defina o lucro que deseja buscar e estime o faturamento necessário para cobrir seus custos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GoalField("Lucro desejado por semana (R$)", goal) { goal = it; message = "" }
        GoalField("Dias de trabalho por semana", days, whole = true) { days = it; message = "" }
        GoalField("Horas de trabalho por dia", hours) { hours = it; message = "" }
        GoalField("Quilômetros totais por dia", kilometres) { kilometres = it; message = "" }
        GoalField("Corridas por dia", rides, whole = true) { rides = it; message = "" }

        AppText(
            text = "Inclua deslocamentos sem passageiro e tempo de espera. Custos de combustível, desgaste, custo fixo diário e por corrida vêm das suas configurações.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (estimate == null) {
            AppText(
                text = calculation.exceptionOrNull()?.message ?: "Revise os valores.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
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
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Gap),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    AppText(
                        text = "Sua projeção",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    GoalResult("Custos por semana", estimate.weeklyOperatingCost)
                    GoalResult("Faturamento por semana", estimate.requiredWeeklyGross)
                    GoalResult("Faturamento por dia", estimate.requiredDailyGross)
                    GoalResult("Meta bruta por hora", estimate.suggestedPerHour)
                    GoalResult("Meta bruta por km", estimate.suggestedPerKm)
                    GoalResult("Lucro mensal médio desejado", estimate.averageMonthlyNetGoal)
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    AppText(
                        text = "Média mensal calculada com 52 semanas ÷ 12 meses. Valores por hora e km são arredondados para cima ao centavo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            NeobrutalButton(
                onClick = {
                    runCatching {
                        repository.updateSettings(
                            repository.settings.value.copy(
                                minTargetPerKm = estimate.suggestedPerKm,
                                minTargetPerHour = estimate.suggestedPerHour
                            )
                        )
                    }.fold(
                        onSuccess = { message = "Metas aplicadas ao semáforo." },
                        onFailure = { message = "Não foi possível aplicar as metas. Tente novamente." }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(AppSpacing.CornerButton),
                text = "Aplicar metas ao semáforo"
            )
        }

        if (message.isNotEmpty()) {
            AppText(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        AppText(
            text = "Esta é uma sugestão de planejamento, sem garantia de receita. Demanda, trânsito e custos reais podem variar. Revise suas metas conforme a rotina.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun GoalField(label: String, value: String, whole: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { AppText(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppSpacing.CornerSmall),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        keyboardOptions = KeyboardOptions(keyboardType = if (whole) KeyboardType.Number else KeyboardType.Decimal)
    )
}

@Composable
private fun GoalResult(label: String, amount: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        AppText(
            Formatters.formatCurrency(amount),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
