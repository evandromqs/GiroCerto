package com.girocerto.ui.home

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.girocerto.app.GiroCertoApp
import com.girocerto.domain.model.*
import com.girocerto.ui.components.Formatters
import com.girocerto.ui.components.NavigationGlyph
import com.girocerto.navigation.Screen
import com.girocerto.ui.goals.MoneyField
import com.girocerto.ui.goals.SummaryLine
import com.girocerto.ui.goals.inputNumber
import com.girocerto.monitoring.GiroCertoAccessibilityService
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun WorkDashboard(onRecord: () -> Unit, onGoals: () -> Unit, onCopilot: () -> Unit, onOffers: () -> Unit) {
    val repo = GiroCertoApp.instance.workRepository
    val profile by repo.profile.collectAsState()
    val days by repo.days.collectAsState()
    val running by GiroCertoAccessibilityService.isServiceRunning.collectAsState()
    val today = days.firstOrNull { it.date == LocalDate.now() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Giro Certo", style = MaterialTheme.typography.headlineMedium)
        Text(profile?.let { "Olá, ${it.name}. Vamos fechar seu dia?" } ?: "Seu trabalho, suas metas, tudo em um lugar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (profile == null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Comece pela sua moto", style = MaterialTheme.typography.titleLarge)
                    Text("Responda quatro passos rápidos. O Giro Certo calcula quanto buscar por dia, hora e km.")
                    Button(onClick = onGoals, modifier = Modifier.fillMaxWidth()) { Text("Criar meu perfil") }
                }
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Hoje • ${LocalDate.now().format(dayFormat)}")
                    Text(if (today == null) "Dia ainda não registrado" else Formatters.formatCurrency(today.net),
                        style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    if (today != null) {
                        Text("Saldo após os custos cadastrados", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SummaryLine("Recebido nos apps", Formatters.formatCurrency(today.revenue))
                        SummaryLine("Custos do dia", Formatters.formatCurrency(today.cost))
                    }
                    SummaryLine("Meta bruta do dia", Formatters.formatCurrency(today?.grossGoal ?: profile!!.dailyGrossGoal))
                    if (today != null && today.grossGoal > 0) LinearProgressIndicator(
                        progress = { (today.revenue / today.grossGoal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = onRecord, modifier = Modifier.fillMaxWidth()) { Text(if (today == null) "Registrar meu dia" else "Editar meu dia") }
                }
            }
        }
        OutlinedButton(onClick = onCopilot, modifier = Modifier.fillMaxWidth()) {
            NavigationGlyph(Screen.COPILOT, true); Spacer(Modifier.width(10.dp))
            Text(if (running) "Copiloto conectado • ver ajustes" else "Configurar leitura de ofertas")
        }
        TextButton(onClick = onOffers) { Text("Ver ofertas analisadas") }
        if (days.isNotEmpty()) {
            Text("Últimos fechamentos", style = MaterialTheme.typography.titleMedium)
            days.take(5).forEach { day ->
                SummaryLine(day.date.format(dayFormat), Formatters.formatCurrency(day.net))
            }
            Text("Ganhos informados por você. Ofertas analisadas não entram no saldo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun WorkDiaryScreen(onProfile: () -> Unit) {
    val repo = GiroCertoApp.instance.workRepository
    val profile by repo.profile.collectAsState()
    val days by repo.days.collectAsState()
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = LocalDate.parse(selectedDate)
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Meus ganhos", style = MaterialTheme.typography.headlineMedium)
        Text("Informe o que recebeu em cada app após as taxas da plataforma.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = {
            DatePickerDialog(context, { _, y, m, d -> selectedDate = LocalDate.of(y, m + 1, d).toString() },
                date.year, date.monthValue - 1, date.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
        }, modifier = Modifier.fillMaxWidth()) { Text("Dia: ${date.format(dayFormat)} • alterar") }
        if (profile == null) {
            Text("Cadastre os custos da sua moto antes do primeiro fechamento.")
            Button(onClick = onProfile) { Text("Criar perfil") }
        } else key(selectedDate) {
            DayEditor(date, days.firstOrNull { it.date == date }, profile!!)
        }
        Text("Dias registrados", style = MaterialTheme.typography.titleMedium)
        if (days.isEmpty()) Text("Seus fechamentos aparecerão aqui.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        days.forEach { day ->
            TextButton(onClick = { selectedDate = day.date.toString() }, modifier = Modifier.fillMaxWidth()) {
                Text(day.date.format(dayFormat), Modifier.weight(1f))
                Text(Formatters.formatCurrency(day.net))
            }
        }
    }
}

@Composable
private fun DayEditor(date: LocalDate, existing: WorkDay?, profile: ElectricProfile) {
    val repo = GiroCertoApp.instance.workRepository
    val scope = rememberCoroutineScope()
    var km by rememberSaveable { mutableStateOf(existing?.totalKm?.toString() ?: "") }
    var hours by rememberSaveable { mutableStateOf(existing?.activeHours?.toString() ?: "") }
    var revenueFields by rememberSaveable { mutableStateOf(ArrayList(WorkPlatform.entries.map { p -> existing?.entries?.find { it.platform == p }?.revenue?.toString() ?: "0" })) }
    var kmFields by rememberSaveable { mutableStateOf(ArrayList(WorkPlatform.entries.map { p -> existing?.entries?.find { it.platform == p }?.km?.toString() ?: "0" })) }
    var hourFields by rememberSaveable { mutableStateOf(ArrayList(WorkPlatform.entries.map { p -> existing?.entries?.find { it.platform == p }?.hours?.toString() ?: "0" })) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var deletePrompt by remember { mutableStateOf(false) }
    val entries = WorkPlatform.entries.mapIndexed { i, p -> PlatformWork(p, inputNumber(revenueFields[i]) ?: -1.0, inputNumber(kmFields[i]) ?: -1.0, inputNumber(hourFields[i]) ?: -1.0) }
    val draft = WorkDay(date, entries, inputNumber(km) ?: -1.0, inputNumber(hours) ?: -1.0,
        existing?.fixedCost ?: profile.dailyRent, existing?.variablePerKm ?: profile.variablePerKm,
        existing?.grossGoal ?: profile.dailyGrossGoal)
    WorkPlatform.entries.forEachIndexed { i, platform ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField("${platform.label} • recebido (R$)", revenueFields[i], {
                    revenueFields = ArrayList(revenueFields).apply { set(i, it) }; message = null
                })
                TextButton(onClick = { expanded = if (expanded == platform.name) null else platform.name }) {
                    Text(if (expanded == platform.name) "Ocultar detalhes" else "Km e horas em ${platform.label}")
                }
                if (expanded == platform.name) {
                    MoneyField("Km atribuídos ao app", kmFields[i], { kmFields = ArrayList(kmFields).apply { set(i, it) }; message = null })
                    MoneyField("Horas ativas no app", hourFields[i], { hourFields = ArrayList(hourFields).apply { set(i, it) }; message = null })
                    Text("Opcional. Esses detalhes não são somados aos totais reais abaixo.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Text("Totais reais do dia", style = MaterialTheme.typography.titleLarge)
    MoneyField("Km rodados no dia", km, { km = it; message = null })
    MoneyField("Horas ativas no dia", hours, { hours = it; message = null })
    Text("Inclua todos os deslocamentos e a espera. Se dois apps ficaram ligados por 3 horas juntos, o total é 3 horas, não 6.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (draft.valid()) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryLine("Recebido", Formatters.formatCurrency(draft.revenue))
                SummaryLine("Fixo rateado do dia", Formatters.formatCurrency(draft.fixedCost))
                SummaryLine("Custo dos km", Formatters.formatCurrency(draft.totalKm * draft.variablePerKm))
                HorizontalDivider()
                SummaryLine("Saldo estimado", Formatters.formatCurrency(draft.net))
                SummaryLine("Recebido por hora", draft.grossPerHour?.let { Formatters.formatPerHour(it) } ?: "Sem horas")
                SummaryLine("Recebido por km", draft.grossPerKm?.let { Formatters.formatPerKm(it) } ?: "Sem km")
            }
        }
    } else Text("Preencha valores válidos: até 24 horas e 2.000 km no dia. Use zero quando não houver movimento.", style = MaterialTheme.typography.bodySmall)
    Text("O fixo é rateado pelos dias planejados. Trabalhar menos dias não reduz a cobrança semanal. Custos e meta ficam guardados com o dia ao salvar pela primeira vez.", style = MaterialTheme.typography.bodySmall)
    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    Button(onClick = { scope.launch {
        busy = true
        try { repo.saveDay(draft); message = "Dia salvo. Você pode editar sem duplicar os ganhos." }
        catch (_: Exception) { message = "Não foi possível salvar. Tente novamente." }
        finally { busy = false }
    } }, enabled = draft.valid() && !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Salvando…" else "Salvar fechamento") }
    if (existing != null) TextButton(onClick = { deletePrompt = true }, enabled = !busy) { Text("Excluir este dia", color = MaterialTheme.colorScheme.error) }
    if (deletePrompt) AlertDialog(onDismissRequest = { deletePrompt = false }, title = { Text("Excluir fechamento?") },
        text = { Text("Os ganhos registrados em ${date.format(dayFormat)} serão removidos.") },
        confirmButton = { TextButton(onClick = { scope.launch {
            busy = true
            try {
                repo.deleteDay(date); km = ""; hours = ""
                revenueFields = ArrayList(List(6) { "0" }); kmFields = ArrayList(List(6) { "0" }); hourFields = ArrayList(List(6) { "0" })
                message = "Fechamento excluído."
            } catch (_: Exception) { message = "Não foi possível excluir." }
            finally { busy = false; deletePrompt = false }
        } }, enabled = !busy) { Text("Excluir") } },
        dismissButton = { TextButton(onClick = { deletePrompt = false }, enabled = !busy) { Text("Cancelar") } })
}
