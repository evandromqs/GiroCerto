package com.girocerto.ui.goals

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.girocerto.app.GiroCertoApp
import com.girocerto.app.R
import com.girocerto.app.theme.AppSpacing
import com.girocerto.domain.model.ElectricProfile
import com.girocerto.domain.model.VehicleType
import com.girocerto.ui.components.AppText
import com.girocerto.ui.components.Formatters
import com.girocerto.ui.components.NeobrutalButton
import com.girocerto.ui.components.NeobrutalCard
import java.util.Locale

private const val TOTAL_STEPS = 5
private val FieldWidth = 400.dp

internal fun inputNumber(value: String): Double? {
    val text = value.trim().replace("\u00A0", "")
    if (!Regex("""(?:[0-9]{1,3}(?:\.[0-9]{3})+,[0-9]+|[0-9]+(?:[.,][0-9]+)?)""").matches(text)) return null
    val normalized = if (text.contains(',')) text.replace(".", "").replace(',', '.') else text
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
}

internal fun inputValue(value: Double): String =
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.').replace('.', ',')

@Composable
fun MoneyField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    maximum: Double = 1_000_000.0,
    wholeNumber: Boolean = false,
    centerText: Boolean = false
) {
    val parsed = inputNumber(value)
    val invalid = value.isNotBlank() && (parsed == null || parsed !in 0.0..maximum || wholeNumber && value.toIntOrNull() == null)
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(18)) },
        label = {
            AppText(
                label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = if (centerText) TextAlign.Center else TextAlign.Start
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            textAlign = if (centerText) TextAlign.Center else TextAlign.Start
        ),
        isError = invalid,
        supportingText = if (invalid) {
            {
                AppText(
                    if (wholeNumber) "Informe um número inteiro até ${maximum.toInt()}." else "Informe um valor de 0 a ${maximum.toInt()}, usando vírgula nos centavos.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (centerText) TextAlign.Center else TextAlign.Start
                )
            }
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AppText(label, Modifier.weight(1f), textAlign = TextAlign.Start, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppText(value, textAlign = TextAlign.End, maxLines = 1, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ElectricProfileScreen(onDone: () -> Unit) {
    val app = GiroCertoApp.instance
    val saved by app.workRepository.profile.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(saved?.name ?: "") }
    var vehicleTypeName by rememberSaveable { mutableStateOf((saved?.vehicleType ?: VehicleType.ELECTRIC_MOTO).name) }
    var vehicleLabel by rememberSaveable { mutableStateOf(saved?.vehicleName ?: "") }
    val vehicle = VehicleType.entries.firstOrNull { it.name == vehicleTypeName } ?: VehicleType.ELECTRIC_MOTO
    var rent by rememberSaveable { mutableStateOf(saved?.weeklyRent?.let(::inputValue) ?: "") }
    var kmCost by rememberSaveable { mutableStateOf(saved?.chargedPerKm?.let(::inputValue) ?: "") }
    var extra by rememberSaveable { mutableStateOf(saved?.extraPerKm?.let(::inputValue) ?: "0") }
    var fuelPrice by rememberSaveable { mutableStateOf(saved?.fuelPricePerLiter?.takeIf { it > 0 }?.let(::inputValue) ?: "") }
    var fuelKm by rememberSaveable { mutableStateOf(saved?.fuelConsumptionKmL?.takeIf { it > 0 }?.let(::inputValue) ?: "") }
    var weeklyFixed by rememberSaveable { mutableStateOf(saved?.weeklyFixedExpenses?.let(::inputValue) ?: "0") }
    var days by rememberSaveable { mutableStateOf(saved?.workDays?.toString() ?: "5") }
    var hours by rememberSaveable { mutableStateOf(saved?.hoursPerDay?.let(::inputValue) ?: "8") }
    var km by rememberSaveable { mutableStateOf(saved?.kmPerDay?.let(::inputValue) ?: "") }
    var goal by rememberSaveable { mutableStateOf(saved?.weeklyNetGoal?.let(::inputValue) ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val viewModel: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val busy = operation.busy
    var completed by rememberSaveable { mutableStateOf(0) }
    val revision by app.workRepository.restoreRevision.collectAsStateWithLifecycle()
    LaunchedEffect(revision) { if (revision > 0) { editing = false; step = 0 } }
    LaunchedEffect(operation.completed) {
        if (operation.completed > completed) { completed = operation.completed; editing = false; onDone() }
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    androidx.activity.compose.BackHandler((saved == null || editing) && step > 0) {
        if (!busy) {
            when {
                imeVisible -> keyboardController?.hide()
                else -> {
                    focusManager.clearFocus()
                    step--
                }
            }
        }
    }
    val draft = ElectricProfile(
        name.trim(), inputNumber(rent) ?: -1.0, inputNumber(kmCost) ?: -1.0,
        inputNumber(extra) ?: -1.0, days.toIntOrNull() ?: 0, inputNumber(hours) ?: -1.0,
        inputNumber(km) ?: -1.0, inputNumber(goal) ?: -1.0,
        vehicle,
        if (vehicle.isElectric) 0.0 else inputNumber(fuelPrice) ?: -1.0,
        if (vehicle.isElectric) 0.0 else inputNumber(fuelKm) ?: -1.0,
        inputNumber(weeklyFixed) ?: -1.0,
        vehicleLabel.trim()
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(AppSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Section),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppText(
            text = "Meu veículo e metas",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (saved != null && !editing) {
            val profile = saved!!
            AppText(
                text = "${profile.name}, este é o seu plano.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            ProfileProjection(profile)
            NeobrutalButton(
                onClick = {
                    name = profile.name; vehicleTypeName = profile.vehicleType.name
                    vehicleLabel = profile.vehicleName
                    rent = inputValue(profile.weeklyRent); kmCost = inputValue(profile.chargedPerKm)
                    extra = inputValue(profile.extraPerKm); days = profile.workDays.toString(); hours = inputValue(profile.hoursPerDay)
                    fuelPrice = profile.fuelPricePerLiter.takeIf { it > 0 }?.let(::inputValue) ?: ""
                    fuelKm = profile.fuelConsumptionKmL.takeIf { it > 0 }?.let(::inputValue) ?: ""
                    weeklyFixed = inputValue(profile.weeklyFixedExpenses)
                    km = inputValue(profile.kmPerDay); goal = inputValue(profile.weeklyNetGoal); step = 0; editing = true; attempted = false
                },
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                borderColor = MaterialTheme.colorScheme.outline,
                borderWidth = AppSpacing.BorderAction,
                shape = RoundedCornerShape(AppSpacing.CornerButton),
                text = "Editar meu perfil",
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            )
            NeobrutalButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(AppSpacing.CornerButton),
                text = "Registrar meus ganhos",
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_diary),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            )
        } else {
            AppText(
                text = "Passo ${step + 1} de $TOTAL_STEPS • ${vehicle.label}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            val stepProgress = (step + 1) / TOTAL_STEPS.toFloat()
            val animatedProgress by animateFloatAsState(
                targetValue = stepProgress,
                animationSpec = tween(300),
                label = "wizard_progress"
            )
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(AppSpacing.CornerSmall))
            )
            Column(
                modifier = Modifier
                    .widthIn(max = FieldWidth)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Gap),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (step) {
                    0 -> {
                        StepTitle("Sobre você")
                        CenteredTextField(
                            value = name,
                            onChange = { name = it.take(60) },
                            label = "Como podemos chamar você?"
                        )
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        ) {
                            VehicleType.entries.forEach { type ->
                                FilterChip(
                                    selected = vehicle == type,
                                    onClick = { vehicleTypeName = type.name; error = null },
                                    label = {
                                        AppText(
                                            type.label,
                                            maxLines = 1,
                                            fontWeight = if (vehicle == type) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    shape = RoundedCornerShape(AppSpacing.CornerSmall),
                                    border = BorderStroke(AppSpacing.BorderThin, MaterialTheme.colorScheme.outline)
                                )
                            }
                        }
                        CenteredTextField(
                            value = vehicleLabel,
                            onChange = { vehicleLabel = it.take(40) },
                            label = "Nome do veículo (opcional)"
                        )
                        Hint("O nome do veículo é opcional e aparece no seu plano e no histórico de fechamentos.")
                    }
                    1 -> {
                        StepTitle("Contrato e custo por km")
                        MoneyField("Contrato semanal (R$)", rent, { rent = it }, centerText = true)
                        MoneyField("Valor cobrado por km (R$)", kmCost, { kmCost = it }, centerText = true)
                        Hint("Use o valor do contrato, aluguel ou parcela. Moto própria ou carro quitado podem ficar com zero no fixo semanal.")
                    }
                    2 -> {
                        if (vehicle.isElectric) {
                            StepTitle("Gastos com energia")
                            MoneyField("Energia e manutenção por km (R$)", extra, { extra = it }, centerText = true)
                            MoneyField("Outros gastos fixos por semana (R$)", weeklyFixed, { weeklyFixed = it }, centerText = true)
                            Hint("Some recarga, bateria e desgaste por km. Se a energia já estiver incluída no contrato, deixe zero para não contar duas vezes.")
                        } else {
                            StepTitle("Gastos com combustível")
                            MoneyField("Preço do combustível (R$/litro)", fuelPrice, { fuelPrice = it }, centerText = true)
                            MoneyField("Consumo (km por litro)", fuelKm, { fuelKm = it }, maximum = 100.0, centerText = true)
                            MoneyField("Desgaste e pedágio por km (R$)", extra, { extra = it }, centerText = true)
                            MoneyField(
                                if (vehicle == VehicleType.CAR) "Seguro e IPVA semanal (R$)"
                                else "Outros gastos fixos por semana (R$)",
                                weeklyFixed, { weeklyFixed = it }, centerText = true
                            )
                            Hint("O combustível é calculado pelo preço e pelo consumo. Manutenção, desgaste e pedágio entram no custo por km; seguro e IPVA, no fixo semanal.")
                        }
                    }
                    3 -> {
                        StepTitle("Como será sua semana?")
                        MoneyField("Dias de trabalho na semana", days, { days = it }, maximum = 7.0, wholeNumber = true, centerText = true)
                        MoneyField("Horas ativas por dia", hours, { hours = it }, maximum = 24.0, centerText = true)
                        MoneyField("Km totais previstos por dia", km, { km = it }, maximum = 2000.0, centerText = true)
                        Hint("Inclua espera e deslocamentos. Conte cada hora e km uma só vez, mesmo com vários apps abertos.")
                    }
                    4 -> {
                        StepTitle("Quanto você quer que sobre?")
                        MoneyField("Meta líquida por semana (R$)", goal, { goal = it }, centerText = true)
                        if (draft.valid()) ProfileProjection(draft)
                        else Hint("Informe sua meta para ver o plano.")
                    }
                }
            }
            val validStep = when (step) {
                0 -> name.isNotBlank()
                1 -> listOf(rent, kmCost).all { (inputNumber(it) ?: -1.0) in 0.0..1_000_000.0 }
                2 -> if (vehicle.isElectric) {
                    (inputNumber(extra) ?: -1.0) in 0.0..1_000_000.0 &&
                        (inputNumber(weeklyFixed) ?: -1.0) in 0.0..1_000_000.0
                } else {
                    (inputNumber(fuelPrice) ?: -1.0) in 0.0..1_000_000.0 &&
                        (inputNumber(fuelKm) ?: 0.0) > 0 && (inputNumber(fuelKm) ?: 101.0) <= 100 &&
                        (inputNumber(extra) ?: -1.0) in 0.0..1_000_000.0 &&
                        (inputNumber(weeklyFixed) ?: -1.0) in 0.0..1_000_000.0
                }
                3 -> (days.toIntOrNull() ?: 0) in 1..7 && (inputNumber(hours) ?: 0.0) > 0 && (inputNumber(hours) ?: 25.0) <= 24 && (inputNumber(km) ?: 0.0) > 0 && (inputNumber(km) ?: 2001.0) <= 2000
                else -> draft.valid()
            }
            if (attempted && !validStep) {
                AppText(
                    "Preencha os campos desta etapa com valores válidos para continuar.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            (error ?: operation.error)?.let {
                AppText(it, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
            }

            NeobrutalButton(
                onClick = {
                    if (validStep) {
                        if (step < TOTAL_STEPS - 1) { step++; error = null; attempted = false }
                        else viewModel.save(draft)
                    } else attempted = true
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(AppSpacing.CornerButton),
                text = if (busy) "Salvando…" else if (step == TOTAL_STEPS - 1) "Salvar perfil e metas" else "Continuar",
                leadingIcon = {
                    Icon(
                        painter = painterResource(if (step == TOTAL_STEPS - 1) R.drawable.ic_check else R.drawable.ic_chevron_right),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            )
            if (step > 0) {
                TextButton(
                    onClick = { step--; error = null; attempted = false },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = AppSpacing.MinTouchTarget)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    AppText("Voltar", color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (saved != null) {
                TextButton(
                    onClick = { editing = false },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = AppSpacing.MinTouchTarget)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    AppText("Cancelar edição", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CenteredTextField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = {
            AppText(
                label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
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
}

@Composable
private fun StepTitle(text: String) {
    AppText(
        text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun Hint(text: String) {
    AppText(
        text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
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
            horizontalAlignment = Alignment.Start
        ) {
            AppText(
                "Meta de faturamento por dia",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
            AppText(
                if (p.vehicleName.isNotBlank()) "${p.vehicleName} • ${p.vehicleType.label}" else p.vehicleType.label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
            AppText(
                Formatters.formatCurrency(p.dailyGrossGoal),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            SummaryLine("Para sobrar na semana", Formatters.formatCurrency(p.weeklyNetGoal))
            SummaryLine("Custos totais da semana", Formatters.formatCurrency(p.weeklyCosts))
            if (!p.vehicleType.isElectric) {
                SummaryLine("Combustível por km", Formatters.formatPerKm(p.fuelPerKm))
                SummaryLine("Consumo informado", "%.1f km/L".format(p.fuelConsumptionKmL))
            }
            if (p.weeklyFixedExpenses > 0) {
                SummaryLine("Gastos fixos por semana", Formatters.formatCurrency(p.weeklyFixedExpenses))
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            SummaryLine("Buscar por hora", Formatters.formatPerHour(p.targetPerHour))
            SummaryLine("Buscar por km", Formatters.formatPerKm(p.targetPerKm))
            SummaryLine("Contrato semanal", Formatters.formatCurrency(p.weeklyRent))
            SummaryLine("Custo variável por km", Formatters.formatPerKm(p.variablePerKm))
            AppText(
                "O fixo semanal é dividido entre ${p.workDays} dias de trabalho. A meta cobre o contrato, os gastos fixos, todos os km previstos e o valor que você quer guardar. É uma projeção, não garantia de ganho.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            AppText(
                "O copiloto usa esses custos por km e essas metas. Os custos fixos são descontados no fechamento do dia, não em cada oferta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
