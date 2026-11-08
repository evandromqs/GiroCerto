package com.girocerto.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
    var showingSplash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1_250); showingSplash = false }
    if (showingSplash) {
        SplashLoadingScreen()
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
        auth.user == null -> LoginScreen(
            loading = auth.loading,
            error = auth.error,
            onGoogle = { app.auth.startGoogleLogin(context) },
            onSignIn = app.auth::signIn,
            onSignUp = app.auth::signUp,
            onClearError = app.auth::clearError,
        )
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
private fun LoginScreen(
    loading: Boolean,
    error: String?,
    onGoogle: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onClearError: () -> Unit,
) {
    var createAccount by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val valid = email.contains('@') && email.substringAfter('@').contains('.') && password.length >= 8 && (!createAccount || name.trim().length >= 2)
    Box(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Image(painterResource(R.drawable.ic_girocerto_brand), "Giro Certo", Modifier.size(96.dp))
            Text("Giro Certo", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text(
                "Seu copiloto para escolher melhores corridas e acompanhar seus ganhos.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Veja se a oferta compensa • Controle custos e metas • Trabalhe com mais segurança",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Button(onClick = onGoogle, enabled = !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Image(painterResource(R.drawable.ic_google_g), null, Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(if (loading) "Abrindo Google…" else "Continuar com Google", fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text("ou entre com e-mail", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(Modifier.weight(1f))
            }
            Text(if (createAccount) "Criar uma conta" else "Entrar com e-mail", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (createAccount) OutlinedTextField(
                value = name, onValueChange = { name = it.take(60); onClearError() },
                label = { Text("Nome") }, singleLine = true, enabled = !loading, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email, onValueChange = { email = it.take(160); onClearError() },
                label = { Text("E-mail") }, singleLine = true, enabled = !loading,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it.take(128); onClearError() },
                label = { Text("Senha") }, singleLine = true, enabled = !loading,
                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { passwordVisible = !passwordVisible }) { Text(if (passwordVisible) "Ocultar" else "Mostrar") } },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                supportingText = { Text("Mínimo de 8 caracteres") }, modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { if (createAccount) onSignUp(name, email, password) else onSignIn(email, password) },
                enabled = !loading && valid, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
            ) { Text(if (loading) "Aguarde…" else if (createAccount) "Criar conta" else "Entrar", fontWeight = FontWeight.Bold) }
            TextButton(onClick = { createAccount = !createAccount; onClearError() }, enabled = !loading) {
                Text(if (createAccount) "Já tenho uma conta" else "Ainda não tenho conta")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
            Text(
                "Depois, você poderá informar veículo, custos e metas para receber análises personalizadas.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
    }
}

@Composable
private fun SplashLoadingScreen() {
    val transition = rememberInfiniteTransition(label = "splash_dots")
    val phases = List(3) { index ->
        transition.animateFloat(
            initialValue = .35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(520, delayMillis = index * 130, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dot_$index",
        )
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Image(painterResource(R.drawable.ic_girocerto_brand), "Giro Certo", Modifier.size(132.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                phases.forEach { alpha ->
                    Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha.value), CircleShape))
                }
            }
            Text("Preparando seu copiloto…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun LoadingScreen() = SplashLoadingScreen()

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
