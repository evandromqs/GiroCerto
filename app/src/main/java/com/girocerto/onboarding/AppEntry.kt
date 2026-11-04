package com.girocerto.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.girocerto.app.GiroCertoApp
import com.girocerto.app.R
import com.girocerto.monitoring.GiroCertoAccessibilityService
import com.girocerto.navigation.AppNavigation
import com.girocerto.overlay.OverlayController
import com.girocerto.ui.components.AppText as Text

@Composable
fun AppEntry() {
    if (com.girocerto.app.RuntimeMode.bypassOnboarding) {
        AppNavigation()
        return
    }
    val app = GiroCertoApp.instance
    val auth by app.auth.state.collectAsStateWithLifecycle()
    val profile by app.workRepository.profile.collectAsStateWithLifecycle()
    val loading by app.workRepository.loading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val onboardingPrefs = remember { context.getSharedPreferences("onboarding_v1", Context.MODE_PRIVATE) }
    var profileOwner by remember(auth.user?.id) { mutableStateOf(onboardingPrefs.getString("profile_owner", null)) }
    var tutorialDone by remember { mutableStateOf(onboardingPrefs.getBoolean("tutorial_done", false)) }
    var permissionsDone by remember { mutableStateOf(onboardingPrefs.getBoolean("permissions_done", false)) }

    when {
        auth.user == null -> LoginScreen(auth.loading, auth.error) { app.auth.startGoogleLogin(context) }
        loading -> LoadingScreen()
        profile == null || profileOwner != auth.user?.id -> com.girocerto.ui.goals.ElectricProfileScreen(onDone = {
            val owner = auth.user?.id ?: return@ElectricProfileScreen
            onboardingPrefs.edit().putString("profile_owner", owner).apply()
            profileOwner = owner
        })
        !tutorialDone -> TutorialScreen {
            onboardingPrefs.edit().putBoolean("tutorial_done", true).apply(); tutorialDone = true
        }
        !permissionsDone -> PermissionGuideScreen {
            onboardingPrefs.edit().putBoolean("permissions_done", true).apply(); permissionsDone = true
        }
        else -> {
            AppNavigation()
            com.girocerto.update.AppUpdatePrompt()
        }
    }
}

@Composable
private fun LoginScreen(loading: Boolean, error: String?, onLogin: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Image(painterResource(R.drawable.ic_girocerto_brand), null, Modifier.size(112.dp))
            Text("Giro Certo", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text("Seus ganhos, custos e ofertas protegidos em um só lugar.", textAlign = TextAlign.Center)
            Button(onClick = onLogin, enabled = !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                Text(if (loading) "Abrindo Google…" else "Continuar com Google", fontWeight = FontWeight.Bold)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
            Text("Ao continuar, sua conta identifica com segurança o backup em nuvem.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable private fun LoadingScreen() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

@Composable
private fun TutorialScreen(onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val pages = listOf(
        "Entenda cada oferta" to "O Giro Certo calcula ganho por km, ganho por hora, custos e lucro antes de você decidir.",
        "Semáforo simples" to "Verde indica uma oferta saudável, amarelo pede atenção e vermelho sinaliza possível prejuízo.",
        "Tudo continua offline" to "A leitura e os cálculos acontecem no celular. A nuvem guarda apenas seu backup protegido.",
    )
    val item = pages[page]
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${page + 1} de ${pages.size}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp)); Text(item.first, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp)); Text(item.second, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(onClick = { if (page < pages.lastIndex) page++ else onDone() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (page < pages.lastIndex) "Próximo" else "Configurar permissões")
        }
    }
}

@Composable
private fun PermissionGuideScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val overlay = remember(refresh) { OverlayController.canDrawOverlays(context) }
    val accessibility = remember(refresh) { GiroCertoAccessibilityService.isAccessibilityEnabled(context) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Permissões do Copiloto", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text("Ative as duas permissões para detectar popups de oferta mesmo enquanto Maps, música ou outro aplicativo estiver na frente.")
        PermissionCard("Aparecer sobre outros apps", "Mostra o semáforo e os cálculos por cima do app aberto.", overlay) {
            context.startActivity(OverlayController.createOverlayPermissionIntent(context)); refresh++
        }
        PermissionCard("Acessibilidade", "Lê somente os textos das ofertas e notificações dos apps habilitados.", accessibility) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); refresh++
        }
        Text("No Android, procure Giro Certo e confirme o uso do serviço. Você pode desativar essas permissões a qualquer momento.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth(), enabled = overlay && accessibility) { Text("Entrar no Giro Certo") }
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }, modifier = Modifier.fillMaxWidth()) { Text("Abrir informações do aplicativo") }
    }
    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++ }
        val owner = context as? androidx.lifecycle.LifecycleOwner
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
}

@Composable
private fun PermissionCard(title: String, description: String, granted: Boolean, onClick: () -> Unit) {
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onClick) { Text(if (granted) "Ativada" else "Ativar") }
    } }
}
