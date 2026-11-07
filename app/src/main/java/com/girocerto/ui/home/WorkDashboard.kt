package com.girocerto.ui.home

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.girocerto.app.theme.AppSpacing
import com.girocerto.domain.model.*
import com.girocerto.domain.repository.ShiftTrackerRepository
import com.girocerto.domain.repository.WorkLedgerRepository
import com.girocerto.monitoring.GiroCertoAccessibilityService
import com.girocerto.navigation.Screen
import com.girocerto.ui.components.AppText
import com.girocerto.ui.components.Formatters
import com.girocerto.ui.components.NavigationGlyph
import com.girocerto.ui.components.NeobrutalButton
import com.girocerto.ui.components.NeobrutalCard
import com.girocerto.ui.components.PlatformIcon
import com.girocerto.ui.components.TrafficLightBadge
import com.girocerto.ui.components.rememberCurrentDay
import com.girocerto.ui.goals.MoneyField
import com.girocerto.ui.goals.SummaryLine
import com.girocerto.ui.goals.inputNumber
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun WorkDashboard(
    workRepository: WorkLedgerRepository,
    shiftRepository: ShiftTrackerRepository,
    onRecord: () -> Unit,
    onGoals: () -> Unit,
    onCopilot: () -> Unit,
    onOffers: () -> Unit,
    onShift: () -> Unit = {}
) {
    val repo = workRepository
    val profile by repo.profile.collectAsStateWithLifecycle()
    val days by repo.days.collectAsStateWithLifecycle()
    val running by GiroCertoAccessibilityService.isServiceRunning.collectAsStateWithLifecycle()
    val currentDay = rememberCurrentDay()
    val today = days.firstOrNull { it.date == currentDay }
    val loading by repo.loading.collectAsStateWithLifecycle()
    val loadError by repo.loadError.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(AppSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Section),
        horizontalAlignment = Alignment.Start
    ) {
        AppText(
            text = "Giro Certo",
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        AppText(
            text = profile?.let { "Olá, ${it.name}. Vamos fechar seu dia?" } ?: "Seu trabalho, suas metas, tudo em um lugar.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val context = LocalContext.current
        val policy = com.girocerto.monitoring.CopilotPreferences(context).read()
        val currentShift by shiftRepository.currentShift.collectAsStateWithLifecycle()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NeobrutalButton(
                text = if (running && policy.enabled) "Copiloto" else "Ativar",
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_copilot), null, tint = MaterialTheme.colorScheme.onPrimary) },
                onClick = {
                    if (GiroCertoAccessibilityService.isAccessibilityEnabled(context) && com.girocerto.overlay.OverlayController.canDrawOverlays(context)) {
                        val preferences = com.girocerto.monitoring.CopilotPreferences(context)
                        preferences.write(preferences.read().copy(enabled = true))
                        com.girocerto.overlay.OverlayController.restore(context)
                        com.girocerto.overlay.OverlayController.startFloatingBubble(context)
                    } else onCopilot()
                }
            )
            NeobrutalButton(
                text = if (currentShift?.isActive == true) "Ponto (${currentShift?.startTime})" else "Registrar Ponto",
                modifier = Modifier.weight(1.2f),
                leadingIcon = { Icon(androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_trip_clock), null, tint = MaterialTheme.colorScheme.onPrimary) },
                onClick = onShift
            )
        }

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(AppSpacing.CornerSmall))
            )
            AppText("Carregando seus ganhos…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (profile == null) {
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
                        text = "Comece pelo seu veículo",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AppText(
                        text = "Responda cinco passos rápidos. O Giro Certo calcula quanto buscar por dia, hora e km.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NeobrutalButton(
                        onClick = onGoals,
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(AppSpacing.CornerButton),
                        text = "Criar meu perfil",
                        leadingIcon = { Icon(androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_profile), null, tint = MaterialTheme.colorScheme.onPrimary) }
                    )
                }
            }
        } else {
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
                    val goal = today?.grossGoal ?: profile!!.dailyGrossGoal
                    val gap = today?.let { it.revenue - goal }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppText(
                            text = "Hoje • ${currentDay.format(dayFormat)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (gap != null) {
                            val status = if (gap >= 0) TrafficLightStatus.GREEN else if (goal > 0 && gap >= -goal * 0.15) TrafficLightStatus.YELLOW else TrafficLightStatus.RED
                            TrafficLightBadge(status = status)
                        }
                    }

                    AppText(
                        text = if (today == null) "Dia ainda não registrado" else Formatters.formatCurrency(today.net),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (today != null) {
                        AppText(
                            text = "Saldo após os custos cadastrados",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SummaryLine("Recebido nos apps", Formatters.formatCurrency(today.revenue))
                        SummaryLine("Custos do dia", Formatters.formatCurrency(today.cost))
                    }
                    SummaryLine("Meta bruta do dia", Formatters.formatCurrency(goal))

                    if (gap != null && goal > 0) {
                        LinearProgressIndicator(
                            progress = { (today.revenue / goal).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(AppSpacing.CornerSmall))
                        )
                        AppText(
                            text = if (gap >= 0) "${Formatters.formatCurrency(gap)} acima da meta" else "Faltam ${Formatters.formatCurrency(-gap)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    NeobrutalButton(
                        onClick = onRecord,
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(AppSpacing.CornerButton),
                        text = if (today == null) "Registrar meu dia" else "Editar meu dia",
                        leadingIcon = {
                            Icon(
                                androidx.compose.ui.res.painterResource(
                                    if (today == null) com.girocerto.app.R.drawable.ic_diary else com.girocerto.app.R.drawable.ic_edit
                                ),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    )
                }
            }
        }

        loadError?.let { AppText(it, color = MaterialTheme.colorScheme.error) }
        profile?.let { WeeklySummary(currentDay, days, it) }

        NeobrutalButton(
            onClick = onCopilot,
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            borderColor = MaterialTheme.colorScheme.outline,
            borderWidth = AppSpacing.BorderAction,
            shape = RoundedCornerShape(AppSpacing.CornerButton),
            text = "Ajustes do copiloto",
            leadingIcon = { NavigationGlyph(Screen.COPILOT, true) }
        )

        NeobrutalButton(
            onClick = onOffers,
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            borderColor = MaterialTheme.colorScheme.outline,
            borderWidth = AppSpacing.BorderAction,
            shape = RoundedCornerShape(AppSpacing.CornerButton),
            text = "Ver ofertas analisadas",
            leadingIcon = { NavigationGlyph(Screen.HISTORY, true) }
        )

        if (days.isNotEmpty()) {
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
                    AppText(
                        text = "Últimos fechamentos",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    days.take(5).forEachIndexed { index, day ->
                        if (index > 0) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        SummaryLine(day.date.format(dayFormat), Formatters.formatCurrency(day.net))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    AppText(
                        text = "Ganhos informados por você. Ofertas analisadas não entram no saldo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun WorkDiaryScreen(
    workRepository: WorkLedgerRepository,
    openTodayRequest: Int = 0,
    onProfile: () -> Unit
) {
    val repo = workRepository
    val profile by repo.profile.collectAsStateWithLifecycle()
    val days by repo.days.collectAsStateWithLifecycle()
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    val viewModel: LedgerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = LedgerViewModel.Factory(
            repository = repo,
            draftStore = com.girocerto.data.repository.LedgerDraftStore(
                androidx.compose.ui.platform.LocalContext.current
            )
        )
    )
    val loading by repo.loading.collectAsStateWithLifecycle()
    val loadError by repo.loadError.collectAsStateWithLifecycle()
    val revision by repo.restoreRevision.collectAsStateWithLifecycle()
    val dayDrafts = key(revision) { androidx.compose.runtime.saveable.rememberSaveableStateHolder() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var consumedRequest by rememberSaveable { mutableStateOf(0) }
    var visited by rememberSaveable { mutableStateOf(arrayListOf<String>()) }

    LaunchedEffect(openTodayRequest) {
        if (openTodayRequest != consumedRequest) {
            selectedDate = LocalDate.now().toString()
            consumedRequest = openTodayRequest
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(selectedDate) {
        val recent = (visited - selectedDate) + selectedDate
        recent.dropLast(12).forEach { dayDrafts.removeState(it) }
        visited = ArrayList(recent.takeLast(12))
    }
    val date = remember(selectedDate) { LocalDate.parse(selectedDate) }
    val recordedDates = remember(days) { days.map { it.date }.toSet() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        state = listState,
        contentPadding = PaddingValues(AppSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Section),
        horizontalAlignment = Alignment.Start
    ) {
        item(key = "editor") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppText(
                    text = "Meus ganhos",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                AppText(
                    text = "Informe o que recebeu em cada app após as taxas da plataforma.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val todayDate = rememberCurrentDay()
                DiaryDateHeader(date = date, onOpenCalendar = { showCalendar = true })
                DiaryWeekSelector(
                    selected = date,
                    today = todayDate,
                    recorded = recordedDates,
                    onSelect = { selectedDate = it.toString() }
                )
                if (showCalendar) {
                    DiaryCalendarDialog(
                        initial = date,
                        today = todayDate,
                        recorded = recordedDates,
                        onSelect = { selectedDate = it.toString(); showCalendar = false },
                        onDismiss = { showCalendar = false }
                    )
                }
                WorkPlatformPicker(repo)
                loadError?.let {
                    AppText(it, color = MaterialTheme.colorScheme.error)
                    TextButton(
                        onClick = repo::retryLoad,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_refresh),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        AppText("Tentar recuperar")
                    }
                }
                if (loading || !viewModel.draftsLoaded) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(AppSpacing.CornerSmall))
                    )
                } else if (profile == null) {
                    AppText("Cadastre os custos do seu veículo antes do primeiro fechamento.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NeobrutalButton(
                        onClick = onProfile,
                        text = "Criar perfil",
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(AppSpacing.CornerButton),
                        leadingIcon = { NavigationGlyph(Screen.PROFILE, true) }
                    )
                } else {
                    dayDrafts.SaveableStateProvider(selectedDate) {
                        DayEditor(date, days.firstOrNull { it.date == date }, profile!!, viewModel, repo)
                    }
                }
                AppText(
                    text = "Dias registrados",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (days.isEmpty()) AppText("Seus fechamentos aparecerão aqui.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                profile?.let { WeeklySummary(date, days, it) }
            }
        }
        items(days, key = { it.date.toString() }) { day ->
            TextButton(
                onClick = { selectedDate = day.date.toString() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppSpacing.MinTouchTarget)
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_calendar),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                AppText(day.date.format(dayFormat), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                AppText(Formatters.formatCurrency(day.net), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun DayEditor(
    date: LocalDate,
    existing: WorkDay?,
    profile: ElectricProfile,
    viewModel: LedgerViewModel,
    repo: WorkLedgerRepository
) {
    val cached = viewModel.draft(date)
    val cachedPlatformCount = cached?.let { (it.size - 2).takeIf { fields -> fields >= 0 && fields % 3 == 0 }?.div(3) } ?: 0
    fun cachedPlatformField(group: Int, index: Int): String? = cached
        ?.takeIf { index < cachedPlatformCount }
        ?.getOrNull(2 + group * cachedPlatformCount + index)
    var km by rememberSaveable { mutableStateOf(cached?.get(0) ?: existing?.totalKm?.toString() ?: "") }
    var hours by rememberSaveable { mutableStateOf(cached?.get(1) ?: existing?.activeHours?.toString() ?: "") }
    var revenueFields by rememberSaveable { mutableStateOf(ArrayList(WorkPlatform.entries.mapIndexed { i, p -> cachedPlatformField(0, i) ?: existing?.entries?.find { it.platform == p }?.revenue?.toString() ?: "0" })) }
    var kmFields by rememberSaveable { mutableStateOf(ArrayList(WorkPlatform.entries.mapIndexed { i, p -> cachedPlatformField(1, i) ?: existing?.entries?.find { it.platform == p }?.km?.toString() ?: "0" })) }
    var hourFields by rememberSaveable { mutableStateOf(ArrayList(WorkPlatform.entries.mapIndexed { i, p -> cachedPlatformField(2, i) ?: existing?.entries?.find { it.platform == p }?.hours?.toString() ?: "0" })) }

    LaunchedEffect(km, hours, revenueFields, kmFields, hourFields) {
        viewModel.cacheDraft(date, listOf(km, hours) + revenueFields + kmFields + hourFields)
    }

    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val busy = operation.busy
    var message by remember { mutableStateOf<String?>(null) }
    var deletePrompt by remember { mutableStateOf(false) }
    val selectedPlatforms by repo.selectedPlatforms.collectAsStateWithLifecycle()
    val allEntries = WorkPlatform.entries.mapIndexed { i, p -> PlatformWork(p, inputNumber(revenueFields[i]) ?: -1.0, inputNumber(kmFields[i]) ?: -1.0, inputNumber(hourFields[i]) ?: -1.0) }
    val visiblePlatforms = platformsForDay(selectedPlatforms, allEntries)
    val entries = allEntries.filter { it.platform in visiblePlatforms }
    val draft = WorkDay(
        date, entries, inputNumber(km) ?: -1.0, inputNumber(hours) ?: -1.0,
        existing?.fixedCost ?: profile.dailyFixedCost, existing?.variablePerKm ?: profile.variablePerKm,
        existing?.grossGoal ?: profile.dailyGrossGoal, if (existing != null) existing.weeklyRent else profile.weeklyRent
    )

    AppText(
        "Totais reais do dia",
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    MoneyField("Km rodados no dia", km, { km = it; message = null }, maximum = 2000.0)
    MoneyField("Horas ativas no dia", hours, { hours = it; message = null }, maximum = 24.0)
    AppText("Inclua todos os deslocamentos e a espera. Se dois apps ficaram ligados por 3 horas juntos, o total é 3 horas, não 6.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())

    WorkPlatform.entries.forEachIndexed { i, platform ->
        if (platform !in visiblePlatforms) return@forEachIndexed
        NeobrutalCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surface,
            borderColor = MaterialTheme.colorScheme.outline,
            borderWidth = AppSpacing.BorderThick,
            shadowOffset = AppSpacing.ShadowOffset,
            shape = RoundedCornerShape(AppSpacing.CornerCard)
        ) {
            Column(Modifier.padding(AppSpacing.Content), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (platform !in selectedPlatforms) {
                    AppText("App desmarcado, com valores neste dia. O registro foi preservado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    PlatformIcon(platform)
                    AppText(platform.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                MoneyField("${platform.label} • recebido (R$)", revenueFields[i], {
                    revenueFields = ArrayList(revenueFields).apply { set(i, it) }; message = null
                })
                TextButton(
                    onClick = { expanded = if (expanded == platform.name) null else platform.name },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(
                            if (expanded == platform.name) com.girocerto.app.R.drawable.ic_expand_less
                            else com.girocerto.app.R.drawable.ic_expand_more
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    AppText(if (expanded == platform.name) "Ocultar detalhes" else "Km e horas em ${platform.label}", fontWeight = FontWeight.SemiBold)
                }
                if (expanded == platform.name) {
                    MoneyField("Km atribuídos ao app", kmFields[i], { kmFields = ArrayList(kmFields).apply { set(i, it) }; message = null })
                    MoneyField("Horas ativas no app", hourFields[i], { hourFields = ArrayList(hourFields).apply { set(i, it) }; message = null })
                    AppText("Opcional. Esses detalhes não entram nos km e nas horas do dia.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (draft.valid()) {
        NeobrutalCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surface,
            borderColor = MaterialTheme.colorScheme.outline,
            borderWidth = AppSpacing.BorderThick,
            shadowOffset = AppSpacing.ShadowOffset,
            shape = RoundedCornerShape(AppSpacing.CornerCard)
        ) {
            Column(Modifier.padding(AppSpacing.Content), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryLine("Recebido", Formatters.formatCurrency(draft.revenue))
                SummaryLine("Fixo rateado do dia", Formatters.formatCurrency(draft.fixedCost))
                SummaryLine("Custo dos km", Formatters.formatCurrency(draft.totalKm * draft.variablePerKm))
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                SummaryLine("Saldo estimado", Formatters.formatCurrency(draft.net))
                SummaryLine("Recebido por hora", draft.grossPerHour?.let { Formatters.formatPerHour(it) } ?: "Sem horas")
                SummaryLine("Recebido por km", draft.grossPerKm?.let { Formatters.formatPerKm(it) } ?: "Sem km")
            }
        }
    } else {
        AppText("Preencha valores válidos: até 24 horas e 2.000 km no dia. Use zero quando não houver movimento.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    AppText("O fixo é rateado pelos dias planejados. Trabalhar menos dias não reduz a cobrança semanal. Custos e meta ficam guardados com o dia ao salvar pela primeira vez.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    message?.let { AppText(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
    if (operation.date == date) operation.message?.let { AppText(it) }

    LaunchedEffect(operation) {
        if (operation.date == date && operation.deleted) {
            km = ""; hours = ""; revenueFields = ArrayList(List(WorkPlatform.entries.size) { "0" })
            kmFields = ArrayList(List(WorkPlatform.entries.size) { "0" }); hourFields = ArrayList(List(WorkPlatform.entries.size) { "0" })
            viewModel.acknowledgeDelete()
        }
    }

    NeobrutalButton(
        onClick = { viewModel.save(draft) },
        enabled = draft.valid() && !busy,
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(AppSpacing.CornerButton),
        text = if (busy) "Salvando…" else "Salvar fechamento",
        leadingIcon = {
            Icon(
                painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    )

    if (existing != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(
                onClick = { deletePrompt = true },
                enabled = !busy,
                modifier = Modifier.heightIn(min = AppSpacing.MinTouchTarget)
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_trash),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                AppText("Excluir este dia", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (deletePrompt) {
        AlertDialog(
            onDismissRequest = { deletePrompt = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                AppText(
                    "Excluir fechamento?",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                AppText("Os ganhos registrados em ${date.format(dayFormat)} serão removidos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.delete(date); deletePrompt = false },
                    enabled = !busy,
                    shape = RoundedCornerShape(AppSpacing.CornerButton)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_trash),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(6.dp))
                    AppText("Excluir", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletePrompt = false },
                    enabled = !busy,
                    shape = RoundedCornerShape(AppSpacing.CornerButton)
                ) {
                    AppText("Cancelar", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

@Composable
private fun WorkPlatformPicker(repo: WorkLedgerRepository) {
    val selected by repo.selectedPlatforms.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var choices by remember { mutableStateOf(selected) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    NeobrutalButton(
        onClick = { choices = selected; error = null; open = true },
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = MaterialTheme.colorScheme.outline,
        borderWidth = AppSpacing.BorderAction,
        shape = RoundedCornerShape(AppSpacing.CornerButton),
        text = "Meus apps de trabalho • alterar",
        leadingIcon = { NavigationGlyph(Screen.SETTINGS, true) }
    )
    AppText(
        WorkPlatform.entries.filter { it in selected }.joinToString { it.label },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (open) {
        AlertDialog(
            onDismissRequest = { if (!saving) open = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                AppText(
                    "Em quais apps você trabalha?",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AppText("Escolha pelo menos um. Os ganhos já registrados serão preservados. Esta seleção filtra o diário; a leitura de ofertas é configurada no Copiloto.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    val selectablePlatforms = listOf(WorkPlatform.UBER, WorkPlatform.NINETY_NINE, WorkPlatform.IFOOD, WorkPlatform.KEETA)
                    selectablePlatforms.forEach { platform ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = AppSpacing.MinTouchTarget)
                                .toggleable(
                                    value = platform in choices,
                                    enabled = !saving,
                                    role = Role.Checkbox,
                                    onValueChange = { choices = if (it) choices + platform else choices - platform }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = platform in choices, enabled = !saving, onCheckedChange = null)
                            PlatformIcon(platform)
                            Spacer(Modifier.width(12.dp))
                            AppText(platform.label, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    error?.let { AppText(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = choices.isNotEmpty() && !saving,
                    onClick = {
                        scope.launch {
                            saving = true
                            try { repo.saveSelectedPlatforms(choices); open = false }
                            catch (_: Exception) { error = "Não foi possível salvar. Tente novamente." }
                            finally { saving = false }
                        }
                    },
                    shape = RoundedCornerShape(AppSpacing.CornerButton)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    AppText(if (saving) "Salvando…" else "Salvar apps", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !saving,
                    onClick = { open = false },
                    shape = RoundedCornerShape(AppSpacing.CornerButton)
                ) {
                    AppText("Cancelar", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

@Composable
private fun WeeklySummary(date: LocalDate, days: List<WorkDay>, profile: ElectricProfile) {
    val closing = remember(date, days, profile.weeklyRent, profile.weeklyFixedExpenses) {
        com.girocerto.domain.calculator.WeeklyLedger.calculate(
            date, days, profile.weeklyRent, profile.weeklyFixedExpenses
        )
    }
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
            AppText(
                "Semana de ${closing.start.format(dayFormat)}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            SummaryLine("Recebido registrado", Formatters.formatCurrency(closing.revenue))
            SummaryLine("Contrato semanal", Formatters.formatCurrency(closing.contract))
            if (closing.fixedExpenses > 0) {
                SummaryLine("Gastos fixos do veículo", Formatters.formatCurrency(closing.fixedExpenses))
            }
            SummaryLine("Custos dos km", Formatters.formatCurrency(closing.variableCost))
            SummaryLine("Saldo da semana", Formatters.formatCurrency(closing.net))
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            AppText("Segunda a domingo. O contrato é descontado uma única vez, mesmo trabalhando menos ou mais dias. ${closing.registeredDays} dias registrados.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (closing.usedCurrentContract) {
                AppText("Estimativa com o contrato atual: esta semana ainda não tem contrato histórico registrado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
