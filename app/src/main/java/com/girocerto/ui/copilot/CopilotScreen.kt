package com.girocerto.ui.copilot

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
    var showReadingDisclosure by remember { mutableStateOf(false) }
    fun update(value: CopilotPolicy) {
        repository.write(value)
        policy = value
        if (!value.enabled) OverlayController.hideOverlay(context)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        update(policy.copy(notifyOffers = granted))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Seu copiloto", style = MaterialTheme.typography.headlineMedium)
        Text("Escolha o que acompanhar e ouvir. Você decide sobre cada oferta.", color = TextSecondary)
        OutlinedButton(onClick = { showReadingDisclosure = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Autorizar leitura de ofertas")
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
            Text("Autorizar cartão flutuante")
        }
        Preference("Copiloto ativo", policy.enabled) { update(policy.copy(enabled = it)) }
        Text("Aplicativos acompanhados", style = MaterialTheme.typography.titleMedium)
        Preference("Uber e Uber Eats", policy.uber) { update(policy.copy(uber = it)) }
        Preference("99 e 99Food", policy.ninetyNine) { update(policy.copy(ninetyNine = it)) }
        Preference("inDrive", policy.inDrive) { update(policy.copy(inDrive = it)) }
        Preference("iFood e Keeta", policy.otherApps) { update(policy.copy(otherApps = it)) }
        Text("Alertas por classificação", style = MaterialTheme.typography.titleMedium)
        Text("Também é necessário ativar a voz nos ajustes gerais.", style = MaterialTheme.typography.bodySmall)
        Preference("Ouvir ofertas verdes", policy.speakGreen) { update(policy.copy(speakGreen = it)) }
        Preference("Ouvir ofertas amarelas", policy.speakYellow) { update(policy.copy(speakYellow = it)) }
        Preference("Ouvir ofertas vermelhas", policy.speakRed) { update(policy.copy(speakRed = it)) }
        Preference("Notificar novas análises", policy.notifyOffers) { enabled ->
            if (enabled && android.os.Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else update(policy.copy(notifyOffers = enabled))
        }
        Text("Nota preferencial do passageiro", style = MaterialTheme.typography.titleMedium)
        Text(if (policy.minimumPassengerRating == 0.0) "Alerta desativado" else "Avisar abaixo de %.1f".format(policy.minimumPassengerRating))
        Slider(value = policy.minimumPassengerRating.toFloat(), valueRange = 0f..5f, steps = 49,
            onValueChange = { update(policy.copy(minimumPassengerRating = kotlin.math.round(it * 10.0) / 10.0)) })
        Text("Somente um aviso quando a nota estiver disponível. Não rejeita ofertas nem altera o cálculo de lucro.", style = MaterialTheme.typography.bodySmall)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            Preference("Salvar comprovante visual", policy.captureOfferScreenshot) { update(policy.copy(captureOfferScreenshot = it)) }
            Text("Desativado por padrão. Ao ativar, cada oferta detectada pode salvar uma imagem visível da tela inteira na galeria, em Imagens/GiroCerto. Dados de outros apps podem aparecer. Não grava vídeo ou áudio. Intervalo mínimo de 30 segundos; ofertas iguais em dois minutos geram uma imagem.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Comprovantes visuais exigem Android 11 ou mais recente.", style = MaterialTheme.typography.bodySmall)
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            Preference("Leitura local de imagem (OCR)", policy.ocrEnabled) { update(policy.copy(ocrEnabled = it)) }
            Text("Opcional, desativada por padrão. Quando faltarem textos acessíveis, lê uma imagem da tela no aparelho para identificar a oferta. Android 11 ou superior. A imagem fica apenas na memória: não é salva, enviada ou gravada. O comprovante visual é uma opção separada.", style = MaterialTheme.typography.bodySmall)
        }
        Text("Histórico", style = MaterialTheme.typography.titleMedium)
        Preference("Salvar análises automaticamente", policy.autoSave) { update(policy.copy(autoSave = it)) }
        Text("Desativado por padrão. Quando ativado, salva ofertas analisadas neste aparelho; isso não significa que a corrida foi aceita ou realizada. Ofertas iguais em até dois minutos são guardadas uma vez.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Preference(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    TactileCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = change)
        }
    }
}

