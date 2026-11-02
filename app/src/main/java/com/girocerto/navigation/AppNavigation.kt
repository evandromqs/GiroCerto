package com.girocerto.navigation

import com.girocerto.ui.components.AppText as Text

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha

import android.content.Context
import com.girocerto.ui.goals.GoalsScreen
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
import com.girocerto.ui.home.HomeScreen
import com.girocerto.ui.profile.ProfileScreen
import com.girocerto.ui.settings.SettingsScreen

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var navigationHistory by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var todayRequest by rememberSaveable { mutableStateOf(0) }
    fun navigate(screen: Screen) {
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
                navigate(Screen.COPILOT)
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
    Scaffold(
        bottomBar = {
            if (currentScreen in rootTabs) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        tonalElevation = 6.dp,
                        shadowElevation = 14.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rootTabs.forEach { screen ->
                                val selected = currentScreen == screen || (screen == Screen.SETTINGS && currentScreen == Screen.ADVANCED)
                                val tint by animateColorAsState(
                                    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    label = "tab_tint"
                                )

                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            navigationHistory = arrayListOf()
                                            currentScreen = screen
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.text.BasicText(
                                        text = screen.title,
                                        modifier = Modifier.size(1.dp).alpha(0f)
                                    )
                                    if (selected) {
                                        androidx.compose.foundation.layout.Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.85f)
                                                .height(42.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
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
                                            modifier = Modifier.size(24.dp),
                                            tint = tint
                                        )

                                        AnimatedVisibility(
                                            visible = selected,
                                            enter = fadeIn(tween(150)) + expandHorizontally(tween(150)),
                                            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(100))
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Spacer(Modifier.width(4.dp))
                                                androidx.compose.foundation.layout.Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Divisor vertical sutil antes do ícone de atalho
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .height(22.dp)
                                    .width(1.dp)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            )

                            // Ícone à direita no menu do rodapé: Copiloto
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable { navigate(Screen.COPILOT) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(com.girocerto.app.R.drawable.ic_copilot),
                                    contentDescription = "Copiloto",
                                    modifier = Modifier.size(22.dp),
                                    tint = if (currentScreen == Screen.COPILOT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TextButton(onClick = { goBack() }) { Text("Voltar") }
                    Text(
                        when (currentScreen) {
                            Screen.HISTORY -> "Ofertas analisadas"
                            Screen.ADVANCED -> "Cartão e voz"
                            else -> currentScreen.title
                        },
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(72.dp))
                }
            }
            AnimatedContent(
                modifier = Modifier.weight(1f),
                targetState = currentScreen,
                label = "Troca de tela",
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(animationSpec = tween(260, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth / 4 }) togetherWith
                        (fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                        slideOutHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth / 6 })
                }
            ) { screen ->
            stateHolder.SaveableStateProvider(screen.name) {
            when (screen) {
                Screen.HOME -> com.girocerto.ui.home.WorkDashboard(
                    onRecord = { todayRequest++; navigate(Screen.DIARY) },
                    onGoals = { navigate(Screen.GOALS) },
                    onCopilot = { navigate(Screen.COPILOT) },
                    onOffers = { navigate(Screen.HISTORY) },
                    onShift = { showShiftDialog = true }
                )
                Screen.DIARY -> com.girocerto.ui.home.WorkDiaryScreen(openTodayRequest = todayRequest, onProfile = { navigate(Screen.GOALS) })
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
                Screen.COPILOT -> CopilotScreen()
            }
            }
            }
        }
    }

    // Modal educativo e transparente para solicitar permissão de sobreposição
    if (showShiftDialog) {
        com.girocerto.ui.home.ShiftDialog(onDismiss = { showShiftDialog = false })
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



