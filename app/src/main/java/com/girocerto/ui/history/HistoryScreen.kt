package com.girocerto.ui.history

import com.girocerto.ui.components.AppText as Text

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import com.girocerto.ui.components.TactileCard as Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.girocerto.ui.profile.PassengerMessagePreferences
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import android.content.Intent
import android.net.Uri
import java.util.Calendar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.girocerto.domain.model.RideAnalysis
import com.girocerto.ui.components.ConfirmationDialog
import com.girocerto.ui.components.Formatters
import com.girocerto.ui.components.TrafficLightBadge
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    onShowOverlay: (RideAnalysis) -> Unit,
    onNavigateToCopilot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rideRepo = GiroCertoApp.instance.rideRepository
    val rides by rideRepo.rides.collectAsState()
    var platformFilter by rememberSaveable { mutableStateOf("Todos") }
    var periodFilter by rememberSaveable { mutableStateOf("Todo período") }
    val platforms = listOf("Todos") + rides.map { it.offer.appName }.distinct().sorted()
    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val periodStart = when (periodFilter) {
        "Hoje" -> startOfToday
        "7 dias" -> Calendar.getInstance().apply { timeInMillis = startOfToday; add(Calendar.DAY_OF_YEAR, -6) }.timeInMillis
        else -> 0L
    }
    val filteredRides = rides.filter {
        (platformFilter == "Todos" || it.offer.appName == platformFilter) && it.timestamp >= periodStart
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var rideToDelete by remember { mutableStateOf<RideAnalysis?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        SnackbarHost(hostState = snackbarHostState)

        // Barra Superior do Histórico
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Histórico de Análises",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${rides.size} corridas salvas localmente",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            if (rides.isNotEmpty()) {
                TextButton(
                    onClick = { showClearAllDialog = true }
                ) {
                    Text("Limpar tudo", color = RedAlert, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            platforms.forEach { platform ->
                FilterChip(selected = platformFilter == platform, onClick = { platformFilter = platform }, label = { Text(platform) })
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Todo período", "Hoje", "7 dias").forEach { period ->
                FilterChip(selected = periodFilter == period, onClick = { periodFilter = period }, label = { Text(period) })
            }
        }
        if (rides.isNotEmpty() && filteredRides.isEmpty()) {
            Text("Nenhuma análise corresponde aos filtros.", color = TextSecondary, modifier = Modifier.padding(vertical = 16.dp))
        }
        if (rides.isEmpty()) {
            EmptyHistoryView(onNavigateToCopilot = onNavigateToCopilot)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredRides, key = { it.id }) { ride ->
                    HistoryItemCard(
                        ride = ride,
                        onDelete = { rideToDelete = ride },
                        onShowOverlay = { onShowOverlay(ride) }
                    )
                }
            }
        }
    }

    // Modal de Confirmação para exclusão individual
    rideToDelete?.let { ride ->
        ConfirmationDialog(
            title = "Excluir Corrida",
            message = "Deseja remover esta análise de ${Formatters.formatCurrency(ride.offer.fare)} do seu histórico?",
            confirmText = "Excluir",
            onConfirm = {
                scope.launch {
                    try { rideRepo.deleteRide(ride.id) } catch (error: java.io.IOException) {
                        snackbarHostState.showSnackbar("Não foi possível alterar o histórico. Tente novamente.")
                        return@launch
                    }
                    rideToDelete = null
                    snackbarHostState.showSnackbar("Registro removido.")
                }
            },
            onDismiss = { rideToDelete = null }
        )
    }

    // Modal de Confirmação para limpar tudo
    if (showClearAllDialog) {
        ConfirmationDialog(
            title = "Limpar Todo o Histórico",
            message = "Tem certeza de que deseja apagar todas as análises salvas? Esta ação não pode ser desfeita.",
            confirmText = "Limpar Tudo",
            onConfirm = {
                scope.launch {
                    try { rideRepo.clearHistory() } catch (error: java.io.IOException) {
                        snackbarHostState.showSnackbar("Não foi possível alterar o histórico. Tente novamente.")
                        return@launch
                    }
                    showClearAllDialog = false
                    snackbarHostState.showSnackbar("Histórico limpo com sucesso.")
                }
            },
            onDismiss = { showClearAllDialog = false }
        )
    }
}

