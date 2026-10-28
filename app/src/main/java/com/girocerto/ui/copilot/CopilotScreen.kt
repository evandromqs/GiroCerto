package com.girocerto.ui.copilot

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.girocerto.ui.components.TactileButton as Button

import com.girocerto.ui.components.AppText as Text

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.girocerto.ui.components.TactileCard
import com.girocerto.app.theme.TextSecondary
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.girocerto.monitoring.CopilotPreferences
import com.girocerto.monitoring.CopilotPolicy
import com.girocerto.overlay.OverlayController

@Composable
fun CopilotScreen() {
    val context = LocalContext.current
    val repository = remember(context) { CopilotPreferences(context) }
    var policy by remember { mutableStateOf(repository.read()) }
    val connected by com.girocerto.monitoring.GiroCertoAccessibilityService.isServiceRunning.collectAsStateWithLifecycle()
    val hidden by OverlayController.isHidden.collectAsStateWithLifecycle()
    val floating by OverlayController.isOverlayActive.collectAsStateWithLifecycle()
    val reader by com.girocerto.monitoring.GiroCertoAccessibilityService.readerStatus.collectAsStateWithLifecycle()
    val observation by com.girocerto.monitoring.GiroCertoAccessibilityService.readerObservation.collectAsStateWithLifecycle()
    var overlayAllowed by remember { mutableStateOf(OverlayController.canDrawOverlays(context)) }
    var readingAllowed by remember { mutableStateOf(com.girocerto.monitoring.GiroCertoAccessibilityService.isAccessibilityEnabled(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                policy = repository.read()
                overlayAllowed = OverlayController.canDrawOverlays(context)
                readingAllowed = com.girocerto.monitoring.GiroCertoAccessibilityService.isAccessibilityEnabled(context)
                OverlayController.syncCopilot(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showMore by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showReadingDisclosure by remember { mutableStateOf(false) }
    fun update(value: CopilotPolicy) {
        repository.write(value)
        policy = value
        if (!value.enabled) OverlayController.hideOverlay(context)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        update(policy.copy(notifyOffers = granted))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(com.girocerto.app.theme.AppSpacing.Screen), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.Start) {
        Text("Seu copiloto", style = MaterialTheme.typography.headlineMedium)
        Text("Escolha o que acompanhar e ouvir. Você decide sobre cada oferta.", color = TextSecondary, modifier = Modifier.fillMaxWidth())
        Text(when {
            !policy.enabled -> "Copiloto pausado"
            !connected && !readingAllowed -> "Falta autorizar a leitura de ofertas"
            hidden -> "Janela flutuante ocultada por você"
            !overlayAllowed -> "Falta autorizar o cartão flutuante"
            !floating -> "Leitura conectada. O botão flutuante ainda não apareceu."
            else -> "Copiloto ativo. O botão flutuante está visível."
        }, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { showReadingDisclosure = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (readingAllowed || connected) "Leitura de ofertas • permitida" else "Autorizar leitura de ofertas")
        }
        if (showReadingDisclosure) AlertDialog(
            onDismissRequest = { showReadingDisclosure = false },
            title = { Text("Leitura de ofertas na tela") },
            text = { Text("Ao ativar o serviço de acessibilidade, o Giro Certo lê textos das janelas dos apps de trabalho selecionados: valores, distâncias, tempos e, quando disponíveis, endereços e avaliações. O processamento acontece no aparelho. As análises só são gravadas quando você salva ou ativa o salvamento automático. OCR e comprovantes de tela são opções separadas, desligadas por padrão. O app não aceita corridas nem executa gestos por você. Você pode desativar o serviço nas configurações do Android. Metas e diário funcionam sem essa permissão.") },
            confirmButton = { TextButton(onClick = {
                showReadingDisclosure = false
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) { Text("Entendi, abrir configurações") } },
            dismissButton = { TextButton(onClick = { showReadingDisclosure = false }) { Text("Agora não") } }
        )
        OutlinedButton(onClick = { context.startActivity(OverlayController.createOverlayPermissionIntent(context)) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (overlayAllowed) "Cartão flutuante • permitido" else "Autorizar cartão flutuante")
        }
        if (hidden) Button(onClick = { OverlayController.restore(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Mostrar botão flutuante")
        }
        Text("Arraste a bolha até a lixeira para ocultar a janela. A leitura continua.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
        Preference("Copiloto ativo", policy.enabled) { update(policy.copy(enabled = it)) }
        Text("Aplicativos acompanhados", style = MaterialTheme.typography.titleMedium)
        Preference("Uber e Uber Eats", policy.uber, com.girocerto.domain.model.WorkPlatform.UBER) { update(policy.copy(uber = it)) }
        Preference("99 e 99Food", policy.ninetyNine, com.girocerto.domain.model.WorkPlatform.NINETY_NINE) { update(policy.copy(ninetyNine = it)) }
        Preference("inDrive", policy.inDrive) { update(policy.copy(inDrive = it)) }
        Preference("iFood e Keeta", policy.otherApps) { update(policy.copy(otherApps = it)) }
        TextButton(onClick = { showMore = !showMore }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showMore) "Ocultar mais opções" else "Mais opções")
        }
        if (showMore) {
            Text("Alertas por classificação", style = MaterialTheme.typography.titleMedium)
            Text("A voz geral continua em Cartão e voz.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
            Preference("Ouvir ofertas verdes", policy.speakGreen) { update(policy.copy(speakGreen = it)) }
            Preference("Ouvir ofertas amarelas", policy.speakYellow) { update(policy.copy(speakYellow = it)) }
            Preference("Ouvir ofertas vermelhas", policy.speakRed) { update(policy.copy(speakRed = it)) }
            Preference("Notificar novas análises", policy.notifyOffers) { enabled ->
                if (enabled && android.os.Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else update(policy.copy(notifyOffers = enabled))
            }
            Text("Nota do passageiro", style = MaterialTheme.typography.titleMedium)
            Text(if (policy.minimumPassengerRating == 0.0) "Aviso desligado" else "Avisar abaixo de %.1f".format(policy.minimumPassengerRating), modifier = Modifier.fillMaxWidth())
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.0, 4.0, 4.5, 4.8, 5.0, policy.minimumPassengerRating).distinct().sorted().forEach { rating ->
                    FilterChip(selected = policy.minimumPassengerRating == rating, onClick = {
                        update(policy.copy(minimumPassengerRating = rating))
                    }, label = { Text(if (rating == 0.0) "Desligado" else "%.1f".format(rating)) })
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                Preference("Leitura local de imagem (OCR)", policy.ocrEnabled) { update(policy.copy(ocrEnabled = it)) }
                Preference("Salvar comprovante visual", policy.captureOfferScreenshot) { update(policy.copy(captureOfferScreenshot = it)) }
            } else Text("OCR e comprovantes exigem Android 11 ou mais recente.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
            Preference("Salvar análises automaticamente", policy.autoSave) { update(policy.copy(autoSave = it)) }
            TextButton(onClick = { showDetails = true }) { Text("Saiba mais") }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(com.girocerto.app.theme.AppSpacing.Content), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Diagnóstico da leitura", style = MaterialTheme.typography.titleMedium)
                    Text(observation, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
                    Text(reader, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = {
                        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        val details = "Giro Certo $version • Android ${android.os.Build.VERSION.RELEASE}\n" +
                            "Leitor conectado: $connected • Copiloto ativo: ${policy.enabled} • OCR: ${policy.ocrEnabled} • Janela: $floating\n" +
                            "$observation\n$reader\n" + com.girocerto.monitoring.ReaderDiagnostics.lastFailure
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Diagnóstico Giro Certo", details))
                        android.widget.Toast.makeText(context, "Diagnóstico copiado", android.widget.Toast.LENGTH_SHORT).show()
                    }) { Text("Copiar diagnóstico") }
                }
            }
        }
        if (showDetails) AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text("Como essas opções funcionam") },
            text = { Text("O aviso de nota só aparece quando a avaliação está disponível. Ele não rejeita a oferta nem muda o lucro.\n\nA leitura por imagem fica no aparelho e não é gravada. O comprovante visual é separado: pode salvar uma imagem da tela em Imagens/GiroCerto, no máximo uma a cada 30 segundos.\n\nSalvar automaticamente guarda a análise neste aparelho. Isso não significa que a corrida foi aceita.") },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Entendi") } }
        )
    }
}

@Composable
private fun Preference(label: String, checked: Boolean, platform: com.girocerto.domain.model.WorkPlatform? = null, change: (Boolean) -> Unit) {
    TactileCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            platform?.let { com.girocerto.ui.components.PlatformIcon(it); Spacer(Modifier.width(12.dp)) }
            Text(label, Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = change)
        }
    }
}

