package com.girocerto.ui.home

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.girocerto.app.GiroCertoApp
import com.girocerto.app.theme.AppSpacing
import com.girocerto.app.theme.NeobrutalBlack
import com.girocerto.app.theme.NeobrutalWhite
import com.girocerto.app.theme.StatusGreenSolid
import com.girocerto.app.theme.StatusRedSolid
import com.girocerto.app.theme.StatusYellowSolid
import com.girocerto.domain.model.DailySummary
import com.girocerto.domain.model.RideAnalysis
import com.girocerto.domain.model.TrafficLightStatus
import com.girocerto.monitoring.CopilotPreferences
import com.girocerto.monitoring.GiroCertoAccessibilityService
import com.girocerto.overlay.OverlayController
import com.girocerto.ui.components.AppText
import com.girocerto.ui.components.Formatters
import com.girocerto.ui.components.MetricCard
import com.girocerto.ui.components.NeobrutalButton
import com.girocerto.ui.components.NeobrutalCard
import com.girocerto.ui.components.TrafficLightBadge

@Composable
fun HomeScreen(
    onNavigateToCopilot: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowOverlay: (RideAnalysis) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rideRepo = GiroCertoApp.instance.rideRepository
    val rides by rideRepo.rides.collectAsStateWithLifecycle()
    val settings by GiroCertoApp.instance.settingsRepository.settings.collectAsStateWithLifecycle()
    val dailySummary = rideRepo.getTodaySummary(settings.dailyFixedCost)

    val isAccessibilityRunning by GiroCertoAccessibilityService.isServiceRunning.collectAsStateWithLifecycle()
    val isAccessibilityConfigured = isAccessibilityRunning || GiroCertoAccessibilityService.isAccessibilityEnabled(context)
    var permissionRevision by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRevision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val copilotEnabled = remember(permissionRevision) { CopilotPreferences(context).read().enabled }
    val hasOverlayPermission = remember(permissionRevision) { OverlayController.canDrawOverlays(context) }
    val liveOffer by GiroCertoAccessibilityService.lastAnalyzedRide.collectAsStateWithLifecycle()
    val isOverlayActive by OverlayController.isOverlayActive.collectAsStateWithLifecycle()
    val readerStatus by GiroCertoAccessibilityService.readerStatus.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(AppSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Section)
    ) {
        // Cabeçalho da Aplicação
        item {
            HeaderSection(onNavigateToSettings = onNavigateToSettings)
        }

        // Status do Copiloto em Tempo Real
        item {
            CopilotStatusCard(
                isAccessibilityActive = isAccessibilityConfigured,
                copilotEnabled = copilotEnabled,
                onOpenSettings = onNavigateToSettings,
                hasOverlayPermission = hasOverlayPermission,
                isOverlayActive = isOverlayActive,
                readerStatus = readerStatus,
                onToggleBubble = { enable ->
                    if (enable) {
                        OverlayController.startFloatingBubble(context)
                    } else {
                        OverlayController.stopFloatingBubble(context)
                    }
                },
                onRequestOverlay = {
                    val intent = OverlayController.createOverlayPermissionIntent(context)
                    context.startActivity(intent)
                },
                onRequestAccessibility = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            )
        }

        // Card de Última Oferta Detectada em Tempo Real (quando o Copiloto está ativo)
        liveOffer?.let { offer ->
            item {
                LiveOfferCard(analysis = offer, onShowOverlay = { onShowOverlay(offer) })
            }
        }

        // Botão Principal de Ação
        item {
            NeobrutalButton(
                onClick = onNavigateToCopilot,
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(AppSpacing.CornerButton),
                borderWidth = AppSpacing.BorderThick,
                shadowOffset = AppSpacing.ShadowOffset,
                text = "CONFIGURAR COPILOTO"
            )
        }

        // Resumo do Desempenho do Dia
        item {
            AppText(
                text = "Resumo do Dia",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (dailySummary.totalRides > 0) {
            item {
                DailySummaryCard(summary = dailySummary)
            }
        } else {
            item {
                EmptyDailyCard()
            }
        }

        // Últimas Análises
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(
                    text = "Últimas Análises",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (rides.isNotEmpty()) {
                    TextButton(onClick = onNavigateToHistory) {
                        AppText(
                            text = "Ver Histórico",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (rides.isEmpty()) {
            item {
                NeobrutalCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    borderColor = MaterialTheme.colorScheme.outline,
                    borderWidth = AppSpacing.BorderThick,
                    shadowOffset = AppSpacing.ShadowOffset,
                    shape = RoundedCornerShape(AppSpacing.CornerCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppText(
                            text = "Nenhuma corrida analisada ainda.",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AppText(
                            text = "As ofertas detectadas nos aplicativos aparecem aqui.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            val recentRides = rides.take(3)
            items(recentRides) { ride ->
                RecentRideItem(
                    ride = ride,
                    onOpenOverlay = { onShowOverlay(ride) }
                )
            }
        }
    }
}

/**
 * Card exibido na Home quando o Copiloto detecta uma oferta em tempo real nos apps de transporte.
 */
@Composable
private fun LiveOfferCard(
    analysis: RideAnalysis,
    onShowOverlay: () -> Unit
) {
    NeobrutalCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline,
        borderWidth = AppSpacing.BorderThick,
        shadowOffset = AppSpacing.ShadowOffset,
        shape = RoundedCornerShape(AppSpacing.CornerCard)
    ) {
        Column(modifier = Modifier.padding(AppSpacing.Content)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppPlatformBadge(appName = analysis.offer.appName)
                    Spacer(modifier = Modifier.width(8.dp))
                    AppText(
                        text = "OFERTA DETECTADA",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                TrafficLightBadge(status = analysis.trafficLight)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(
                    text = Formatters.formatCurrency(analysis.offer.fare),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Column(horizontalAlignment = Alignment.End) {
                    AppText(
                        text = Formatters.formatPerKm(analysis.grossPerKm),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    AppText(
                        text = "por km",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard(title = "por hora", value = Formatters.formatOfferPerHour(analysis.grossPerHour, analysis.offer.durationMinutes), modifier = Modifier.weight(1f))
                MetricCard(title = "lucro", value = Formatters.formatCurrency(analysis.estimatedProfit), modifier = Modifier.weight(1f))
                MetricCard(title = "distancia", value = Formatters.formatKm(analysis.totalDistanceKm), modifier = Modifier.weight(1f))
            }
            if (!analysis.offer.destinationAddress.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                AppText(
                    text = "Destino: ${analysis.offer.destinationAddress}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            NeobrutalButton(
                onClick = onShowOverlay,
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(AppSpacing.CornerButton),
                text = "Abrir no Card Flutuante"
            )
        }
    }
}

/**
 * Badge colorida representando visualmente a plataforma de transporte/entrega.
 */
@Composable
private fun AppPlatformBadge(appName: String) {
    val (bgColor, textColor, label) = when {
        appName.contains("uber eats", ignoreCase = true) -> Triple(Color(0xFF102746), NeobrutalWhite, "EATS")
        appName.contains("uber moto", ignoreCase = true) -> Triple(Color(0xFF374151), NeobrutalWhite, "MOTO")
        appName.contains("uber", ignoreCase = true) -> Triple(Color(0xFF111827), NeobrutalWhite, "UBER")
        appName.contains("99food", ignoreCase = true) -> Triple(Color(0xFFE11D48), NeobrutalWhite, "99FOOD")
        appName.contains("99", ignoreCase = true) -> Triple(Color(0xFFF97316), NeobrutalWhite, "99")
        appName.contains("ifood", ignoreCase = true) -> Triple(Color(0xFFEA1D2C), NeobrutalWhite, "iFOOD")
        appName.contains("indrive", ignoreCase = true) -> Triple(Color(0xFF22C55E), NeobrutalBlack, "inDRIVE")
        appName.contains("keeta", ignoreCase = true) -> Triple(Color(0xFFFBBF24), NeobrutalBlack, "KEETA")
        else -> Triple(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, appName.take(6).uppercase())
    }
    val shape = RoundedCornerShape(AppSpacing.CornerSmall)
    Box(
        modifier = Modifier
            .background(bgColor, shape)
            .border(BorderStroke(AppSpacing.BorderThin, NeobrutalBlack), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        AppText(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun HeaderSection(
    onNavigateToSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            AppText(
                text = "Giro Certo",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            AppText(
                text = "Copiloto Financeiro para Motoristas",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        NeobrutalButton(
            onClick = onNavigateToSettings,
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            borderColor = MaterialTheme.colorScheme.outline,
            borderWidth = AppSpacing.BorderAction,
            shape = RoundedCornerShape(AppSpacing.CornerButton),
            text = "Ajustes"
        )
    }
}

@Composable
private fun DailySummaryCard(summary: DailySummary) {
    NeobrutalCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline,
        borderWidth = AppSpacing.BorderThick,
        shadowOffset = AppSpacing.ShadowOffset,
        shape = RoundedCornerShape(AppSpacing.CornerCard)
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.Content)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    AppText(
                        text = "LUCRO ESTIMADO HOJE",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    AppText(
                        text = Formatters.formatCurrency(summary.totalEstimatedProfit),
                        color = if (summary.totalEstimatedProfit >= 0) StatusGreenSolid else StatusRedSolid,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    AppText(
                        text = "FATURAMENTO BRUTO",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    AppText(
                        text = Formatters.formatCurrency(summary.totalGrossFare),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Corridas",
                    value = summary.totalRides.toString(),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Distância Total",
                    value = Formatters.formatKm(summary.totalDistanceKm),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Média R$/km",
                    value = Formatters.formatPerKm(summary.averageGrossPerKm),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Breakdown do semáforo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SemaphorIndicator(color = StatusGreenSolid, count = summary.greenCount, label = "Compensou")
                SemaphorIndicator(color = StatusYellowSolid, count = summary.yellowCount, label = "Atenção")
                SemaphorIndicator(color = StatusRedSolid, count = summary.redCount, label = "Desvantagem")
            }
        }
    }
}

@Composable
private fun SemaphorIndicator(color: Color, count: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
                .border(BorderStroke(1.dp, NeobrutalBlack), CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        AppText(
            text = "$count $label",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EmptyDailyCard() {
    NeobrutalCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline,
        borderWidth = AppSpacing.BorderThick,
        shadowOffset = AppSpacing.ShadowOffset,
        shape = RoundedCornerShape(AppSpacing.CornerCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppText(
                text = "Nenhuma corrida analisada hoje",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            AppText(
                text = "Com o copiloto ativo, aguarde uma oferta no aplicativo de corridas.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun RecentRideItem(
    ride: RideAnalysis,
    onOpenOverlay: () -> Unit
) {
    NeobrutalCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline,
        borderWidth = AppSpacing.BorderThick,
        shadowOffset = AppSpacing.ShadowOffset,
        shape = RoundedCornerShape(AppSpacing.CornerCard)
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.Content)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ride.offer.appName.isNotBlank() && !ride.offer.appName.contains("Giro Certo", ignoreCase = true)) {
                        AppPlatformBadge(appName = ride.offer.appName)
                    }
                    AppText(
                        text = Formatters.formatCurrency(ride.offer.fare),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                TrafficLightBadge(status = ride.trafficLight)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AppText(
                    text = "${Formatters.formatKm(ride.totalDistanceKm)} • ${Formatters.formatDuration(ride.offer.durationMinutes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                AppText(
                    text = "Lucro: ${Formatters.formatCurrency(ride.estimatedProfit)}",
                    color = if (ride.estimatedProfit >= 0) StatusGreenSolid else StatusRedSolid,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(
                    text = "${Formatters.formatPerKm(ride.grossPerKm)} • ${Formatters.formatOfferPerHour(ride.grossPerHour, ride.offer.durationMinutes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )

                TextButton(onClick = onOpenOverlay) {
                    AppText(
                        text = "Abrir no Flutuante",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CopilotStatusCard(
    copilotEnabled: Boolean,
    onOpenSettings: () -> Unit,
    isAccessibilityActive: Boolean,
    hasOverlayPermission: Boolean,
    isOverlayActive: Boolean,
    readerStatus: String,
    onToggleBubble: (Boolean) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit
) {
    val isFullyActive = copilotEnabled && isAccessibilityActive && hasOverlayPermission

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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (isFullyActive) StatusGreenSolid else StatusYellowSolid,
                                shape = CircleShape
                            )
                            .border(BorderStroke(1.dp, NeobrutalBlack), CircleShape)
                    )
                    AppText(
                        text = if (!copilotEnabled) "Copiloto pausado" else if (isFullyActive) "Copiloto em Tempo Real Ativo" else "Copiloto Automático Pendente",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            AppText(
                text = if (!copilotEnabled) {
                    "O monitoramento está desativado. Ative o copiloto nos ajustes para voltar a acompanhar ofertas."
                } else if (isFullyActive) {
                    "Monitorando os aplicativos habilitados nos ajustes. O card flutuante com R$/km e semáforo abrirá automaticamente sobre o app de transporte."
                } else {
                    "Para o Giro Certo calcular o lucro automaticamente sobre a tela da Uber/99, autorize as permissões abaixo:"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp
            )

            if (copilotEnabled && readerStatus.isNotBlank()) {
                AppText(text = readerStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, lineHeight = 17.sp)
            }

            if (!copilotEnabled) {
                NeobrutalButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    borderColor = MaterialTheme.colorScheme.outline,
                    borderWidth = AppSpacing.BorderAction,
                    shape = RoundedCornerShape(AppSpacing.CornerButton),
                    text = "Abrir ajustes do copiloto"
                )
            } else if (!isFullyActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!hasOverlayPermission) {
                        NeobrutalButton(
                            onClick = onRequestOverlay,
                            modifier = Modifier.weight(1f),
                            backgroundColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(AppSpacing.CornerButton),
                            text = "Janela Flutuante"
                        )
                    }
                    if (!isAccessibilityActive) {
                        NeobrutalButton(
                            onClick = onRequestAccessibility,
                            modifier = Modifier.weight(1f),
                            backgroundColor = StatusYellowSolid,
                            contentColor = NeobrutalBlack,
                            shape = RoundedCornerShape(AppSpacing.CornerButton),
                            text = "Acessibilidade"
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AppText(
                            text = "Botão Flutuante na Tela",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        AppText(
                            text = if (isOverlayActive) "Bolha ativa sobre os outros apps" else "Ativar ícone flutuante persistente",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    NeobrutalButton(
                        onClick = { onToggleBubble(!isOverlayActive) },
                        backgroundColor = if (isOverlayActive) StatusRedSolid else MaterialTheme.colorScheme.primary,
                        contentColor = if (isOverlayActive) NeobrutalWhite else MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(AppSpacing.CornerButton),
                        text = if (isOverlayActive) "Ocultar Bolha" else "Exibir Bolha"
                    )
                }
            }
        }
    }
}