@Composable
private fun HistoryItemCard(
    ride: RideAnalysis,
    onDelete: () -> Unit,
    onShowOverlay: () -> Unit
) {
    val context = LocalContext.current
    var showMessageDraft by remember { mutableStateOf(false) }
    var passengerDraft by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    if (showMessageDraft) {
        AlertDialog(
            onDismissRequest = { showMessageDraft = false },
            title = { Text("Revise a mensagem") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("Você escolherá o aplicativo e o destinatário e confirmará o envio. O Giro Certo não identifica o passageiro nem envia mensagens automaticamente.")
                    OutlinedTextField(value = passengerDraft, onValueChange = { passengerDraft = it },
                        modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
                        label = { Text("Mensagem") })
                }
            },
            confirmButton = {
                TextButton(enabled = passengerDraft.isNotBlank(), onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, passengerDraft.trim())
                    }, "Escolher aplicativo"))
                    showMessageDraft = false
                }) { Text("Escolher aplicativo") }
            },
            dismissButton = { TextButton(onClick = { showMessageDraft = false }) { Text("Cancelar") } }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Linha 1: Valor e Semáforo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Formatters.formatCurrency(ride.offer.fare),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TrafficLightBadge(status = ride.trafficLight)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Linha 2: Distância, tempo e Lucro
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Linha 3: R$/km e data/hora
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${Formatters.formatPerKm(ride.grossPerKm)} • ${Formatters.formatOfferPerHour(ride.grossPerHour, ride.offer.durationMinutes)}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = Formatters.formatDateTime(ride.timestamp),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            if (expanded) {
                TextButton(onClick = {
                    passengerDraft = PassengerMessagePreferences(context).read()
                    showMessageDraft = true
                }) { Text("Compartilhar mensagem ao passageiro") }
                TextButton(onClick = {
                    val message = "Giro Certo · ${ride.offer.appName}\nOferta: ${Formatters.formatCurrency(ride.offer.fare)}\nDistância: ${Formatters.formatKm(ride.totalDistanceKm)}\nLucro estimado: ${Formatters.formatCurrency(ride.estimatedProfit)}\n${ride.decisionReason}"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message)
                    }, "Compartilhar análise"))
                }) { Text("Compartilhar resumo") }
                ride.offer.pickupAddress.takeIf { it.isNotBlank() }?.let { address ->
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))) }
                    }) { Text("Ver embarque no mapa") }
                }
                ride.offer.destinationAddress.takeIf { it.isNotBlank() }?.let { address ->
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))
                        runCatching { context.startActivity(intent) }
                    }) { Text("Ver destino no mapa") }
                }
            }
            // Área Expansível com Detalhes Completos
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Motivo da Avaliação:",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = ride.decisionReason,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Composição Financeira:",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(text = "• Combustível: ${Formatters.formatCurrency(ride.fuelCost)}", color = TextSecondary, fontSize = 11.sp)
                            Text(text = "• Custos Operacionais extras: ${Formatters.formatCurrency(ride.additionalOperationalCost)}", color = TextSecondary, fontSize = 11.sp)
                            if (ride.fixedCost > 0) {
                                Text(text = "• Custo fixo da corrida: ${Formatters.formatCurrency(ride.fixedCost)}", color = TextSecondary, fontSize = 11.sp)
                            }
                            Text(text = "• Custo Total Estimado: ${Formatters.formatCurrency(ride.totalCost)}", color = TextSecondary, fontSize = 11.sp)
                            Text(text = "• Margem de Lucro: ${Formatters.formatPercent(ride.estimatedMarginPercent)}", color = GreenPositive, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onShowOverlay,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                            border = BorderStroke(1.dp, BorderDark)
                        ) {
                            Text("Abrir Janela Flutuante", fontSize = 11.sp)
                        }

                        TextButton(
                            onClick = onDelete
                        ) {
                            Text("Excluir", color = RedAlert, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryView(onNavigateToCopilot: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Nenhuma corrida salva ainda",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Salve ofertas pela janela flutuante ou ative o salvamento automático nos ajustes do copiloto.",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onNavigateToCopilot,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                border = BorderStroke(1.dp, PrimaryBlue)
            ) {
                Text("Configurar copiloto")
            }
        }
    }
}







