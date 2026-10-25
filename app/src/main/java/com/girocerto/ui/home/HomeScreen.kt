package com.girocerto.ui.home

import com.girocerto.ui.components.TactileButton as Button

import com.girocerto.ui.components.AppText as Text

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.ButtonDefaults
import com.girocerto.ui.components.TactileCard as Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.girocerto.app.GiroCertoApp
import com.girocerto.app.theme.BackgroundDark
import com.girocerto.app.theme.BorderDark
import com.girocerto.app.theme.GreenPositive
import com.girocerto.app.theme.PrimaryBlue
import com.girocerto.app.theme.RedAlert
import com.girocerto.app.theme.SurfaceDark
import com.girocerto.app.theme.SurfaceVariantDark
import com.girocerto.app.theme.TextPrimary
import com.girocerto.app.theme.TextSecondary
import com.girocerto.app.theme.YellowWarning
import com.girocerto.domain.model.DailySummary
import com.girocerto.domain.model.RideAnalysis
import com.girocerto.domain.model.TrafficLightStatus
import com.girocerto.monitoring.CopilotPreferences
import com.girocerto.monitoring.GiroCertoAccessibilityService
import com.girocerto.overlay.OverlayController
import com.girocerto.ui.components.Formatters
import com.girocerto.ui.components.MetricCard
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
    val rides by rideRepo.rides.collectAsState()
    val settings by GiroCertoApp.instance.settingsRepository.settings.collectAsState()
    val dailySummary = rideRepo.getTodaySummary(settings.dailyFixedCost)

    val isAccessibilityRunning by GiroCertoAccessibilityService.isServiceRunning.collectAsState()
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
    val liveOffer by GiroCertoAccessibilityService.lastAnalyzedRide.collectAsState()
    val isOverlayActive by OverlayController.isOverlayActive.collectAsState()
    val readerStatus by GiroCertoAccessibilityService.readerStatus.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
            Button(
                onClick = onNavigateToCopilot,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = BackgroundDark)
            ) {
                Text(
                    text = "CONFIGURAR COPILOTO",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = BackgroundDark
                )
            }
        }

        // Resumo do Desempenho do Dia
        item {
            Text(
                text = "Resumo do Dia",
                color = TextPrimary,
                fontSize = 16.sp,
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
                Text(
                    text = "Últimas Análises",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (rides.isNotEmpty()) {
                    Text(
                        text = "Ver Histórico",
                        color = PrimaryBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onNavigateToHistory() }
                    )
                }
            }
        }

        if (rides.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Nenhuma corrida analisada ainda.",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "As ofertas detectadas nos aplicativos aparecem aqui.",
                            color = TextSecondary,
                            fontSize = 12.sp
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
    val borderColor = when (analysis.trafficLight) {
        TrafficLightStatus.GREEN -> GreenPositive
        TrafficLightStatus.YELLOW -> YellowWarning
        TrafficLightStatus.RED -> RedAlert
        TrafficLightStatus.INVALID -> BorderDark
    }
    val statusLabel = when (analysis.trafficLight) {
        TrafficLightStatus.GREEN -> "Compensou"
        TrafficLightStatus.YELLOW -> "Limite"
        TrafficLightStatus.RED -> "Nao Compensa"
        TrafficLightStatus.INVALID -> "Incompleto"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(2.dp, borderColor.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppPlatformBadge(appName = analysis.offer.appName)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("OFERTA DETECTADA", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(text = statusLabel, color = borderColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(Formatters.formatCurrency(analysis.offer.fare), color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Column(horizontalAlignment = Alignment.End) {
                    Text(Formatters.formatPerKm(analysis.grossPerKm), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("por km", color = TextSecondary, fontSize = 10.sp)
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
                Text("Destino: ${analysis.offer.destinationAddress}", color = TextSecondary, fontSize = 11.sp, maxLines = 1)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onShowOverlay, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = BackgroundDark)) {
                Text("Abrir no Card Flutuante", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Badge colorida representando visualmente a plataforma de transporte/entrega.
 */
@Composable
private fun AppPlatformBadge(appName: String) {
    val (bgColor, textColor, label) = when {
        appName.contains("uber eats", ignoreCase = true) -> Triple(Color(0xFF102746L), Color.White, "EATS")
        appName.contains("uber moto", ignoreCase = true) -> Triple(Color(0xFF374151L), Color.White, "MOTO")
        appName.contains("uber", ignoreCase = true) -> Triple(Color(0xFF111827L), Color.White, "UBER")
        appName.contains("99food", ignoreCase = true) -> Triple(Color(0xFF102746L), Color.White, "99FOOD")
        appName.contains("99", ignoreCase = true) -> Triple(Color(0xFF102746L), Color.White, "99")
        appName.contains("ifood", ignoreCase = true) -> Triple(Color(0xFF102746L), Color.White, "iFOOD")
        appName.contains("indrive", ignoreCase = true) -> Triple(Color(0xFF102746L), Color.White, "inDRIVE")
        appName.contains("keeta", ignoreCase = true) -> Triple(Color(0xFF102746L), Color.Black, "KEETA")
        else -> Triple(PrimaryBlue, Color.White, appName.take(6).uppercase())
    }
    Box(
        modifier = Modifier.background(bgColor, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
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
            Text(
                text = "Giro Certo",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Copiloto Financeiro para Motoristas",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }

        OutlinedButton(
            onClick = onNavigateToSettings,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Text("Ajustes", fontSize = 12.sp)
        }
    }
}

@Composable
private fun DailySummaryCard(summary: DailySummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "LUCRO ESTIMADO HOJE",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = Formatters.formatCurrency(summary.totalEstimatedProfit),
                        color = if (summary.totalEstimatedProfit >= 0) GreenPositive else RedAlert,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "FATURAMENTO BRUTO",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = Formatters.formatCurrency(summary.totalGrossFare),
                        color = TextPrimary,
                        fontSize = 18.sp,
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
                SemaphorIndicator(color = GreenPositive, count = summary.greenCount, label = "Compensou")
                SemaphorIndicator(color = YellowWarning, count = summary.yellowCount, label = "Atenção")
                SemaphorIndicator(color = RedAlert, count = summary.redCount, label = "Desvantagem")
            }
        }
    }
}

@Composable
private fun SemaphorIndicator(color: Color, count: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count $label",
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun EmptyDailyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Nenhuma corrida analisada hoje",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Com o copiloto ativo, aguarde uma oferta no aplicativo de corridas.",
                color = TextSecondary,
                fontSize = 12.sp,
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
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
                    Text(
                        text = Formatters.formatCurrency(ride.offer.fare),
                        color = TextPrimary,
                        fontSize = 17.sp,
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
                Text(
                    text = "${Formatters.formatKm(ride.totalDistanceKm)} • ${Formatters.formatDuration(ride.offer.durationMinutes)}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = "Lucro: ${Formatters.formatCurrency(ride.estimatedProfit)}",
                    color = if (ride.estimatedProfit >= 0) GreenPositive else RedAlert,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${Formatters.formatPerKm(ride.grossPerKm)} • ${Formatters.formatOfferPerHour(ride.grossPerHour, ride.offer.durationMinutes)}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                Text(
                    text = "Abrir no Flutuante",
                    color = PrimaryBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onOpenOverlay() }
                )
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(
            1.dp,
            if (isFullyActive) GreenPositive.copy(alpha = 0.5f) else YellowWarning.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
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
                                color = if (isFullyActive) GreenPositive else YellowWarning,
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = if (!copilotEnabled) "Copiloto pausado" else if (isFullyActive) "Copiloto em Tempo Real Ativo" else "Copiloto Automático Pendente",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = if (!copilotEnabled) {
                    "O monitoramento está desativado. Ative o copiloto nos ajustes para voltar a acompanhar ofertas."
                } else if (isFullyActive) {
                    "Monitorando os aplicativos habilitados nos ajustes. O card flutuante com R$/km e semáforo abrirá automaticamente sobre o app de transporte."
                } else {
                    "Para o Giro Certo calcular o lucro automaticamente sobre a tela da Uber/99, autorize as permissões abaixo:"
                },
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            if (copilotEnabled && readerStatus.isNotBlank()) {
                Text(text = readerStatus, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            }

            if (!copilotEnabled) {
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Abrir ajustes do copiloto") }
            } else if (!isFullyActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!hasOverlayPermission) {
                        Button(
                            onClick = onRequestOverlay,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = BackgroundDark)
                        ) {
                            Text("Janela Flutuante", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (!isAccessibilityActive) {
                        Button(
                            onClick = onRequestAccessibility,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = YellowWarning)
                        ) {
                            Text("Acessibilidade", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            } else {
                // Controle do Botão Flutuante Permanente (estilo GigU)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Botão Flutuante na Tela",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isOverlayActive) "Bolha ativa sobre os outros apps" else "Ativar ícone flutuante persistente",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = { onToggleBubble(!isOverlayActive) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOverlayActive) RedAlert.copy(alpha = 0.8f) else PrimaryBlue
                        )
                    ) {
                        Text(
                            text = if (isOverlayActive) "Ocultar Bolha" else "Exibir Bolha",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BackgroundDark
                        )
                    }
                }
            }
        }
    }
}







