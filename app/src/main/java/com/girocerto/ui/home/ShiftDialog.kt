package com.girocerto.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.girocerto.app.GiroCertoApp
import com.girocerto.app.theme.AppSpacing
import com.girocerto.app.theme.BorderDark
import com.girocerto.app.theme.PrimaryBlue
import com.girocerto.app.theme.SurfaceDark
import com.girocerto.app.theme.TextPrimary
import com.girocerto.app.theme.TextSecondary
import com.girocerto.domain.model.ShiftRecord
import com.girocerto.domain.model.WorkDay
import com.girocerto.ui.components.AppText
import com.girocerto.ui.components.NeobrutalButton
import com.girocerto.ui.components.NeobrutalCard
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ShiftDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val shiftRepo = GiroCertoApp.instance.shiftRepository
    val workRepo = GiroCertoApp.instance.workRepository
    val currentShift by shiftRepo.currentShift.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val isActive = currentShift?.isActive == true
    var timeInput by remember(currentShift) { mutableStateOf(ShiftRecord.currentTimeString()) }
    var kmInput by remember(currentShift) { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        NeobrutalCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.Screen),
            backgroundColor = MaterialTheme.colorScheme.surface,
            borderColor = MaterialTheme.colorScheme.outline,
            borderWidth = AppSpacing.BorderThick,
            shadowOffset = AppSpacing.ShadowOffset,
            shape = RoundedCornerShape(AppSpacing.CornerCard)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.Content),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = if (!isActive) "Registrar Ponto • Início" else "Registrar Ponto • Encerramento",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (!isActive) {
                    AppText(
                        text = "Inicie seu turno para acompanhar o tempo de trabalho e os quilômetros reais do odômetro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val activeShift = currentShift!!
                    AppText(
                        text = "Turno em andamento desde às ${activeShift.startTime}" +
                            (activeShift.startKm?.let { " (odômetro inicial: ${String.format(java.util.Locale.GERMANY, "%.0f km", it)})" } ?: "") +
                            ".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = timeInput,
                    onValueChange = { timeInput = it },
                    label = { AppText(if (!isActive) "Horário de início" else "Horário de término") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                OutlinedTextField(
                    value = kmInput,
                    onValueChange = { kmInput = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { AppText(if (!isActive) "Km inicial do veículo (opcional)" else "Km final do odômetro") },
                    placeholder = { AppText("Ex: 45200") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                errorMessage?.let { error ->
                    AppText(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppText("Cancelar")
                    }

                    NeobrutalButton(
                        text = if (!isActive) "Iniciar Turno" else "Encerrar Turno",
                        modifier = Modifier.weight(1.5f),
                        backgroundColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        onClick = {
                            val kmValue = kmInput.replace(',', '.').toDoubleOrNull()
                            if (isActive) {
                                val start = currentShift?.startKm
                                if (start != null && kmValue != null && kmValue < start) {
                                    errorMessage = "O km final não pode ser menor que o inicial (${start.toInt()} km)"
                                    return@NeobrutalButton
                                }
                                val finished = shiftRepo.endShift(timeInput.trim(), kmValue)
                                if (finished != null) {
                                    val duration = finished.durationHours
                                    val totalOdometer = finished.totalOdometerKm
                                    scope.launch {
                                        // Update day record with shift hours/km if available
                                        val today = LocalDate.now()
                                        val existing = workRepo.days.value.firstOrNull { it.date == today }
                                        val profile = workRepo.profile.value
                                        if (existing != null) {
                                            val updated = existing.copy(
                                                activeHours = duration ?: existing.activeHours,
                                                totalKm = totalOdometer ?: existing.totalKm
                                            )
                                            workRepo.saveDay(updated)
                                        } else if (duration != null || totalOdometer != null) {
                                            val day = WorkDay(
                                                date = today,
                                                entries = emptyList(),
                                                totalKm = totalOdometer ?: 0.0,
                                                activeHours = duration ?: 0.0,
                                                fixedCost = profile?.dailyRent ?: 0.0,
                                                variablePerKm = profile?.variablePerKm ?: 0.0,
                                                grossGoal = profile?.dailyGrossGoal ?: 0.0,
                                                weeklyRent = profile?.weeklyRent
                                            )
                                            workRepo.saveDay(day)
                                        }
                                    }
                                    val msg = "Turno encerrado! " +
                                        (duration?.let { String.format(java.util.Locale.GERMANY, "%.1fh", it) } ?: "") +
                                        (totalOdometer?.let { " • " + String.format(java.util.Locale.GERMANY, "%.1f km", it) } ?: "")
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            } else {
                                shiftRepo.startShift(timeInput.trim(), kmValue)
                                Toast.makeText(context, "Turno iniciado às $timeInput!", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}
