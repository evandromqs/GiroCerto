package com.girocerto.navigation

import com.girocerto.ui.components.AppText as Text

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import android.content.Context
import com.girocerto.ui.goals.GoalsScreen
import com.girocerto.ui.copilot.CopilotScreen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

    BackHandler(currentScreen != Screen.HOME) {
        currentScreen = navigationHistory.lastOrNull()?.let { Screen.valueOf(it) } ?: Screen.HOME
        navigationHistory = ArrayList(navigationHistory.dropLast(1))
    }
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = TextPrimary
            ) {
                listOf(Screen.HOME, Screen.DIARY, Screen.GOALS, Screen.SETTINGS).forEach { screen ->
                    val selected = currentScreen == screen || (screen == Screen.SETTINGS && currentScreen in listOf(Screen.COPILOT, Screen.ADVANCED, Screen.PROFILE))


                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigationHistory = arrayListOf(); currentScreen = screen },
                        icon = { NavigationGlyph(screen, selected) },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) PrimaryBlue else TextSecondary
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = PrimaryBlue.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .padding(innerPadding)
        ) {
            AnimatedContent(targetState = currentScreen, label = "Troca de tela",
                transitionSpec = { (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 16 }) togetherWith fadeOut(tween(120)) }) { screen ->
            stateHolder.SaveableStateProvider(screen.name) {
            when (screen) {
                Screen.HOME -> com.girocerto.ui.home.WorkDashboard(
                    onRecord = { todayRequest++; navigate(Screen.DIARY) },
                    onGoals = { navigate(Screen.GOALS) },
                    onCopilot = { navigate(Screen.COPILOT) },
                    onOffers = { navigate(Screen.HISTORY) }
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



