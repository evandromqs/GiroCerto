package com.girocerto.ui.history

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import com.girocerto.app.GiroCertoApp
import com.girocerto.app.theme.AppSpacing
import com.girocerto.app.theme.StatusGreenSolid
import com.girocerto.app.theme.StatusRedSolid
import com.girocerto.domain.model.RideAnalysis
import com.girocerto.ui.components.AppText
import com.girocerto.ui.components.ConfirmationDialog
import com.girocerto.ui.components.Formatters
import com.girocerto.ui.components.NeobrutalButton
import com.girocerto.ui.components.NeobrutalCard
import com.girocerto.ui.components.TrafficLightBadge
import com.girocerto.ui.profile.PassengerMessagePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
fun HistoryScreen(
    onShowOverlay: (RideAnalysis) -> Unit,
    onNavigateToCopilot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: HistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = HistoryViewModel.Factory(GiroCertoApp.instance.rideRepository)
    )
    val rides by viewModel.rides.collectAsStateWithLifecycle()
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()
    var platformFilter by rememberSaveable { mutableStateOf("Todos") }
    var periodFilter by rememberSaveable { mutableStateOf("Todo período") }
    val platforms by produceState(listOf("Todos"), rides) { value = withContext(Dispatchers.Default) { listOf("Todos") + rides.map { it.offer.appName }.distinct().sorted() } }
    val currentDay = com.girocerto.ui.components.rememberCurrentDay()
    val startOfToday = remember(currentDay) { currentDay.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val periodStart = when (periodFilter) {
        "Hoje" -> startOfToday
        "7 dias" -> Calendar.getInstance().apply { timeInMillis = startOfToday; add(Calendar.DAY_OF_YEAR, -6) }.timeInMillis
        else -> 0L
    }
    val filteredRides by produceState(emptyList<RideAnalysis>(), rides, platformFilter, periodStart) {
        value = withContext(Dispatchers.Default) {
            HistoryFilter.apply(rides, platformFilter.takeUnless { it == "Todos" }, periodStart)
        }
    }
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var rideToDelete by remember { mutableStateOf<RideAnalysis?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(AppSpacing.Screen)
    ) {
        SnackbarHost(hostState = snackbarHostState)
        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(AppSpacing.CornerSmall))
            )
        }
        loadError?.let { message ->
            AppText(message, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = viewModel::retryLoad) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_refresh),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    AppText("Tentar recuperar histórico")
                }
            }
        }

        // Barra Superior do Histórico
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppText(
                text = "Histórico de Análises",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            AppText(
                text = "${rides.size} corridas salvas localmente",
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start
            )
            if (rides.isNotEmpty()) {
                TextButton(
                    onClick = { showClearAllDialog = true }
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_trash),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(6.dp))
                    AppText("Limpar tudo", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            platforms.forEach { platform ->
                FilterChip(
                    selected = platformFilter == platform,
                    onClick = { platformFilter = platform },
                    label = { AppText(platform, fontWeight = if (platformFilter == platform) FontWeight.Bold else FontWeight.Normal) },
                    shape = RoundedCornerShape(AppSpacing.CornerSmall),
                    border = BorderStroke(AppSpacing.BorderThin, MaterialTheme.colorScheme.outline)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val periodOptions = listOf("Todo período", "Hoje", "7 dias")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                periodOptions.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = periodFilter == period,
                        onClick = { periodFilter = period },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = periodOptions.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            activeContentColor = MaterialTheme.colorScheme.primary,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            inactiveBorderColor = MaterialTheme.colorScheme.outline
                        )
                    ) {
                        AppText(
                            period,
                            fontWeight = if (periodFilter == period) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        if (rides.isNotEmpty() && filteredRides.isEmpty()) {
            AppText(
                "Nenhuma análise corresponde aos filtros.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                textAlign = TextAlign.Center
            )
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
                    try { viewModel.deleteRide(ride.id) } catch (error: java.io.IOException) {
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
                    try { viewModel.clearHistory() } catch (error: java.io.IOException) {
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
    var showMessageDraft by rememberSaveable { mutableStateOf(false) }
    var passengerDraft by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    if (showMessageDraft) {
        AlertDialog(
            onDismissRequest = { showMessageDraft = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                AppText(
                    "Revise a mensagem",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText("Você escolherá o aplicativo e o destinatário e confirmará o envio. O Giro Certo não identifica o passageiro nem envia mensagens automaticamente.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = passengerDraft,
                        onValueChange = { passengerDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(AppSpacing.CornerSmall),
                        label = { AppText("Mensagem") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passengerDraft.isNotBlank(),
                    onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, passengerDraft.trim())
                        }, "Escolher aplicativo"))
                        showMessageDraft = false
                    },
                    shape = RoundedCornerShape(AppSpacing.CornerButton)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_share),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    AppText("Escolher aplicativo", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showMessageDraft = false },
                    shape = RoundedCornerShape(AppSpacing.CornerButton)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_close),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    AppText("Cancelar", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    NeobrutalCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline,
        borderWidth = AppSpacing.BorderThick,
        shadowOffset = AppSpacing.ShadowOffset,
        shape = RoundedCornerShape(AppSpacing.CornerCard)
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.Content)
        ) {
            // Linha 1: Valor e Semáforo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(
                    text = Formatters.formatCurrency(ride.offer.fare),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
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
                AppText(
                    text = "${Formatters.formatKm(ride.totalDistanceKm)} • ${Formatters.formatDuration(ride.offer.durationMinutes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                AppText(
                    text = "Lucro: ${Formatters.formatCurrency(ride.estimatedProfit)}",
                    color = if (ride.estimatedProfit >= 0) StatusGreenSolid else StatusRedSolid,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Linha 3: R$/km e data/hora
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AppText(
                    text = "${Formatters.formatPerKm(ride.grossPerKm)} • ${Formatters.formatOfferPerHour(ride.grossPerHour, ride.offer.durationMinutes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                AppText(
                    text = Formatters.formatDateTime(ride.timestamp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Área Expansível com Detalhes Completos
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(AppSpacing.CornerSmall))
                            .border(BorderStroke(AppSpacing.BorderThin, MaterialTheme.colorScheme.outline), RoundedCornerShape(AppSpacing.CornerSmall))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AppText(
                                text = "Motivo da Avaliação:",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            AppText(
                                text = ride.decisionReason,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            AppText(
                                text = "Composição Financeira:",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            AppText(text = "• Combustível: ${Formatters.formatCurrency(ride.fuelCost)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            AppText(text = "• Custos Operacionais extras: ${Formatters.formatCurrency(ride.additionalOperationalCost)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            if (ride.fixedCost > 0) {
                                AppText(text = "• Custo fixo da corrida: ${Formatters.formatCurrency(ride.fixedCost)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            AppText(text = "• Custo Total Estimado: ${Formatters.formatCurrency(ride.totalCost)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            AppText(text = "• Margem de Lucro: ${Formatters.formatPercent(ride.estimatedMarginPercent)}", color = StatusGreenSolid, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = {
                            passengerDraft = PassengerMessagePreferences(context).read()
                            showMessageDraft = true
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = AppSpacing.MinTouchTarget)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_share),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        AppText("Compartilhar mensagem ao passageiro", fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = {
                            val message = "Giro Certo · ${ride.offer.appName}\nOferta: ${Formatters.formatCurrency(ride.offer.fare)}\nDistância: ${Formatters.formatKm(ride.totalDistanceKm)}\nLucro estimado: ${Formatters.formatCurrency(ride.estimatedProfit)}\n${ride.decisionReason}"
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message)
                            }, "Compartilhar análise"))
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = AppSpacing.MinTouchTarget)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_share),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        AppText("Compartilhar resumo", fontWeight = FontWeight.SemiBold)
                    }
                    ride.offer.pickupAddress.takeIf { it.isNotBlank() }?.let { address ->
                        TextButton(
                            onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))) }
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = AppSpacing.MinTouchTarget)
                        ) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_map),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            AppText("Ver embarque no mapa", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    ride.offer.destinationAddress.takeIf { it.isNotBlank() }?.let { address ->
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))
                                runCatching { context.startActivity(intent) }
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = AppSpacing.MinTouchTarget)
                        ) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_map),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            AppText("Ver destino no mapa", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NeobrutalButton(
                            onClick = onShowOverlay,
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            borderColor = MaterialTheme.colorScheme.outline,
                            borderWidth = AppSpacing.BorderAction,
                            shape = RoundedCornerShape(AppSpacing.CornerButton),
                            text = "Abrir Janela Flutuante",
                            leadingIcon = {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_open_app),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )

                        TextButton(
                            onClick = onDelete,
                            modifier = Modifier.heightIn(min = AppSpacing.MinTouchTarget)
                        ) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_trash),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(6.dp))
                            AppText("Excluir", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryView(onNavigateToCopilot: () -> Unit) {
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppText(
                text = "Nenhuma corrida salva ainda",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            AppText(
                text = "Salve ofertas pela janela flutuante ou ative o salvamento automático nos ajustes do copiloto.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            NeobrutalButton(
                onClick = onNavigateToCopilot,
                backgroundColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(AppSpacing.CornerButton),
                text = "Configurar copiloto",
                leadingIcon = {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_copilot),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            )
        }
    }
}

