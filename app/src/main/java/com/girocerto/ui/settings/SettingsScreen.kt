package com.girocerto.ui.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.girocerto.ui.components.TactileButton as Button
import com.girocerto.ui.components.AppText as Text

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.girocerto.app.GiroCertoApp
import com.girocerto.app.theme.AppSpacing
import com.girocerto.domain.calculator.RideCalculator
import com.girocerto.domain.model.FuelType
import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.VehicleSettings
import com.girocerto.monitoring.VoiceAnnouncer
import com.girocerto.overlay.OverlayController
import com.girocerto.ui.components.NeobrutalButton
import com.girocerto.ui.components.NeobrutalCard
import com.girocerto.ui.components.TactileButton
import kotlinx.coroutines.launch

/**
 * Tela de configurações operacionais do Giro Certo.
 *
 * Permite ao motorista configurar:
 * - Tipo e preço de combustível
 * - Consumo médio do veículo
 * - Custos adicionais por km e custos fixos
 * - Metas de rendimento mínimo (semáforo)
 * - Preferências de voz TTS e duração do card flutuante
 */
@Composable
fun SettingsScreen(
    onNavigateToCopilot: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsRepo = GiroCertoApp.instance.settingsRepository
    val currentSettings by settingsRepo.settings.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- Estado dos campos de texto ---
    var fuelPrice by rememberSaveable(currentSettings) {
        mutableStateOf(currentSettings.fuelPricePerLiter.toString().replace(".", ","))
    }
    var consumption by rememberSaveable(currentSettings) {
        mutableStateOf(currentSettings.fuelConsumptionKmL.toString().replace(".", ","))
    }
    var additionalCost by rememberSaveable(currentSettings) {
        mutableStateOf(currentSettings.additionalCostPerKm.toString().replace(".", ","))
    }
    var minTargetKm by rememberSaveable(currentSettings) {
        mutableStateOf(currentSettings.minTargetPerKm.toString().replace(".", ","))
    }
    var minTargetHour by rememberSaveable(currentSettings) {
        mutableStateOf(currentSettings.minTargetPerHour.toString().replace(".", ","))
    }
    var fixedCost by rememberSaveable(currentSettings) {
        mutableStateOf(currentSettings.fixedCostPerRide.toString().replace(".", ","))
    }
    var dailyFixedCost by rememberSaveable(currentSettings) {
        mutableStateOf(currentSettings.dailyFixedCost.toString().replace(".", ","))
    }

    // --- Estado dos controles especiais ---
    var selectedFuelType by rememberSaveable(currentSettings) { mutableStateOf(currentSettings.fuelType) }
    var isVoiceEnabled by rememberSaveable(currentSettings) { mutableStateOf(currentSettings.isVoiceEnabled) }
    var speechRate by rememberSaveable(currentSettings) { mutableFloatStateOf(currentSettings.speechRate) }
    var cardDuration by rememberSaveable(currentSettings) { mutableIntStateOf(currentSettings.cardDurationSeconds) }
    var isFloatingBubbleEnabled by rememberSaveable(currentSettings) { mutableStateOf(currentSettings.isFloatingBubbleEnabled) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun save() {
        val fPrice = fuelPrice.replace(",", ".").toDoubleOrNull()
        val fCons = consumption.replace(",", ".").toDoubleOrNull()
        val addCost = additionalCost.replace(",", ".").toDoubleOrNull()
        val targetKm = minTargetKm.replace(",", ".").toDoubleOrNull()
        val targetHour = minTargetHour.replace(",", ".").toDoubleOrNull()
        val fixCost = fixedCost.replace(",", ".").toDoubleOrNull()
        val dailyFix = dailyFixedCost.replace(",", ".").toDoubleOrNull()

        if (fPrice == null || fPrice < 0) { errorMessage = "Preço do combustível inválido."; return }
        if (fCons == null || fCons <= 0) { errorMessage = "Consumo deve ser maior que zero (km/L)."; return }
        if (addCost == null || addCost < 0) { errorMessage = "Custo operacional por km inválido."; return }
        if (targetKm == null || targetKm < 0) { errorMessage = "Meta de R$/km inválida."; return }
        if (targetHour == null || targetHour < 0) { errorMessage = "Meta de R$/h inválida."; return }
        if (fixCost == null || fixCost < 0) { errorMessage = "Custo fixo por corrida inválido."; return }
        if (dailyFix == null || dailyFix < 0) { errorMessage = "Custo fixo diário inválido."; return }

        val updated = currentSettings.copy(
            fuelType = selectedFuelType,
            fuelPricePerLiter = fPrice,
            fuelConsumptionKmL = fCons,
            additionalCostPerKm = addCost,
            minTargetPerKm = targetKm,
            minTargetPerHour = targetHour,
            fixedCostPerRide = fixCost,
            dailyFixedCost = dailyFix,
            isVoiceEnabled = isVoiceEnabled,
            speechRate = speechRate,
            cardDurationSeconds = cardDuration,
            isFloatingBubbleEnabled = isFloatingBubbleEnabled
        )

        settingsRepo.updateSettings(updated)
        errorMessage = null
        scope.launch { snackbarHostState.showSnackbar("Configurações salvas com sucesso!") }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(AppSpacing.Screen)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SnackbarHost(hostState = snackbarHostState)
        TactileButton(
            onClick = onNavigateToCopilot,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppSpacing.MinTouchTarget),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(AppSpacing.CornerButton),
            border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
        ) {
            Text("Configurar copiloto", fontWeight = FontWeight.Bold)
        }

        Text(
            text = "Configurações Operacionais",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Ajuste os custos reais do seu veículo e as metas financeiras para calibrar o semáforo de decisão.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )

        // 
        // SEÇÃO 1 — Tipo de Combustível
        // 
        SectionCard(title = "Tipo de Combustível") {
            Text(
                text = "Selecione o combustível do seu veículo:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            val fuelOptions = listOf(
                FuelType.GASOLINA to "Gasolina",
                FuelType.ETANOL to "Etanol",
                FuelType.GNV to "GNV",
                FuelType.ELETRICO to "Elétrico"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                fuelOptions.forEach { (type, label) ->
                    FilterChip(
                        selected = selectedFuelType == type,
                        onClick = { selectedFuelType = type },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = AppSpacing.MinTouchTarget),
                        shape = RoundedCornerShape(AppSpacing.CornerSmall),
                        border = BorderStroke(
                            AppSpacing.BorderThin,
                            if (selectedFuelType == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            // Rótulo da unidade de acordo com o tipo
            val unitLabel = when (selectedFuelType) {
                FuelType.GNV -> "m³"
                FuelType.ELETRICO -> "kWh"
                else -> "Litro"
            }
            val consumptionLabel = when (selectedFuelType) {
                FuelType.GNV -> "Autonomia (km/m³)"
                FuelType.ELETRICO -> "Autonomia (km/kWh)"
                else -> "Consumo médio (km/Litro)"
            }

            Spacer(modifier = Modifier.height(4.dp))

            SettingsTextField(
                value = fuelPrice,
                onValueChange = { fuelPrice = it },
                label = "Preço por $unitLabel (R$/$unitLabel)",
                placeholder = when (selectedFuelType) {
                    FuelType.GNV -> "Ex: 4,50"
                    FuelType.ELETRICO -> "Ex: 0,90"
                    else -> "Ex: 5,80"
                }
            )

            SettingsTextField(
                value = consumption,
                onValueChange = { consumption = it },
                label = consumptionLabel,
                placeholder = when (selectedFuelType) {
                    FuelType.GNV -> "Ex: 12,0"
                    FuelType.ELETRICO -> "Ex: 6,5"
                    else -> "Ex: 10,0"
                }
            )
        }

        // 
        // SEÇÃO 2 — Outros Custos
        // 
        SectionCard(title = "Manutenção e Custos Adicionais") {
            SettingsTextField(
                value = additionalCost,
                onValueChange = { additionalCost = it },
                label = "Desgaste por km — pneus, óleo, depreciação (R$/km)",
                placeholder = "Ex: 0,30"
            )

            SettingsTextField(
                value = fixedCost,
                onValueChange = { fixedCost = it },
                label = "Custo fixo por corrida (R$, opcional)",
                placeholder = "Ex: 0,00 ou taxa fixa de plataforma"
            )

            SettingsTextField(
                value = dailyFixedCost,
                onValueChange = { dailyFixedCost = it },
                label = "Custo fixo diário — seguro, aluguel, MEI (R$/dia)",
                placeholder = "Ex: 0,00"
            )
        }

        // 
        // SEÇÃO 3 — Metas do Semáforo
        // 
        SectionCard(title = "Metas Mínimas de Decisão (Semáforo)") {
            Text(
                text = "Acima da meta = Verde    Até 15% abaixo = Amarelo    Abaixo = Vermelho",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))

            SettingsTextField(
                value = minTargetKm,
                onValueChange = { minTargetKm = it },
                label = "Meta mínima de ganho por km (R$/km)",
                placeholder = "Ex: 2,00"
            )

            SettingsTextField(
                value = minTargetHour,
                onValueChange = { minTargetHour = it },
                label = "Meta mínima de ganho por hora (R$/h)",
                placeholder = "Ex: 35,00"
            )
        }

        // 
        // SEÇÃO 4 — Voz e Card Flutuante
        // 
        SectionCard(title = "Voz e Card Flutuante") {
            // Toggle de voz
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Aviso por Voz (TTS)",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Fala R$/km e semáforo ao receber oferta",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = isVoiceEnabled,
                    onCheckedChange = { isVoiceEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            // Velocidade da fala
            if (isVoiceEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Velocidade da Voz", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    val rateLabel = when {
                        speechRate < 0.9f -> "Lento"
                        speechRate < 1.05f -> "Normal"
                        speechRate < 1.25f -> "Rápido"
                        else -> "Muito Rápido"
                    }
                    Text(
                        text = "$rateLabel (${String.format("%.1fx", speechRate)})",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Slider(
                    value = speechRate,
                    onValueChange = { speechRate = it },
                    valueRange = 0.7f..1.6f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }

            // Duração do card
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Duração do Card Flutuante", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                Text(
                    text = "${cardDuration}s",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = cardDuration.toFloat(),
                onValueChange = { cardDuration = it.toInt() },
                valueRange = 5f..30f,
                steps = 4,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
            Text(
                text = "O card fecha automaticamente após ${cardDuration}s sem interação.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )

            // Switch Botão Flutuante Permanente (estilo GigU)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Botão Flutuante Permanente",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Mantém a bolha do Giro Certo sempre na tela",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = isFloatingBubbleEnabled,
                    onCheckedChange = { isFloatingBubbleEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }

        // Mensagem de erro
        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // 
        // BOTÕES DE AÇÃO
        // 
        NeobrutalButton(
            text = "SALVAR CONFIGURAÇÕES",
            onClick = { save() },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppSpacing.MinTouchTarget),
            backgroundColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(AppSpacing.CornerButton)
        )

        TactileButton(
            onClick = {
                val defaults = VehicleSettings()
                fuelPrice = defaults.fuelPricePerLiter.toString().replace(".", ",")
                consumption = defaults.fuelConsumptionKmL.toString().replace(".", ",")
                additionalCost = defaults.additionalCostPerKm.toString().replace(".", ",")
                minTargetKm = defaults.minTargetPerKm.toString().replace(".", ",")
                minTargetHour = defaults.minTargetPerHour.toString().replace(".", ",")
                fixedCost = defaults.fixedCostPerRide.toString().replace(".", ",")
                dailyFixedCost = defaults.dailyFixedCost.toString().replace(".", ",")
                selectedFuelType = defaults.fuelType
                isVoiceEnabled = defaults.isVoiceEnabled
                speechRate = defaults.speechRate
                cardDuration = defaults.cardDurationSeconds
                isFloatingBubbleEnabled = defaults.isFloatingBubbleEnabled
                errorMessage = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppSpacing.MinTouchTarget),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(AppSpacing.CornerButton),
            border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
        ) {
            Text("Restaurar Valores Padrão", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 
        // SEÇÃO 5 — Copiloto & Teste Prático
        // 
        SectionCard(title = "Copiloto & Teste Prático") {
            Text(
                text = "Simule uma oferta real para verificar como o card flutuante e a voz funcionam com suas configurações atuais.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            // Botão Uber
            TactileButton(
                onClick = {
                    val testOffer = RideOffer(
                        fare = 29.50,
                        distanceKm = 9.2,
                        durationMinutes = 24,
                        pickupDistanceKm = 1.3,
                        appName = "Uber",
                        destinationAddress = "Av. Paulista, 1000 - Consolação"
                    )
                    val analysis = RideCalculator.calculate(testOffer, currentSettings)
                    OverlayController.showOverlay(context, analysis)
                    if (currentSettings.isVoiceEnabled) {
                        val announcer = VoiceAnnouncer(context)
                        scope.launch {
                            kotlinx.coroutines.delay(400)
                            announcer.speakAnalysis(analysis)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppSpacing.MinTouchTarget),
                shape = RoundedCornerShape(AppSpacing.CornerButton),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
            ) {
                Text("Simular Oferta Uber", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Botão iFood
            TactileButton(
                onClick = {
                    val testOffer = RideOffer(
                        fare = 14.80,
                        distanceKm = 4.5,
                        durationMinutes = 18,
                        pickupDistanceKm = 0.8,
                        appName = "iFood",
                        destinationAddress = "Rua Augusta, 200 - Consolação"
                    )
                    val analysis = RideCalculator.calculate(testOffer, currentSettings)
                    OverlayController.showOverlay(context, analysis)
                    if (currentSettings.isVoiceEnabled) {
                        val announcer = VoiceAnnouncer(context)
                        scope.launch {
                            kotlinx.coroutines.delay(400)
                            announcer.speakAnalysis(analysis)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppSpacing.MinTouchTarget),
                shape = RoundedCornerShape(AppSpacing.CornerButton),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
            ) {
                Text("Simular Entrega iFood", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Botão 99
            TactileButton(
                onClick = {
                    val testOffer = RideOffer(
                        fare = 22.00,
                        distanceKm = 7.8,
                        durationMinutes = 20,
                        pickupDistanceKm = 0.5,
                        appName = "99",
                        destinationAddress = "Shopping Ibirapuera - Moema"
                    )
                    val analysis = RideCalculator.calculate(testOffer, currentSettings)
                    OverlayController.showOverlay(context, analysis)
                    if (currentSettings.isVoiceEnabled) {
                        val announcer = VoiceAnnouncer(context)
                        scope.launch {
                            kotlinx.coroutines.delay(400)
                            announcer.speakAnalysis(analysis)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppSpacing.MinTouchTarget),
                shape = RoundedCornerShape(AppSpacing.CornerButton),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
            ) {
                Text("Simular Oferta 99", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Permissões
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TactileButton(
                    onClick = {
                        val intent = OverlayController.createOverlayPermissionIntent(context)
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = AppSpacing.MinTouchTarget),
                    shape = RoundedCornerShape(AppSpacing.CornerButton),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
                ) {
                    Text("Janela Flutuante", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                TactileButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = AppSpacing.MinTouchTarget),
                    shape = RoundedCornerShape(AppSpacing.CornerButton),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
                ) {
                    Text("Acessibilidade", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// 
// Composables reutilizáveis internos
// 

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
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
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text(placeholder, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
        singleLine = true
    )
}







