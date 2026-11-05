package com.girocerto.ui.copilot

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.girocerto.app.R
import com.girocerto.app.theme.AppSpacing
import com.girocerto.domain.model.WorkPlatform
import com.girocerto.monitoring.CopilotPolicy
import com.girocerto.monitoring.CopilotPreferences
import com.girocerto.monitoring.GiroCertoAccessibilityService
import com.girocerto.monitoring.ReaderDiagnostics
import com.girocerto.overlay.OverlayController
import com.girocerto.overlay.OverlayCorner
import com.girocerto.ui.components.AppText as Text
import com.girocerto.ui.components.NeobrutalCard
import com.girocerto.ui.components.PlatformIcon
import com.girocerto.ui.components.TactileButton

@Composable
fun CopilotModal(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.84f),
            shape = RoundedCornerShape(AppSpacing.CornerCard),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(AppSpacing.BorderThick, MaterialTheme.colorScheme.outline),
            tonalElevation = 0.dp,
            shadowElevation = 24.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Ajustes do copiloto",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(AppSpacing.MinTouchTarget)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Fechar ajustes do copiloto",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
                Box(Modifier.weight(1f)) {
                    CopilotScreen(inModal = true)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CopilotScreen(inModal: Boolean = false) {
    val context = LocalContext.current
    val repository = remember(context) { CopilotPreferences(context) }
    var policy by remember { mutableStateOf(repository.read()) }
    val connected by GiroCertoAccessibilityService.isServiceRunning.collectAsStateWithLifecycle()
    val hidden by OverlayController.isHidden.collectAsStateWithLifecycle()
    val floating by OverlayController.isOverlayActive.collectAsStateWithLifecycle()
    val reader by GiroCertoAccessibilityService.readerStatus.collectAsStateWithLifecycle()
    val observation by GiroCertoAccessibilityService.readerObservation.collectAsStateWithLifecycle()
    var overlayAllowed by remember { mutableStateOf(OverlayController.canDrawOverlays(context)) }
    var readingAllowed by remember { mutableStateOf(GiroCertoAccessibilityService.isAccessibilityEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                policy = repository.read()
                overlayAllowed = OverlayController.canDrawOverlays(context)
                readingAllowed = GiroCertoAccessibilityService.isAccessibilityEnabled(context)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (inModal) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(AppSpacing.Screen),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Section),
        horizontalAlignment = Alignment.Start
    ) {
        if (!inModal) {
            Text(
                text = "Seu copiloto",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = "Escolha o que acompanhar e ouvir. Você decide sobre cada oferta.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        val statusText = when {
            !policy.enabled -> "Copiloto pausado"
            !connected && !readingAllowed -> "Falta autorizar a leitura de ofertas"
            hidden -> "Janela flutuante ocultada por você"
            !overlayAllowed -> "Falta autorizar o cartão flutuante"
            !floating -> "Leitura conectada. O botão flutuante ainda não apareceu."
            else -> "Copiloto ativo. O botão flutuante está visível."
        }
        Text(
            text = statusText,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // SEÇÃO: Permissões Essenciais
        NeobrutalCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(AppSpacing.Content),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Permissões do Copiloto",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                TactileButton(
                    onClick = { showReadingDisclosure = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppSpacing.MinTouchTarget),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (readingAllowed || connected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                        contentColor = if (readingAllowed || connected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(AppSpacing.CornerButton),
                    border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(painterResource(R.drawable.ic_copilot), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (readingAllowed || connected) "Leitura de ofertas • permitida" else "Autorizar leitura de ofertas",
                        fontWeight = FontWeight.Bold
                    )
                }

                TactileButton(
                    onClick = { context.startActivity(OverlayController.createOverlayPermissionIntent(context)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppSpacing.MinTouchTarget),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (overlayAllowed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                        contentColor = if (overlayAllowed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(AppSpacing.CornerButton),
                    border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(painterResource(R.drawable.ic_open_app), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (overlayAllowed) "Cartão flutuante • permitido" else "Autorizar cartão flutuante",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (hidden) {
                    TactileButton(
                        onClick = { OverlayController.restore(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppSpacing.MinTouchTarget),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(AppSpacing.CornerButton),
                        border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(painterResource(R.drawable.ic_open_app), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mostrar botão flutuante", fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "Arraste a bolha até a lixeira para ocultar a janela. A leitura continua.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // SEÇÃO: Estado do Copiloto & Apps Acompanhados
        NeobrutalCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(AppSpacing.Content),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PreferenceRow(
                    label = "Copiloto ativo",
                    checked = policy.enabled,
                    onCheckedChange = { update(policy.copy(enabled = it)) }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    thickness = 1.dp
                )

                Text(
                    text = "Aplicativos acompanhados",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                PreferenceRow(
                    label = "99 e 99Food",
                    checked = policy.ninetyNine,
                    platform = WorkPlatform.NINETY_NINE,
                    onCheckedChange = { update(policy.copy(ninetyNine = it)) }
                )
                PreferenceRow(
                    label = "Uber e Uber Eats",
                    checked = policy.uber,
                    platform = WorkPlatform.UBER,
                    onCheckedChange = { update(policy.copy(uber = it)) }
                )
                PreferenceRow(
                    label = "iFood",
                    checked = policy.ifood,
                    platform = WorkPlatform.IFOOD,
                    onCheckedChange = { update(policy.copy(ifood = it)) }
                )
                PreferenceRow(
                    label = "Qida",
                    checked = policy.qida,
                    platform = WorkPlatform.QIDA,
                    onCheckedChange = { update(policy.copy(qida = it)) }
                )
            }
        }

        // SEÇÃO: Canto de Repouso do Modal (Resting Corner Gravity Picker - FEATURE 10)
        NeobrutalCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(AppSpacing.Content),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Canto de repouso do modal",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Escolha o canto da tela onde o modal flutuante deve ancorar ao abrir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val corners = listOf(
                    OverlayCorner.TOP_LEFT,
                    OverlayCorner.TOP_RIGHT,
                    OverlayCorner.BOTTOM_LEFT,
                    OverlayCorner.BOTTOM_RIGHT
                )

                // 2x2 grid for corner picker
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    corners.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (corner in row) {
                                val selected = policy.restingCorner == corner
                                TactileButton(
                                    onClick = { update(policy.copy(restingCorner = corner)) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = AppSpacing.MinTouchTarget),
                                    shape = RoundedCornerShape(AppSpacing.CornerSmall),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = BorderStroke(
                                        AppSpacing.BorderThin,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    CornerPreview(
                                        corner = corner,
                                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = corner.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Toggle Mais Opções
        TactileButton(
            onClick = { showMore = !showMore },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppSpacing.MinTouchTarget),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(AppSpacing.CornerButton),
            border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
        ) {
            Icon(
                painter = painterResource(if (showMore) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (showMore) "Ocultar mais opções" else "Mais opções",
                fontWeight = FontWeight.Bold
            )
        }

        if (showMore) {
            // SEÇÃO: Alertas por Classificação
            NeobrutalCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(AppSpacing.Content),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Alertas por classificação",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "A voz geral continua em Cartão e voz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PreferenceRow("Ouvir ofertas verdes", policy.speakGreen) { update(policy.copy(speakGreen = it)) }
                    PreferenceRow("Ouvir ofertas amarelas", policy.speakYellow) { update(policy.copy(speakYellow = it)) }
                    PreferenceRow("Ouvir ofertas vermelhas", policy.speakRed) { update(policy.copy(speakRed = it)) }
                    PreferenceRow("Notificar novas análises", policy.notifyOffers) { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33) {
                            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            update(policy.copy(notifyOffers = enabled))
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )

                    Text(
                        text = "Nota do passageiro",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (policy.minimumPassengerRating == 0.0) "Aviso desligado" else "Avisar abaixo de %.1f".format(policy.minimumPassengerRating),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0.0, 4.0, 4.5, 4.8, 5.0, policy.minimumPassengerRating).distinct().sorted().forEach { rating ->
                            FilterChip(
                                selected = policy.minimumPassengerRating == rating,
                                onClick = { update(policy.copy(minimumPassengerRating = rating)) },
                                label = { Text(if (rating == 0.0) "Desligado" else "%.1f".format(rating)) },
                                shape = RoundedCornerShape(AppSpacing.CornerSmall),
                                border = BorderStroke(
                                    AppSpacing.BorderThin,
                                    if (policy.minimumPassengerRating == rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            // SEÇÃO: OCR & Comprovantes
            NeobrutalCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(AppSpacing.Content),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Leitura e Registro de Ofertas",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (Build.VERSION.SDK_INT >= 30) {
                        PreferenceRow("Leitura local de imagem (OCR)", policy.ocrEnabled) { update(policy.copy(ocrEnabled = it)) }
                        PreferenceRow("Salvar comprovante visual", policy.captureOfferScreenshot) { update(policy.copy(captureOfferScreenshot = it)) }
                    } else {
                        Text(
                            text = "OCR e comprovantes exigem Android 11 ou mais recente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    PreferenceRow("Salvar análises automaticamente", policy.autoSave) { update(policy.copy(autoSave = it)) }
                    PreferenceRow("Rejeitar ofertas no prejuízo automaticamente", policy.autoRejectLoss) { update(policy.copy(autoRejectLoss = it)) }
                    Text("Recusa automaticamente chamadas avaliadas como Prejuízo nos aplicativos de motorista.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PreferenceRow("Adicionar 5 min — Uber e 99", policy.addFiveMinutes) { update(policy.copy(addFiveMinutes = it)) }
                    Text("Acrescenta 5 minutos à duração informada para calcular R$/hora e custos com margem. Não estima tempo quando ele não aparece na oferta.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    TactileButton(
                        onClick = { showDetails = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppSpacing.MinTouchTarget),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(AppSpacing.CornerButton),
                        border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(painterResource(R.drawable.ic_info), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Saiba mais sobre estas opções", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // SEÇÃO: Diagnóstico da Leitura
            NeobrutalCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(AppSpacing.Content),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Diagnóstico da leitura",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = observation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = reader,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TactileButton(
                        onClick = {
                            val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                            val details = "Giro Certo $version • Android ${Build.VERSION.RELEASE}\n" +
                                "Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} • API ${Build.VERSION.SDK_INT}\n" +
                                "Leitor conectado: $connected • Copiloto ativo: ${policy.enabled} • OCR: ${policy.ocrEnabled} • Janela: $floating\n" +
                                "$observation\n$reader\n" + ReaderDiagnostics.lastFailure
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Diagnóstico Giro Certo", details))
                            android.widget.Toast.makeText(context, "Diagnóstico copiado", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppSpacing.MinTouchTarget),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(AppSpacing.CornerButton),
                        border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(painterResource(R.drawable.ic_copy), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copiar diagnóstico", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // DIALOGS
        if (showReadingDisclosure) {
            AlertDialog(
                onDismissRequest = { showReadingDisclosure = false },
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                title = {
                    Text(
                        text = "Leitura de ofertas na tela",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = "Ao ativar o serviço de acessibilidade, o Giro Certo lê textos das janelas dos apps de trabalho selecionados: valores, distâncias, tempos e, quando disponíveis, endereços e avaliações. O processamento acontece no aparelho. As análises só são gravadas quando você salva ou ativa o salvamento automático. OCR e comprovantes de tela são opções separadas, desligadas por padrão. O app não aceita corridas nem executa gestos por você. Você pode desativar o serviço nas configurações do Android. Metas e diário funcionam sem essa permissão.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TactileButton(
                        onClick = {
                            showReadingDisclosure = false
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(AppSpacing.CornerButton),
                        border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("Entendi, abrir configurações", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TactileButton(
                        onClick = { showReadingDisclosure = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(AppSpacing.CornerButton),
                        border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("Agora não")
                    }
                }
            )
        }

        if (showDetails) {
            AlertDialog(
                onDismissRequest = { showDetails = false },
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                title = {
                    Text(
                        text = "Como essas opções funcionam",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = "O aviso de nota só aparece quando a avaliação está disponível. Ele não rejeita a oferta nem muda o lucro.\n\nA leitura por imagem fica no aparelho e não é gravada. O comprovante visual é separado: pode salvar uma imagem da tela em Imagens/GiroCerto, no máximo uma a cada 30 segundos.\n\nSalvar automaticamente guarda a análise neste aparelho. Isso não significa que a corrida foi aceita.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TactileButton(
                        onClick = { showDetails = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(AppSpacing.CornerButton),
                        border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("Entendi", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
private fun CornerPreview(corner: OverlayCorner, contentColor: Color) {
    val alignment = when (corner) {
        OverlayCorner.TOP_LEFT -> Alignment.TopStart
        OverlayCorner.TOP_RIGHT -> Alignment.TopEnd
        OverlayCorner.BOTTOM_LEFT -> Alignment.BottomStart
        OverlayCorner.BOTTOM_RIGHT -> Alignment.BottomEnd
    }
    Box(
        modifier = Modifier
            .size(22.dp)
            .border(BorderStroke(1.dp, contentColor.copy(alpha = 0.6f)), RoundedCornerShape(5.dp)),
        contentAlignment = alignment
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(7.dp)
                .background(contentColor, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun PreferenceRow(
    label: String,
    checked: Boolean,
    platform: WorkPlatform? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        platform?.let {
            PlatformIcon(it)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

