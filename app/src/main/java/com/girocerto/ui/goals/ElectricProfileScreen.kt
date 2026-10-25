package com.girocerto.ui.goals

import com.girocerto.ui.components.TactileButton as Button

import com.girocerto.ui.components.TactileCard as Card

import com.girocerto.ui.components.AppText as Text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.girocerto.app.GiroCertoApp
import com.girocerto.domain.model.ElectricProfile
import com.girocerto.ui.components.Formatters
import kotlinx.coroutines.launch

internal fun inputNumber(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

@Composable
fun MoneyField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier.fillMaxWidth())
}

@Composable
fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun ElectricProfileScreen(onDone: () -> Unit) {
    val app = GiroCertoApp.instance
    val saved by app.workRepository.profile.collectAsState()
    var editing by rememberSaveable { mutableStateOf(false) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(saved?.name ?: "") }
    var rent by rememberSaveable { mutableStateOf(saved?.weeklyRent?.toString() ?: "") }
    var kmCost by rememberSaveable { mutableStateOf(saved?.chargedPerKm?.toString() ?: "") }
    var extra by rememberSaveable { mutableStateOf(saved?.extraPerKm?.toString() ?: "0") }
    var days by rememberSaveable { mutableStateOf(saved?.workDays?.toString() ?: "5") }
    var hours by rememberSaveable { mutableStateOf(saved?.hoursPerDay?.toString() ?: "8") }
    var km by rememberSaveable { mutableStateOf(saved?.kmPerDay?.toString() ?: "") }
    var goal by rememberSaveable { mutableStateOf(saved?.weeklyNetGoal?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val draft = ElectricProfile(name.trim(), inputNumber(rent) ?: -1.0, inputNumber(kmCost) ?: -1.0,
        inputNumber(extra) ?: -1.0, days.toIntOrNull() ?: 0, inputNumber(hours) ?: -1.0,
        inputNumber(km) ?: -1.0, inputNumber(goal) ?: -1.0)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text("Minha moto e metas", style = MaterialTheme.typography.headlineMedium)
        if (saved != null && !editing) {
            val profile = saved!!
            Text("${profile.name}, este é o seu plano.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            ProfileProjection(profile)
            OutlinedButton(onClick = {
                name = profile.name; rent = profile.weeklyRent.toString(); kmCost = profile.chargedPerKm.toString()
                extra = profile.extraPerKm.toString(); days = profile.workDays.toString(); hours = profile.hoursPerDay.toString()
                km = profile.kmPerDay.toString(); goal = profile.weeklyNetGoal.toString(); step = 0; editing = true
            }, modifier = Modifier.fillMaxWidth()) { Text("Editar meu perfil") }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Registrar meus ganhos") }
        } else {
            Text("Passo ${step + 1} de 4 • Moto elétrica", color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth())
            when (step) {
                0 -> {
                    Text("Como é o seu contrato?", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text("Como podemos chamar você?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    MoneyField("Valor fixo por semana (R$)", rent, { rent = it })
                    MoneyField("Valor cobrado por km (R$)", kmCost, { kmCost = it })
                    Text("Use os valores do seu contrato. Se não houver uma cobrança, informe zero.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                1 -> {
                    Text("Existe algum custo extra?", style = MaterialTheme.typography.titleLarge)
                    MoneyField("Recarga e manutenção extras por km (R$)", extra, { extra = it })
                    Text("Se recarga e manutenção já estiverem incluídas no contrato, deixe zero para não contar duas vezes.")
                }
                2 -> {
                    Text("Como será sua semana?", style = MaterialTheme.typography.titleLarge)
                    MoneyField("Dias de trabalho por semana (1 a 7)", days, { days = it })
                    MoneyField("Horas ativas por dia", hours, { hours = it })
                    MoneyField("Km totais previstos por dia", km, { km = it })
                    Text("Inclua espera e deslocamentos. Conte cada hora e km uma só vez, mesmo com vários apps abertos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                3 -> {
                    Text("Quanto você quer que sobre?", style = MaterialTheme.typography.titleLarge)
                    MoneyField("Meta líquida por semana (R$)", goal, { goal = it })
                    if (draft.valid()) ProfileProjection(draft)
                    else Text("Informe sua meta para ver o plano.")
                }
            }
            val validStep = when (step) {
                0 -> name.isNotBlank() && listOf(rent, kmCost).all { (inputNumber(it) ?: -1.0) in 0.0..1_000_000.0 }
                1 -> (inputNumber(extra) ?: -1.0) in 0.0..1_000_000.0
                2 -> (days.toIntOrNull() ?: 0) in 1..7 && (inputNumber(hours) ?: 0.0) > 0 && (inputNumber(hours) ?: 25.0) <= 24 && (inputNumber(km) ?: 0.0) > 0 && (inputNumber(km) ?: 2001.0) <= 2000
                else -> draft.valid()
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                if (step < 3) { step++; error = null }
                else scope.launch {
                    busy = true
                    try {
                        app.workRepository.saveProfile(draft)
                        app.settingsRepository.updateSettings(draft.vehicleSettings(app.settingsRepository.settings.value))
                        editing = false; onDone()
                    } catch (_: Exception) { error = "Não foi possível salvar. Tente novamente." }
                    finally { busy = false }
                }
            }, enabled = validStep && !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Salvando…" else if (step == 3) "Salvar perfil e metas" else "Continuar")
            }
            if (step > 0) TextButton(onClick = { step--; error = null }, enabled = !busy) { Text("Voltar") }
            if (saved != null) TextButton(onClick = { editing = false }, enabled = !busy) { Text("Cancelar edição") }
        }
    }
}

@Composable
private fun ProfileProjection(p: ElectricProfile) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text("Meta de faturamento por dia", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Formatters.formatCurrency(p.dailyGrossGoal), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            SummaryLine("Para sobrar na semana", Formatters.formatCurrency(p.weeklyNetGoal))
            SummaryLine("Custos previstos na semana", Formatters.formatCurrency(p.weeklyCosts))
            HorizontalDivider()
            SummaryLine("Buscar por hora", Formatters.formatPerHour(p.targetPerHour))
            SummaryLine("Buscar por km", Formatters.formatPerKm(p.targetPerKm))
            SummaryLine("Contrato semanal", Formatters.formatCurrency(p.weeklyRent))
            SummaryLine("Custo variável por km", Formatters.formatPerKm(p.variablePerKm))
            Text("O fixo semanal é dividido entre ${p.workDays} dias de trabalho. A meta cobre o fixo, todos os km previstos e o valor que você quer guardar. É uma projeção, não garantia de ganho.", style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Justify, modifier = Modifier.fillMaxWidth())
            Text("O copiloto usa esses custos por km e essas metas. O fixo semanal é descontado no fechamento do dia, não em cada oferta.", style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Justify, modifier = Modifier.fillMaxWidth())
        }
    }
}
