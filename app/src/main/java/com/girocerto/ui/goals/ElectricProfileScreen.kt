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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.girocerto.app.GiroCertoApp
import com.girocerto.app.theme.AppSpacing
import com.girocerto.domain.model.ElectricProfile
import com.girocerto.ui.components.AppText
import com.girocerto.ui.components.Formatters
import com.girocerto.ui.components.NeobrutalButton
import com.girocerto.ui.components.NeobrutalCard

internal fun inputNumber(value: String): Double? {
    val text = value.trim().replace("\u00A0", "")
    if (!Regex("""(?:[0-9]{1,3}(?:\.[0-9]{3})+,[0-9]+|[0-9]+(?:[.,][0-9]+)?)""").matches(text)) return null
    val normalized = if (text.contains(',')) text.replace(".", "").replace(',', '.') else text
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
}

@Composable
fun MoneyField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    maximum: Double = 1_000_000.0,
    wholeNumber: Boolean = false
) {
    val parsed = inputNumber(value)
    val invalid = value.isNotBlank() && (parsed == null || parsed !in 0.0..maximum || wholeNumber && value.toIntOrNull() == null)
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(18)) },
        label = { AppText(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Start),
        isError = invalid,
        supportingText = if (invalid) {
            { AppText(if (wholeNumber) "Informe um número inteiro até ${maximum.toInt()}." else "Informe um valor de 0 a ${maximum.toInt()}, usando vírgula nos centavos.") }
        } else null,
        shape = RoundedCornerShape(AppSpacing.CornerSmall),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        keyboardOptions = KeyboardOptions(keyboardType = if (wholeNumber) KeyboardType.Number else KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppText(label, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppText(value, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ElectricProfileScreen(onDone: () -> Unit) {
    val app = GiroCertoApp.instance
    val saved by app.workRepository.profile.collectAsStateWithLifecycle()
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
    val viewModel: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val busy = operation.busy
    var completed by rememberSaveable { mutableStateOf(0) }
    val revision by app.workRepository.restoreRevision.collectAsStateWithLifecycle()
    LaunchedEffect(revision) { if (revision > 0) { editing = false; step = 0 } }
    LaunchedEffect(operation.completed) {
        if (operation.completed > completed) { completed = operation.completed; editing = false; onDone() }
    }
    androidx.activity.compose.BackHandler((saved == null || editing) && step > 0) { if (!busy) step-- }
    val draft = ElectricProfile(
        name.trim(), inputNumber(rent) ?: -1.0, inputNumber(kmCost) ?: -1.0,
        inputNumber(extra) ?: -1.0, days.toIntOrNull() ?: 0, inputNumber(hours) ?: -1.0,
        inputNumber(km) ?: -1.0, inputNumber(goal) ?: -1.0
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(AppSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Section),
        horizontalAlignment = androidx.compose.ui.Alignment.Start
    ) {
        AppText(
            text = "Minha moto e metas",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (saved != null && !editing) {
            val profile = saved!!
            AppText(
                text = "${profile.name}, este é o seu plano.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            ProfileProjection(profile)
            NeobrutalButton(
                onClick = {
                    name = profile.name; rent = profile.weeklyRent.toString(); kmCost = profile.chargedPerKm.toString()
                    extra = profile.extraPerKm.toString(); days = profile.workDays.toString(); hours = profile.hoursPerDay.toString()
                    km = profile.kmPerDay.toString(); goal = profile.weeklyNetGoal.toString(); step = 0; editing = true
                },
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                borderColor = MaterialTheme.colorScheme.outline,
                borderWidth = AppSpacing.BorderAction,
                shape = RoundedCornerShape(AppSpacing.CornerButton),
                text = "Editar meu perfil"
            )
            NeobrutalButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(AppSpacing.CornerButton),
                text = "Registrar meus ganhos"
            )
        } else {
            AppText(
                text = "Passo ${step + 1} de 4 • Moto elétrica",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            LinearProgressIndicator(
                progress = { (step + 1) / 4f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(AppSpacing.CornerSmall))
            )
            when (step) {
                0 -> {
                    AppText("Como é o seu contrato?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(60) },
                        label = { AppText("Como podemos chamar você?") },
                        singleLine = true,
                        shape = RoundedCornerShape(AppSpacing.CornerSmall),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    MoneyField("Valor fixo por semana (R$)", rent, { rent = it })
                    MoneyField("Valor cobrado por km (R$)", kmCost, { kmCost = it })
                    AppText("Use os valores do seu contrato. Se não houver uma cobrança, informe zero.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                1 -> {
                    AppText("Existe algum custo extra?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    MoneyField("Recarga e manutenção extras por km (R$)", extra, { extra = it })
                    AppText("Se recarga e manutenção já estiverem incluídas no contrato, deixe zero para não contar duas vezes.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                2 -> {
                    AppText("Como será sua semana?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    MoneyField("Dias de trabalho por semana (1 a 7)", days, { days = it }, maximum = 7.0, wholeNumber = true)
                    MoneyField("Horas ativas por dia", hours, { hours = it }, maximum = 24.0)
                    MoneyField("Km totais previstos por dia", km, { km = it }, maximum = 2000.0)
                    AppText("Inclua espera e deslocamentos. Conte cada hora e km uma só vez, mesmo com vários apps abertos.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                3 -> {
                    AppText("Quanto você quer que sobre?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    MoneyField("Meta líquida por semana (R$)", goal, { goal = it })
                    if (draft.valid()) ProfileProjection(draft)
                    else AppText("Informe sua meta para ver o plano.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            val validStep = when (step) {
                0 -> name.isNotBlank() && listOf(rent, kmCost).all { (inputNumber(it) ?: -1.0) in 0.0..1_000_000.0 }
                1 -> (inputNumber(extra) ?: -1.0) in 0.0..1_000_000.0
                2 -> (days.toIntOrNull() ?: 0) in 1..7 && (inputNumber(hours) ?: 0.0) > 0 && (inputNumber(hours) ?: 25.0) <= 24 && (inputNumber(km) ?: 0.0) > 0 && (inputNumber(km) ?: 2001.0) <= 2000
                else -> draft.valid()
            }
            if (!validStep) {
                AppText("Preencha os campos desta etapa com valores válidos para continuar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            (error ?: operation.error)?.let { AppText(it, color = MaterialTheme.colorScheme.error) }

            NeobrutalButton(
                onClick = {
                    if (step < 3) { step++; error = null }
                    else viewModel.save(draft)
                },
                enabled = validStep && !busy,
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(AppSpacing.CornerButton),
                text = if (busy) "Salvando…" else if (step == 3) "Salvar perfil e metas" else "Continuar"
            )
            if (step > 0) {
                TextButton(
                    onClick = { step--; error = null },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = AppSpacing.MinTouchTarget)
                ) {
                    AppText("Voltar", color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (saved != null) {
                TextButton(
                    onClick = { editing = false },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = AppSpacing.MinTouchTarget)
                ) {
                    AppText("Cancelar edição", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ProfileProjection(p: ElectricProfile) {
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
            horizontalAlignment = androidx.compose.ui.Alignment.Start
        ) {
            AppText("Meta de faturamento por dia", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            AppText(
                Formatters.formatCurrency(p.dailyGrossGoal),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            SummaryLine("Para sobrar na semana", Formatters.formatCurrency(p.weeklyNetGoal))
            SummaryLine("Custos previstos na semana", Formatters.formatCurrency(p.weeklyCosts))
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            SummaryLine("Buscar por hora", Formatters.formatPerHour(p.targetPerHour))
            SummaryLine("Buscar por km", Formatters.formatPerKm(p.targetPerKm))
            SummaryLine("Contrato semanal", Formatters.formatCurrency(p.weeklyRent))
            SummaryLine("Custo variável por km", Formatters.formatPerKm(p.variablePerKm))
            AppText(
                "O fixo semanal é dividido entre ${p.workDays} dias de trabalho. A meta cobre o fixo, todos os km previstos e o valor que você quer guardar. É uma projeção, não garantia de ganho.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            AppText(
                "O copiloto usa esses custos por km e essas metas. O fixo semanal é descontado no fechamento do dia, não em cada oferta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
