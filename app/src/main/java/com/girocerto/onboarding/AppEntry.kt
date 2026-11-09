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
import androidx.compose.ui.draw.clip
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
        !tutorialDone || !permissionsDone -> UnifiedOnboardingSpotlightScreen(
            onCompleted = {
                onboardingPrefs.edit()
                    .putBoolean("tutorial_done", true)
                    .putBoolean("permissions_done", true)
                    .apply()
                tutorialDone = true
                permissionsDone = true
            }
        )
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
private fun UnifiedOnboardingSpotlightScreen(onCompleted: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val overlay = remember(refresh) { OverlayController.canDrawOverlays(context) }
    val accessibility = remember(refresh) { GiroCertoAccessibilityService.isAccessibilityEnabled(context) }
    var currentStep by remember { mutableIntStateOf(0) }

    val steps = listOf(
        SpotlightStep(
            tag = "CÁLCULO EM TEMPO REAL",
            title = "Avaliação Automática",
            description = "O Giro Certo analisa valores, distância e tempo de cada oferta da Uber, 99 e iFood na hora, calculando seu lucro líquido real.",
            iconRes = R.drawable.ic_copilot
        ),
        SpotlightStep(
            tag = "DECISÃO VISUAL SEM TEXTO",
            title = "Semáforo Inteligente",
            description = "Identifique em 1 segundo no canto da tela se a corrida é Ótima, Boa ou Ruim usando os ícones oficiais e cores de alto contraste.",
            iconRes = R.drawable.ic_goals
        ),
        SpotlightStep(
            tag = "COMANDOS DE VOZ",
            title = "Controle com as Mãos no Volante",
            description = "Diga 'aceitar corrida', 'recusar', 'quanto fiz hoje' ou 'bater ponto' para pilotar sem tirar a atenção do trânsito.",
            iconRes = R.drawable.ic_trip_clock
        ),
        SpotlightStep(
            tag = "CONFIGURAÇÃO FINAL",
            title = "Permissões Essenciais",
            description = "Ative a sobreposição e a acessibilidade para o copiloto monitorar as corridas sobre os aplicativos de motorista.",
            iconRes = R.drawable.ic_advanced
        )
    )

    val step = steps[currentStep]

    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++
        }
        val owner = context as? androidx.lifecycle.LifecycleOwner
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PASSO ${currentStep + 1} DE ${steps.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (currentStep < steps.lastIndex) {
                        TextButton(onClick = { currentStep = steps.lastIndex }) {
                            Text("Pular", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Progress Bar
                LinearProgressIndicator(
                    progress = { (currentStep + 1) / steps.size.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))

            // Spotlight Card (Clean, sem fundo opaco/escuro)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Spotlight Badge Icon
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(step.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Step Tag Pill
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = step.tag,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Step 4 Permissions integration inside the spotlight card
                    if (currentStep == steps.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PermissionItemRow(
                                title = "Sobrepor a outros aplicativos",
                                subtitle = "Exibe o card de oferta na hora que toca a chamada.",
                                granted = overlay,
                                onAction = {
                                    context.startActivity(OverlayController.createOverlayPermissionIntent(context))
                                    refresh++
                                }
                            )

                            PermissionItemRow(
                                title = "Acessibilidade do Giro Certo",
                                subtitle = "Lê dados da oferta sem interferir no app de corrida.",
                                granted = accessibility,
                                onAction = {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    refresh++
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentStep > 0) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Voltar", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        if (currentStep < steps.lastIndex) {
                            currentStep++
                        } else {
                            onCompleted()
                        }
                    },
                    modifier = Modifier.weight(if (currentStep > 0) 1.5f else 1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = currentStep < steps.lastIndex || (overlay && accessibility),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (currentStep < steps.lastIndex) "Avançar" else "Concluir e Começar",
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

private data class SpotlightStep(
    val tag: String,
    val title: String,
    val description: String,
    val iconRes: Int
)

@Composable
private fun PermissionItemRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary,
                    contentColor = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(if (granted) "OK" else "Ativar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TutorialScreen(onDone: () -> Unit) = UnifiedOnboardingSpotlightScreen(onDone)

@Composable
private fun PermissionGuideScreen(onDone: () -> Unit) = UnifiedOnboardingSpotlightScreen(onDone)

@Composable
private fun PermissionCard(title: String, description: String, granted: Boolean, onClick: () -> Unit) = Unit

