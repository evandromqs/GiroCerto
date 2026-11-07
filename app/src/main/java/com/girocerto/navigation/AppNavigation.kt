package com.girocerto.navigation

import com.girocerto.ui.components.AppText as Text

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.animation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlin.math.abs

import android.content.Context
import com.girocerto.ui.copilot.CopilotScreen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.girocerto.ui.components.NavigationGlyph
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.girocerto.app.theme.BackgroundDark
import com.girocerto.app.theme.BorderDark
import com.girocerto.app.theme.PrimaryBlue
import com.girocerto.app.theme.SurfaceDark
import com.girocerto.app.theme.TextPrimary
import com.girocerto.app.theme.TextSecondary
import com.girocerto.domain.model.RideAnalysis
import com.girocerto.overlay.OverlayController
import com.girocerto.ui.history.HistoryScreen

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val app = context.applicationContext as com.girocerto.app.GiroCertoApp
    var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var navigationHistory by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var todayRequest by rememberSaveable { mutableStateOf(0) }
    var showCopilot by rememberSaveable { mutableStateOf(false) }
    fun navigate(screen: Screen) {
        if (screen == Screen.COPILOT) { showCopilot = true; return }
        if (currentScreen != screen) { navigationHistory = ArrayList(navigationHistory + currentScreen.name); currentScreen = screen }
    }
    var pendingOverlayAnalysis by remember { mutableStateOf<RideAnalysis?>(null) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    fun handleShowOverlay(analysis: RideAnalysis) {
        if (OverlayController.canDrawOverlays(context)) {
            OverlayController.showOverlay(context, analysis)
        } else {
            pendingOverlayAnalysis = analysis
            showPermissionRationale = true
        }
    }

    var showShiftDialog by remember { mutableStateOf(false) }
    val requestedTarget by com.girocerto.app.MainActivity.targetScreen.collectAsStateWithLifecycle()
    LaunchedEffect(requestedTarget) {
        when (requestedTarget) {
            "SHIFT", "PONTO" -> {
                showShiftDialog = true
                com.girocerto.app.MainActivity.targetScreen.value = null
            }
            "HISTORY" -> {
                navigate(Screen.HISTORY)
                com.girocerto.app.MainActivity.targetScreen.value = null
            }
            "DIARY" -> {
                navigate(Screen.DIARY)
                com.girocerto.app.MainActivity.targetScreen.value = null
            }
            "COPILOT" -> {
                showCopilot = true
                com.girocerto.app.MainActivity.targetScreen.value = null
            }
        }
    }

    val stateHolder = rememberSaveableStateHolder()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, pendingOverlayAnalysis) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && OverlayController.canDrawOverlays(context)) {
                pendingOverlayAnalysis?.let { OverlayController.showOverlay(context, it) }
                pendingOverlayAnalysis = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun goBack() {
        currentScreen = navigationHistory.lastOrNull()?.let { Screen.valueOf(it) } ?: Screen.HOME
        navigationHistory = ArrayList(navigationHistory.dropLast(1))
    }
    BackHandler(currentScreen != Screen.HOME) { goBack() }
    val rootTabs = listOf(Screen.HOME, Screen.DIARY, Screen.GOALS, Screen.SETTINGS)
    var swipeDistance by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    Scaffold(
        bottomBar = {
            if (currentScreen in rootTabs) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        tonalElevation = 6.dp,
                        shadowElevation = 14.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(66.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            rootTabs.forEach { screen ->
                                val selected = currentScreen == screen ||
                                    (screen == Screen.SETTINGS && currentScreen == Screen.ADVANCED)
                                val tint by animateColorAsState(
                                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    animationSpec = tween(180),
                                    label = "tab_tint"
                                )
                                val container by animateColorAsState(
                                    targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent,
                                    animationSpec = tween(180),
                                    label = "tab_container"
                                )

                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(container)
                                        .selectable(
                                            selected = selected,
                                            role = Role.Tab,
                                            onClick = {
                                                navigationHistory = arrayListOf()
                                                currentScreen = screen
                                            }
                                        )
                                        .padding(horizontal = 2.dp)
                                        .animateContentSize(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        ),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(
                                            when (screen) {
                                                Screen.HOME -> com.girocerto.app.R.drawable.ic_home
                                                Screen.DIARY -> com.girocerto.app.R.drawable.ic_diary
                                                Screen.GOALS -> com.girocerto.app.R.drawable.ic_goals
                                                Screen.SETTINGS -> com.girocerto.app.R.drawable.ic_settings
                                                else -> com.girocerto.app.R.drawable.ic_home
                                            }
                                        ),
                                        contentDescription = screen.title,
                                        modifier = Modifier.size(20.dp),
                                        tint = tint
                                    )
                                    AnimatedVisibility(
                                        visible = selected,
                                        enter = expandHorizontally(
                                            expandFrom = Alignment.Start,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        ) + fadeIn(animationSpec = tween(160)),
                                        exit = shrinkHorizontally(
                                            shrinkTowards = Alignment.Start,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        ) + fadeOut(animationSpec = tween(120))
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Spacer(Modifier.width(2.dp))
                                            Text(
                                                text = screen.title,
                                                color = tint,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(innerPadding)
        ) {
            if (currentScreen !in rootTabs) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { goBack() },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            painter = painterResource(com.girocerto.app.R.drawable.ic_chevron_left),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Voltar")
                    }
                }
            }
            AnimatedContent(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(currentScreen) {
                        if (currentScreen in rootTabs) {
                            detectHorizontalDragGestures(
                                onDragStart = { swipeDistance = 0f },
                                onDragCancel = { swipeDistance = 0f },
                                onDragEnd = {
                                    val index = rootTabs.indexOf(currentScreen)
                                    if (abs(swipeDistance) > 90f) {
                                        val next = if (swipeDistance < 0) index + 1 else index - 1
                                        rootTabs.getOrNull(next)?.let {
                                            navigationHistory = arrayListOf()
                                            currentScreen = it
                                        }
                                    }
                                    swipeDistance = 0f
                                },
                            ) { change, amount ->
                                change.consume()
                                swipeDistance += amount
                            }
                        }
                    },
                targetState = currentScreen,
                label = "Troca de tela",
                transitionSpec = {
                    val forward = targetState.position >= initialState.position
                    val direction = if (forward) 1 else -1
                    (fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(
                            animationSpec = tween(330, easing = FastOutSlowInEasing)
                        ) { fullWidth -> fullWidth * direction }) togetherWith
                        (fadeOut(animationSpec = tween(170, easing = FastOutSlowInEasing)) +
                        slideOutHorizontally(
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) { fullWidth -> -fullWidth * direction / 3 })
                }
            ) { screen ->
            stateHolder.SaveableStateProvider(screen.name) {
            when (screen) {
                Screen.HOME -> com.girocerto.ui.home.WorkDashboard(
                    workRepository = app.workRepository,
                    shiftRepository = app.shiftRepository,
                    onRecord = { todayRequest++; navigate(Screen.DIARY) },
                    onGoals = { navigate(Screen.GOALS) },
                    onCopilot = { navigate(Screen.COPILOT) },
                    onOffers = { navigate(Screen.HISTORY) },
                    onShift = { showShiftDialog = true }
                )
                Screen.DIARY -> com.girocerto.ui.home.WorkDiaryScreen(
                    workRepository = app.workRepository,
                    openTodayRequest = todayRequest,
                    onProfile = { navigate(Screen.GOALS) }
                )
                Screen.HISTORY -> HistoryScreen(
                    onShowOverlay = { handleShowOverlay(it) },
                    onNavigateToCopilot = { navigate(Screen.COPILOT) }
                )
                Screen.PROFILE -> com.girocerto.ui.goals.ElectricProfileScreen(onDone = { todayRequest++; navigate(Screen.DIARY) })
                Screen.SETTINGS -> com.girocerto.ui.settings.SimpleSettingsScreen(
                    onCopilot = { navigate(Screen.COPILOT) },
                    onProfile = { navigate(Screen.GOALS) },
                    onAdvanced = { navigate(Screen.ADVANCED) })
                Screen.ADVANCED -> com.girocerto.ui.settings.CardAndVoiceScreen()
                Screen.GOALS -> com.girocerto.ui.goals.ElectricProfileScreen(onDone = { todayRequest++; navigate(Screen.DIARY) })
                Screen.COPILOT -> Unit
            }
            }
            }
        }
    }

    // Ajustes do copiloto em modal flutuante, sem trocar de tela
    if (showCopilot) {
        com.girocerto.ui.copilot.CopilotModal(onDismiss = { showCopilot = false })
    }

    // Modal educativo e transparente para solicitar permissão de sobreposição
    if (showShiftDialog) {
        com.girocerto.ui.home.ShiftDialog(
            shiftRepository = app.shiftRepository,
            workRepository = app.workRepository,
            onDismiss = { showShiftDialog = false }
        )
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false; pendingOverlayAnalysis = null },
            containerColor = SurfaceDark,
            title = {
                Text(text = "Permissão da Janela Flutuante", color = TextPrimary)
            },
            text = {
                Text(
                    text = "Para exibir o semáforo e os indicadores em miniatura por cima dos aplicativos de corrida enquanto você dirige, o Android exige autorização para 'Sobrepor a outros apps'.\n\nO Giro Certo não interfere nem clica em outros aplicativos.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionRationale = false
                        val intent = OverlayController.createOverlayPermissionIntent(context)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = PrimaryBlue)
                ) {
                    Text("Autorizar nas Configurações", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionRationale = false; pendingOverlayAnalysis = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) {
                    Text("Agora Não")
                }
            }
        )
    }
}

private val Screen.position: Int
    get() = when (this) {
        Screen.HOME -> 0
        Screen.DIARY -> 1
        Screen.GOALS -> 2
        Screen.SETTINGS -> 3
        else -> 4
    }
