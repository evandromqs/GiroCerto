package com.girocerto.ui.profile

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.girocerto.ui.components.TactileButton as Button

import com.girocerto.ui.components.AppText as Text

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.girocerto.ui.components.TactileCard as Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.girocerto.domain.model.RideAnalysis
import com.girocerto.domain.model.TrafficLightStatus
import com.girocerto.ui.components.Formatters

/**
 * Tela de Perfil do Motorista — exibe estatísticas acumuladas, breakdown por plataforma,
 * e informações do veículo configurado no Giro Certo.
 */
@Composable
fun ProfileScreen(
    onNavigateToGoals: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val messagePreferences = remember(context) { PassengerMessagePreferences(context) }
    var passengerMessage by rememberSaveable { mutableStateOf(messagePreferences.read()) }
    var messageSaved by rememberSaveable { mutableStateOf(false) }
    val rideRepo = GiroCertoApp.instance.rideRepository
    val settingsRepo = GiroCertoApp.instance.settingsRepository
    val rides by rideRepo.rides.collectAsStateWithLifecycle()
    val settings by settingsRepo.settings.collectAsStateWithLifecycle()

    // Estatísticas acumuladas
    val totalRides = rides.size
    val totalProfit = rides.sumOf { it.estimatedProfit }
    val totalGross = rides.sumOf { it.offer.fare }
    val totalDistance = rides.sumOf { it.totalDistanceKm }
    val avgKmRate = if (totalDistance > 0) totalGross / totalDistance else 0.0
    val greenCount = rides.count { it.trafficLight == TrafficLightStatus.GREEN }
    val yellowCount = rides.count { it.trafficLight == TrafficLightStatus.YELLOW }
    val redCount = rides.count { it.trafficLight == TrafficLightStatus.RED }

    // Breakdown por plataforma
    val platformBreakdown = rides
        .groupBy { resolveDisplayAppName(it.offer.appName) }
        .mapValues { it.value.size }
        .entries
        .sortedByDescending { it.value }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        androidx.compose.material3.OutlinedButton(onClick = onNavigateToGoals, modifier = Modifier.fillMaxWidth()) {
            Text("Metas e planejamento")
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("Mensagem ao passageiro", color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("Guarde um modelo neste aparelho. No histórico, você pode revisar a mensagem, escolher o aplicativo e confirmar o envio. Nada é enviado automaticamente.", color = TextSecondary, fontSize = 12.sp)
                OutlinedTextField(
                    value = passengerMessage,
                    onValueChange = { passengerMessage = it; messageSaved = false },
                    modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
                    label = { Text("Seu modelo de mensagem") }
                )
                Button(onClick = {
                    messagePreferences.save(passengerMessage)
                    messageSaved = true
                }, enabled = passengerMessage.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("Salvar modelo")
                }
                if (messageSaved) Text("Modelo salvo neste aparelho.", color = GreenPositive, fontSize = 12.sp)
            }
        }
        // Avatar e nome do motorista
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Círculo de iniciais
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "GC",
                        color = BackgroundDark,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Text(
                    text = "Motorista Giro Certo",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Copiloto Financeiro Ativo",
                    color = PrimaryBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                // Info do veículo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val fuelLabel = settings.fuelType.name
                        .lowercase()
                        .replaceFirstChar { it.uppercase() }
                        .replace("Gnv", "GNV")
                        .replace("Eletrico", "Elétrico")
                    Text(
                        text = "$fuelLabel · R\$ ${
                            String.format("%.2f", settings.fuelPricePerLiter).replace(".", ",")
                        }/L · ${
                            settings.fuelConsumptionKmL.toInt()
                        } km/L",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Estatísticas Gerais
        Text(
            text = "Desempenho Acumulado",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        if (totalRides == 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Text(
                    text = "Nenhuma corrida registrada ainda.\nAnalise suas primeiras corridas para ver o desempenho aqui.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
            }
        } else {
            // Lucro e faturamento
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("LUCRO LÍQUIDO TOTAL", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = Formatters.formatCurrency(totalProfit),
                                color = if (totalProfit >= 0) GreenPositive else RedAlert,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("FATURAMENTO BRUTO", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = Formatters.formatCurrency(totalGross),
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatChip(label = "Corridas", value = "$totalRides", modifier = Modifier.weight(1f))
                        StatChip(label = "Distância", value = Formatters.formatKm(totalDistance), modifier = Modifier.weight(1f))
                        StatChip(label = "Média R$/km", value = Formatters.formatPerKm(avgKmRate), modifier = Modifier.weight(1f))
                    }
                }
            }

            // Semáforo acumulado
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Distribuição do Semáforo",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SemaphoreBar(
                        greenCount = greenCount,
                        yellowCount = yellowCount,
                        redCount = redCount,
                        total = totalRides
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        SemaphoreLabel(color = GreenPositive, count = greenCount, label = "Compensou")
                        SemaphoreLabel(color = YellowWarning, count = yellowCount, label = "Limite")
                        SemaphoreLabel(color = RedAlert, count = redCount, label = "Prejuízo")
                    }
                }
            }

            // Breakdown por Plataforma
            if (platformBreakdown.isNotEmpty()) {
                Text(
                    text = "Corridas por Plataforma",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        platformBreakdown.forEach { (appName, count) ->
                            PlatformBreakdownRow(
                                appName = appName,
                                count = count,
                                total = totalRides
                            )
                        }
                    }
                }
            }
        }

        // Configurações rápidas do veículo
        Text(
            text = "Configuração Atual",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConfigRow(label = "Meta por km", value = Formatters.formatPerKm(settings.minTargetPerKm))
                ConfigRow(label = "Meta por hora", value = Formatters.formatPerHour(settings.minTargetPerHour))
                ConfigRow(label = "Custo por km", value = Formatters.formatPerKm(
                    (settings.fuelPricePerLiter / settings.fuelConsumptionKmL) + settings.additionalCostPerKm
                ))
                ConfigRow(label = "Voz habilitada", value = if (settings.isVoiceEnabled) " Sim" else " Não")
                ConfigRow(label = "Duração do card", value = "${settings.cardDurationSeconds}s")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(text = label, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SemaphoreBar(greenCount: Int, yellowCount: Int, redCount: Int, total: Int) {
    if (total == 0) return
    val greenFrac = greenCount.toFloat() / total
    val yellowFrac = yellowCount.toFloat() / total
    val redFrac = redCount.toFloat() / total

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
    ) {
        if (greenFrac > 0f) Box(modifier = Modifier.weight(greenFrac).fillMaxSize().background(GreenPositive))
        if (yellowFrac > 0f) Box(modifier = Modifier.weight(yellowFrac).fillMaxSize().background(YellowWarning))
        if (redFrac > 0f) Box(modifier = Modifier.weight(redFrac).fillMaxSize().background(RedAlert))
    }
}

@Composable
private fun SemaphoreLabel(color: Color, count: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Column {
            Text(text = "$count", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(text = label, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PlatformBreakdownRow(appName: String, count: Int, total: Int) {
    val fraction = if (total > 0) count.toFloat() / total else 0f
    val pct = (fraction * 100).toInt()
    val badgeColor = appBadgeColor(appName)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(badgeColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = appName,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(text = "$count corridas", color = TextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$pct%",
            color = badgeColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
        Text(text = value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun resolveDisplayAppName(appName: String): String {
    return when {
        appName.contains("uber eats", ignoreCase = true) -> "Uber Eats"
        appName.contains("uber moto", ignoreCase = true) -> "Uber Moto"
        appName.contains("uber", ignoreCase = true) -> "Uber"
        appName.contains("99food", ignoreCase = true) || appName.contains("didi food", ignoreCase = true) -> "99Food"
        appName.contains("99", ignoreCase = true) || appName.contains("didi", ignoreCase = true) -> "99"
        appName.contains("ifood", ignoreCase = true) -> "iFood"
        appName.contains("indrive", ignoreCase = true) -> "inDrive"
        appName.contains("keeta", ignoreCase = true) -> "Keeta"
        else -> appName.ifBlank { "Outro" }
    }
}

private fun appBadgeColor(appName: String): Color {
    return when {
        appName.contains("Uber Eats", ignoreCase = true) -> Color(0xFF102746)
        appName.contains("Uber Moto", ignoreCase = true) -> Color(0xFF6B7280)
        appName.contains("Uber", ignoreCase = true) -> Color(0xFF374151)
        appName.contains("99Food", ignoreCase = true) -> Color(0xFF102746)
        appName.contains("99", ignoreCase = true) -> Color(0xFF102746)
        appName.contains("iFood", ignoreCase = true) -> Color(0xFF102746)
        appName.contains("inDrive", ignoreCase = true) -> Color(0xFF102746)
        appName.contains("Keeta", ignoreCase = true) -> Color(0xFF102746)
        else -> Color(0xFF102746)
    }
}





