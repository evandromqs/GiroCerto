package com.girocerto.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal enum class DownloadPhase { AVAILABLE, DOWNLOADING, VERIFYING, READY, FAILED }
internal data class UpdateState(val release: AppRelease? = null, val checking: Boolean = false,
    val phase: DownloadPhase = DownloadPhase.AVAILABLE, val progress: Int? = null,
    val message: String = "Verifica novas versões ao abrir o app.", val showPrompt: Boolean = false)

internal class AppUpdateManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val downloads = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val installedName: String = packageInfo.versionName ?: "1.0"
    private val installedCode = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
    private val _state = MutableStateFlow(UpdateState())
    val state = _state.asStateFlow()
    private var downloadJob: Job? = null
    private val transferMutex = Mutex()
    private var installJob: Job? = null
    private var pendingInstall: AppRelease? = null
    private var lastCheck = -60_000L
    private val updateDirectory get() = File(context.filesDir, "updates").apply { mkdirs() }
    private fun apk(release: AppRelease) = File(updateDirectory, release.fileName)

    init {
        val saved = runCatching { parse(preferences.getString("release", null) ?: "") }.getOrNull()
        val id = preferences.getLong("download_id", -1L)
        if (saved != null && saved.isNewerThan(installedCode)) {
            _state.value = UpdateState(release = saved, phase = DownloadPhase.DOWNLOADING)
            downloadJob = scope.launch { transferMutex.withLock {
                if (withContext(Dispatchers.IO) { apk(saved).isFile }) verifyAndReady(saved, false)
                else if (id > 0) monitor(id, saved, false)
                else unavailable("O APK não está mais disponível. Baixe novamente.")
            } }
        } else {
            clearDownload()
            scope.launch(Dispatchers.IO) { updateDirectory.listFiles()?.filter { it.name.endsWith(".apk") || it.name.endsWith(".part") }?.forEach { it.delete() } }
        }
    }
    fun check(force: Boolean = false) {
        if (_state.value.checking) { return }
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastCheck < 60_000L) { return }
        lastCheck = now
        _state.update { it.copy(checking = true) }
        scope.launch {
            try {
                val release = withContext(Dispatchers.IO) { parse(fetchManifest()) }
                if (release.isNewerThan(installedCode)) {
                    if (_state.value.release != release) {
                        cancelTransfer()
                        _state.value = UpdateState(release = release, message = "Nova versão disponível: ${release.versionName}", showPrompt = force || shouldPrompt(release.versionCode))
                    } else {
                        if (_state.value.phase == DownloadPhase.READY && !withContext(Dispatchers.IO) { apk(release).isFile }) unavailable("APK removido. Baixe novamente.")
                        _state.update { it.copy(checking = false, showPrompt = force || shouldPrompt(release.versionCode)) }
                    }
                } else { cancelTransfer(); _state.value = UpdateState(message = "Você já usa a versão mais recente.") }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(checking = false, message = "Não foi possível consultar atualizações. Confira a conexão e tente novamente.") } }
        }
    }
    private fun clearDownload() { preferences.edit().remove("release").remove("download_id").apply() }
    private fun shouldPrompt(version: Long) = UpdateReminder.shouldPrompt(version, preferences.getLong("dismissed_version", -1), preferences.getLong("remind_after", 0), System.currentTimeMillis())
    fun dismiss() {
        _state.value.release?.let { preferences.edit().putLong("dismissed_version", it.versionCode).putLong("remind_after", System.currentTimeMillis() + UpdateReminder.DELAY_MS).apply() }
        _state.update { it.copy(showPrompt = false) }
    }
    fun open() { if (_state.value.release == null) check(true) else { onResume(); _state.update { it.copy(showPrompt = true) } } }
    private fun cancelTransfer() {
        downloadJob?.cancel(); downloadJob = null
        val id = preferences.getLong("download_id", -1L)
        if (id > 0) downloads.remove(id)
        clearDownload()
    }
    fun cancelDownload() { cancelTransfer(); _state.update { it.copy(phase = DownloadPhase.AVAILABLE, progress = null, message = "Download cancelado. Você pode tentar novamente.") } }
    fun download() {
        val release = _state.value.release ?: return
        if (downloadJob?.isActive == true) return
        if (_state.value.phase == DownloadPhase.READY && apk(release).isFile) return
        cancelTransfer()
        _state.update { it.copy(phase = DownloadPhase.DOWNLOADING, progress = null, message = "Baixando na pasta do Giro Certo…") }
        downloadJob = scope.launch { transferMutex.withLock {
            var createdId = -1L
            try {
                val id = withContext(Dispatchers.IO) {
                    val destination = File(context.getExternalFilesDir("updates"), release.fileName)
                    if (destination.exists()) check(destination.delete())
                    val request = DownloadManager.Request(Uri.parse(release.apkUrl)).setTitle(release.fileName)
                        .setDescription("Atualização do Giro Certo").setMimeType("application/vnd.android.package-archive")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalFilesDir(context, "updates", release.fileName)
                    val id = downloads.enqueue(request)
                    createdId = id
                    try {
                        check(preferences.edit().putLong("download_id", id).putString("release", serialize(release)).commit())
                        currentCoroutineContext().ensureActive()
                        id
                    } catch (error: Exception) {
                        downloads.remove(id)
                        if (preferences.getLong("download_id", -1L) == id) clearDownload()
                        throw error
                    }
                }
                monitor(id, release, true)
            } catch (e: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) {
                    if (createdId > 0) { downloads.remove(createdId)
                        if (preferences.getLong("download_id", -1L) == createdId) clearDownload() }
                }
                throw e
            } catch (_: Exception) { unavailable("Não foi possível baixar. Confira a conexão e o espaço livre.") }
        } }
    }
    private suspend fun monitor(id: Long, release: AppRelease, userInitiated: Boolean) {
        try {
            while (currentCoroutineContext().isActive) {
                val status = withContext(Dispatchers.IO) {
                    downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                        check(cursor != null && cursor.moveToFirst())
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        Triple(status, if(total > 0) (done * 100 / total).toInt().coerceIn(0,100) else null, reason)
                    }
                }
                _state.update { it.copy(phase = DownloadPhase.DOWNLOADING, progress = status.second,
                    message = if(status.first == DownloadManager.STATUS_PAUSED) "Download pausado pelo Android. Aguarde a rede ou cancele para tentar novamente." else "Baixando ${release.fileName}") }
                if(status.first == DownloadManager.STATUS_FAILED) { unavailable("Falha no download (código ${status.third}). Confira rede e armazenamento e tente novamente."); return }
                if(status.first == DownloadManager.STATUS_SUCCESSFUL) {
                    _state.update { it.copy(phase = DownloadPhase.VERIFYING, message = "Conferindo o APK…") }
                    withContext(Dispatchers.IO) {
                        val uri = checkNotNull(downloads.getUriForDownloadedFile(id))
                        val temporary = File(updateDirectory, release.fileName + ".part")
                        try {
                            checkNotNull(context.contentResolver.openInputStream(uri)).use { input -> temporary.outputStream().use { input.copyTo(it) } }
                            check(checksum(temporary) == release.sha256)
                            val target = apk(release); if(target.exists()) check(target.delete())
                            check(temporary.renameTo(target))
                        } finally { temporary.delete() }
                    }
                    downloads.remove(id)
                    preferences.edit().remove("download_id").apply()
                    verifyAndReady(release, userInitiated); return
                }
                delay(1_000)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { unavailable("O APK não pôde ser conferido. Baixe novamente.") }
    }
    private fun checksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(64 * 1024)
            while(true) { val count = input.read(buffer); if(count < 0) break; digest.update(buffer,0,count) } }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private suspend fun verifyAndReady(release: AppRelease, prompt: Boolean) {
        val valid = withContext(Dispatchers.IO) { runCatching { checksum(apk(release)) == release.sha256 }.getOrDefault(false) }
        if (!valid) { withContext(Dispatchers.IO) { apk(release).delete() }; unavailable("Arquivo ausente ou inválido. Baixe novamente."); return }
        _state.update { it.copy(phase = DownloadPhase.READY, progress = 100,
            message = "APK verificado. Toque em Instalar atualização para abrir o instalador do Android.", showPrompt = prompt || shouldPrompt(release.versionCode)) }
    }
    private fun unavailable(message: String) { _state.update { it.copy(phase = DownloadPhase.FAILED, progress = null, message = message, showPrompt = true) } }
    fun onResume() {
        val pending = pendingInstall
        pendingInstall = null
        if (pending != null && context.packageManager.canRequestPackageInstalls()) install()
        val release = _state.value.release ?: return
        if (_state.value.phase == DownloadPhase.READY) scope.launch {
            if (!withContext(Dispatchers.IO) { apk(release).isFile }) unavailable("APK removido. Baixe novamente.")
        }
    }
    fun install() {
        val release = _state.value.release ?: return
        if (installJob?.isActive == true) return
        installJob = scope.launch {
            try {
                val valid = withContext(Dispatchers.IO) { apk(release).isFile && checksum(apk(release)) == release.sha256 }
                if (!valid) { unavailable("APK ausente ou alterado. Baixe novamente."); return@launch }
                if (!context.packageManager.canRequestPackageInstalls()) {
                    pendingInstall = release
                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return@launch
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk(release))
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
                dismiss()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(message = "Não foi possível abrir o instalador. Verifique a permissão para instalar atualizações.", showPrompt = true) } }
        }
    }
    private fun parse(text: String): AppRelease {
        val json = JSONObject(text)
        return AppRelease.validated(json.getLong("versionCode"), json.getString("versionName"), json.getString("sha256"), json.getString("apkUrl"))
    }
    private fun serialize(release: AppRelease) = JSONObject().put("versionCode", release.versionCode)
        .put("versionName", release.versionName).put("sha256", release.sha256).put("apkUrl", release.apkUrl).toString()

    private fun fetchManifest(): String {
        var url = URL("https://github.com/evandromqs/GiroCerto/releases/latest/download/update.json")
        repeat(6) {
            require(url.protocol == "https" && (url.host == "github.com" || url.host.endsWith(".githubusercontent.com")))
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 12_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "GiroCerto/$installedName")
                when (connection.responseCode) {
                    301, 302, 303, 307, 308 -> url = URL(url, connection.getHeaderField("Location") ?: error("Missing redirect"))
                    200 -> return connection.inputStream.bufferedReader().use { reader ->
                        val buffer = CharArray(65_537)
                        var total = 0
                        while (total < buffer.size) {
                            val count = reader.read(buffer, total, buffer.size - total)
                            if (count < 0) break
                            total += count
                        }
                        require(total <= 65_536)
                        String(buffer, 0, total)
                    }
                    else -> error("Update endpoint unavailable")
                }
            } finally { connection.disconnect() }
        }
        error("Too many redirects")
    }
}
