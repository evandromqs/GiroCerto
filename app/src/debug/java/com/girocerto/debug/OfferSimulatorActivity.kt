package com.girocerto.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.girocerto.app.theme.GiroCertoTheme
import com.girocerto.domain.model.RideAnalysis
import com.girocerto.domain.model.TrafficLightStatus
import com.girocerto.domain.model.VehicleSettings
import com.girocerto.domain.usecase.AnalyzeRideOffer
import java.util.Locale

/** Activity disponível apenas no APK debug. */
class OfferSimulatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GiroCertoTheme { OfferSimulatorScreen() } }
    }
}

@Composable
private fun OfferSimulatorScreen() {
    val analyzer = remember { AnalyzeRideOffer() }
    val settings = remember { VehicleSettings() }
    var results by remember { mutableStateOf(emptyMap<String, RideAnalysis>()) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Simulador de ofertas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Usa o mesmo motor financeiro do leitor real, sem abrir Uber, 99, iFood ou Keeta.")
            Button(
                onClick = {
                    results = OfferSimulatorFixtures.all.associate { fixture ->
                        fixture.name to analyzer(fixture.parsedOffer(), settings, timestamp = 1L)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Executar todos os cenários") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(OfferSimulatorFixtures.all, key = { it.name }) { fixture ->
                    val analysis = results[fixture.name]
                    val reading = fixture.reading
                    val offer = runCatching { fixture.parsedOffer() }.getOrNull()
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(fixture.name, fontWeight = FontWeight.Bold)
                                Text(
                                    analysis?.trafficLight?.label() ?: "Aguardando",
                                    color = analysis?.trafficLight?.color() ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                offer?.let { "R$ ${money(it.fare)} • ${money(it.totalDistanceKm)} km • ${it.durationMinutes} min" }
                                    ?: "Leitura incompleta: ${reading.error ?: reading.missingFields.joinToString()}"
                            )
                            Text("Texto → parser de acessibilidade → motor financeiro", style = MaterialTheme.typography.labelSmall)
                            analysis?.let {
                                Text("R$ ${money(it.grossPerKm)}/km • lucro R$ ${money(it.estimatedProfit)}")
                                Text(it.decisionReason, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (it.trafficLight == fixture.expected) "Resultado esperado confirmado"
                                    else "Esperado: ${fixture.expected.label()}",
                                    color = if (it.trafficLight == fixture.expected) Color(0xFF16803A) else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun money(value: Double) = String.format(Locale.forLanguageTag("pt-BR"), "%.2f", value)
private fun TrafficLightStatus.label() = when (this) {
    TrafficLightStatus.GREEN -> "VERDE"
    TrafficLightStatus.YELLOW -> "AMARELO"
    TrafficLightStatus.RED -> "VERMELHO"
    TrafficLightStatus.INVALID -> "INVÁLIDO"
}
private fun TrafficLightStatus.color() = when (this) {
    TrafficLightStatus.GREEN -> Color(0xFF16803A)
    TrafficLightStatus.YELLOW -> Color(0xFF9A6700)
    TrafficLightStatus.RED, TrafficLightStatus.INVALID -> Color(0xFFB42336)
}
