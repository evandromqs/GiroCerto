package com.girocerto.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal enum class DownloadPhase { AVAILABLE, DOWNLOADING, VERIFYING, READY, FAILED }
internal data class UpdateState(
    val release: AppRelease? = null, val checking: Boolean = false,
    val phase: DownloadPhase = DownloadPhase.AVAILABLE, val progress: Int? = null,
    val message: String = "Verifica novas versões ao abrir o app.", val showPrompt: Boolean = false
)

internal class AppUpdateManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val downloads = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val installedName: String = packageInfo.versionName ?: "1.0"
    private val installedCode = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
    private val _state = MutableStateFlow(UpdateState())
    val state = _state.asStateFlow()
    private var downloadJob: Job? = null
    private var lastCheck = -60_000L

    init {
        val saved = runCatching { parse(preferences.getString("release", null) ?: "") }.getOrNull()
        val id = preferences.getLong("download_id", -1L)
        if (saved != null && saved.isNewerThan(installedCode) && id > 0) {
            _state.value = UpdateState(release = saved, phase = DownloadPhase.DOWNLOADING)
            downloadJob = scope.launch { monitor(id, saved) }
        } else clearDownload()
    }

    fun check(force: Boolean = false) {
        if (_state.value.checking || downloadJob?.isActive == true) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastCheck < 60_000L) return
        lastCheck = now
        _state.update { it.copy(checking = true) }
        scope.launch {
            try {
                val release = parse(fetchManifest())
                if (release.isNewerThan(installedCode)) {
                    _state.update { current ->
                        if (current.release == release) current.copy(checking = false, showPrompt = force || shouldPrompt(release.versionCode))
                        else UpdateState(release = release, message = "Nova versão disponível: ${release.versionName}", showPrompt = force || shouldPrompt(release.versionCode))
                    }
                } else _state.update { UpdateState(message = "Você já usa a versão mais recente.") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.update { it.copy(checking = false, message = "Não foi possível consultar atualizações. Confira a conexão e tente novamente.") }
            }
        }
    }

    private fun clearDownload() { preferences.edit().remove("release").remove("download_id").apply() }
    private fun shouldPrompt(version: Long) = UpdateReminder.shouldPrompt(version, preferences.getLong("dismissed_version", -1),
        preferences.getLong("remind_after", 0), System.currentTimeMillis())
    fun dismiss() {
        _state.value.release?.let { preferences.edit().putLong("dismissed_version", it.versionCode)
            .putLong("remind_after", System.currentTimeMillis() + UpdateReminder.DELAY_MS).apply() }
        _state.update { it.copy(showPrompt = false) }
    }
    fun open() {
        if (_state.value.release != null) _state.update { it.copy(showPrompt = true) }
        else check(force = true)
    }

    fun download() {
        val release = _state.value.release ?: return
        if (downloadJob?.isActive == true || _state.value.phase == DownloadPhase.READY) return
        _state.update { it.copy(phase = DownloadPhase.DOWNLOADING, progress = null, message = "Iniciando download…") }
        downloadJob = scope.launch {
            try {
                // Remove only a failed download created by this updater before retrying.
                val previous = preferences.getLong("download_id", -1L)
                val previousRelease = runCatching { parse(preferences.getString("release", null) ?: "") }.getOrNull()
                if (previous > 0 && previousRelease == release) downloads.remove(previous)
                val request = DownloadManager.Request(Uri.parse(release.apkUrl))
                    .setTitle(release.fileName)
                    .setDescription("Atualização do Giro Certo")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                if (Build.VERSION.SDK_INT >= 29) request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, release.fileName)
                val id = downloads.enqueue(request)
                preferences.edit().putLong("download_id", id).putString("release", serialize(release)).commit()
                monitor(id, release)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                fail("Não foi possível baixar. Confira a conexão, o espaço livre e se o arquivo já existe em Downloads.")
            }
        }
    }

    private suspend fun monitor(id: Long, release: AppRelease) {
        try {
            while (true) {
                val status = downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    if (cursor == null || !cursor.moveToFirst()) throw IllegalStateException("Download removed")
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    _state.update { it.copy(progress = if (total > 0) ((bytes * 100 / total).toInt()).coerceIn(0, 100) else null,
                        message = if (status == DownloadManager.STATUS_PAUSED) "Download pausado pelo Android; aguardando conexão ou condições de rede." else "Baixando ${release.fileName}") }
                    status
                }
                if (status == DownloadManager.STATUS_FAILED) throw IllegalStateException("Download failed")
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    _state.update { it.copy(phase = DownloadPhase.VERIFYING, message = "Conferindo o arquivo baixado…") }
                    val uri = downloads.getUriForDownloadedFile(id) ?: throw IllegalStateException("Missing APK")
                    val digest = MessageDigest.getInstance("SHA-256")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
                    } ?: throw IllegalStateException("Unreadable APK")
                    val checksum = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    if (checksum != release.sha256) {
                        downloads.remove(id)
                        clearDownload()
                        fail("O arquivo não passou na verificação. Baixe novamente.")
                        return
                    }
                    _state.update { it.copy(phase = DownloadPhase.READY, progress = 100,
                        message = "${release.fileName} baixado e verificado. Abra Downloads e toque no APK para instalar.", showPrompt = true) }
                    return
                }
                delay(1_000L)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            fail("O download não foi concluído. Tente novamente; confira também o espaço livre em Downloads.")
        }
    }

    private fun fail(message: String) { _state.update { it.copy(phase = DownloadPhase.FAILED, message = message, showPrompt = true) } }
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
