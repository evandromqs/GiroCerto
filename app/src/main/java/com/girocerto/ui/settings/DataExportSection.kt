package com.girocerto.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.girocerto.app.GiroCertoApp
import com.girocerto.app.R
import com.girocerto.app.theme.AppSpacing
import com.girocerto.data.repository.WorkExport
import com.girocerto.ui.components.AppText as Text
import com.girocerto.ui.components.NeobrutalCard
import com.girocerto.ui.components.TactileButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DataExportSection() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val cloud = GiroCertoApp.instance.cloudBackup
    val cloudStatus by cloud.status.collectAsState()
    val recoveryCode = remember { cloud.recoveryCode }
    var showCloudRestore by remember { mutableStateOf(false) }
    var cloudCode by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            message = try {
                val snapshot = GiroCertoApp.instance.workRepository.days.value
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri) ?: error("Arquivo indisponível")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write("\uFEFF" + WorkExport.csv(snapshot)) }
                }
                "Ganhos exportados. Cada linha corresponde a um dia."
            } catch (_: Exception) { "Não foi possível exportar. Tente outro local." }
        }
    }
    var pendingBackup by remember { mutableStateOf<String?>(null) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    val text = GiroCertoApp.instance.workRepository.backup()
                    checkNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(text) }
                }
                "Backup completo exportado. Guarde em um local seguro."
            } catch (_: Exception) { "Não foi possível exportar o backup." }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                pendingBackup = withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { reader ->
                        val chars = CharArray(5_000_001); var count = 0
                        while (count < chars.size) { val n = reader.read(chars, count, chars.size - count); if (n < 0) break; count += n }
                        require(count <= 5_000_000); String(chars, 0, count)
                    }
                }
            } catch (_: Exception) { message = "Backup inválido ou maior que 5 MB." }
        }
    }
    if (pendingBackup != null) {
        AlertDialog(
            onDismissRequest = { pendingBackup = null },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = {
                Text(
                    text = "Restaurar backup?",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "O perfil, os apps e os dias presentes no backup substituirão os correspondentes neste aparelho. Dias que não estão no arquivo serão preservados. Exporte o backup atual antes de continuar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TactileButton(
                    onClick = {
                        val text = pendingBackup ?: return@TactileButton
                        pendingBackup = null
                        scope.launch {
                            message = try {
                                GiroCertoApp.instance.workRepository.restoreBackup(text)
                                "Backup restaurado."
                            } catch (_: Exception) {
                                "Restauração recusada: arquivo inválido, versão incompatível ou registro protegido. Nenhum dado foi alterado."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(AppSpacing.CornerButton),
                    border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Restaurar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TactileButton(
                    onClick = { pendingBackup = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(AppSpacing.CornerButton),
                    border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cancelar")
                }
            }
        )
    }
    if (showCloudRestore) {
        AlertDialog(
            onDismissRequest = { showCloudRestore = false },
            title = { Text("Restaurar da nuvem", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cole o código de recuperação. Os dados deste aparelho serão substituídos pelo backup vinculado ao código.")
                    OutlinedTextField(
                        value = cloudCode,
                        onValueChange = { cloudCode = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Código de recuperação") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TactileButton(onClick = {
                    val code = cloudCode
                    showCloudRestore = false
                    scope.launch {
                        message = cloud.restore(code).fold(
                            onSuccess = { "Backup em nuvem restaurado." },
                            onFailure = { "Não foi possível restaurar: código inválido ou backup indisponível." },
                        )
                    }
                }) { Text("Restaurar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showCloudRestore = false }) { Text("Cancelar") } },
        )
    }

    NeobrutalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppSpacing.Content),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Dados e backup",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TactileButton(
                onClick = { export.launch("GiroCerto-ganhos.csv") },
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
                Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Exportar meus ganhos", fontWeight = FontWeight.Bold)
            }
            TactileButton(
                onClick = { backup.launch("GiroCerto-backup.json") },
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
                Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Exportar backup completo", fontWeight = FontWeight.Bold)
            }
            TactileButton(
                onClick = { restore.launch(arrayOf("application/json", "text/plain")) },
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
                Icon(painterResource(R.drawable.ic_upload), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Restaurar backup", fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                "Proteção em nuvem",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                cloudStatus,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
            TactileButton(
                onClick = {
                    clipboard.setText(AnnotatedString(recoveryCode))
                    message = "Código de recuperação copiado. Guarde-o em local seguro."
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = AppSpacing.MinTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                shape = RoundedCornerShape(AppSpacing.CornerButton),
                border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline),
            ) {
                Icon(painterResource(R.drawable.ic_copy), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copiar código de recuperação", fontWeight = FontWeight.Bold)
            }
            TactileButton(
                onClick = { scope.launch { message = if (cloud.upload().isSuccess) "Backup em nuvem atualizado." else "Sem conexão; tentaremos novamente automaticamente." } },
                modifier = Modifier.fillMaxWidth().heightIn(min = AppSpacing.MinTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(AppSpacing.CornerButton),
                border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.outline),
            ) { Text("Sincronizar agora", fontWeight = FontWeight.Bold) }
            TextButton(onClick = { showCloudRestore = true }) { Text("Restaurar usando código") }
            message?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
