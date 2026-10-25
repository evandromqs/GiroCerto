package com.girocerto.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.girocerto.ui.components.TactileCard as Card
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.girocerto.app.GiroCertoApp
import com.girocerto.app.theme.BackgroundDark
import com.girocerto.app.theme.TextPrimary
import com.girocerto.app.theme.TextSecondary
import com.girocerto.domain.calculator.EarningsGoalCalculator
import com.girocerto.ui.components.Formatters

@Composable
fun GoalsScreen(modifier: Modifier = Modifier) {
    val repository = GiroCertoApp.instance.settingsRepository
    val settings by repository.settings.collectAsState()
    var goal by rememberSaveable { mutableStateOf("1000") }
    var days by rememberSaveable { mutableStateOf("5") }
    var hours by rememberSaveable { mutableStateOf("8") }
    var kilometres by rememberSaveable { mutableStateOf("150") }
    var rides by rememberSaveable { mutableStateOf("15") }
    var message by rememberSaveable { mutableStateOf("") }
    fun decimal(text: String) = text.trim().replace(",", ".").toDoubleOrNull() ?: Double.NaN
    val calculation = runCatching {
        EarningsGoalCalculator.calculate(
            EarningsGoalCalculator.Plan(decimal(goal), days.toIntOrNull() ?: 0,
                decimal(hours), decimal(kilometres), rides.toIntOrNull() ?: 0), settings)
    }
    val estimate = calculation.getOrNull()
    Column(modifier.fillMaxSize().background(BackgroundDark).verticalScroll(rememberScrollState())
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Planejar ganhos", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Text("Defina o lucro que deseja buscar e estime o faturamento necessário para cobrir seus custos.", color = TextSecondary)
        GoalField("Lucro desejado por semana (R$)", goal) { goal = it; message = "" }
        GoalField("Dias de trabalho por semana", days, whole = true) { days = it; message = "" }
        GoalField("Horas de trabalho por dia", hours) { hours = it; message = "" }
        GoalField("Quilômetros totais por dia", kilometres) { kilometres = it; message = "" }
        GoalField("Corridas por dia", rides, whole = true) { rides = it; message = "" }
        Text("Inclua deslocamentos sem passageiro e tempo de espera. Custos de combustível, desgaste, custo fixo diário e por corrida vêm das suas configurações.", color = TextSecondary)
        if (estimate == null) {
            Text(calculation.exceptionOrNull()?.message ?: "Revise os valores.", color = MaterialTheme.colorScheme.error)
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sua projeção", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            GoalResult("Custos por semana", estimate.weeklyOperatingCost)
            GoalResult("Faturamento por semana", estimate.requiredWeeklyGross)
            GoalResult("Faturamento por dia", estimate.requiredDailyGross)
            GoalResult("Meta bruta por hora", estimate.suggestedPerHour)
            GoalResult("Meta bruta por km", estimate.suggestedPerKm)
            GoalResult("Lucro mensal médio desejado", estimate.averageMonthlyNetGoal)
            Text("Média mensal calculada com 52 semanas ÷ 12 meses. Valores por hora e km são arredondados para cima ao centavo.", color = TextSecondary)
            }
            }
            Button(onClick = {
                runCatching {
                    repository.updateSettings(repository.settings.value.copy(
                        minTargetPerKm = estimate.suggestedPerKm,
                        minTargetPerHour = estimate.suggestedPerHour))
                }.fold(onSuccess = { message = "Metas aplicadas ao semáforo." },
                    onFailure = { message = "Não foi possível aplicar as metas. Tente novamente." })
            }, modifier = Modifier.fillMaxWidth()) { Text("Aplicar metas ao semáforo") }
        }
        if (message.isNotEmpty()) Text(message, color = TextPrimary)
        Text("Esta é uma sugestão de planejamento, sem garantia de receita. Demanda, trânsito e custos reais podem variar. Revise suas metas conforme a rotina.", color = TextSecondary)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun GoalField(label: String, value: String, whole: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = if (whole) KeyboardType.Number else KeyboardType.Decimal))
}

@Composable
private fun GoalResult(label: String, amount: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(Formatters.formatCurrency(amount), color = TextPrimary)
    }
}


