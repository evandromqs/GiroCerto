package com.girocerto.data.cloud

import android.content.Context
import android.util.Base64
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.girocerto.app.BuildConfig
import com.girocerto.data.repository.RideRepository
import com.girocerto.data.repository.WorkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit

class CloudBackupManager(
    context: Context,
    private val workRepository: WorkRepository,
    private val rideRepository: RideRepository,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("cloud_backup_v1", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow("Backup em nuvem aguardando sincronização")
    val status = _status.asStateFlow()

    val recoveryCode: String get() = ensureCredentials().let { "${it.first}.${it.second}" }

    fun schedule() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val periodic = PeriodicWorkRequestBuilder<CloudBackupWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints).build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            "girocerto-cloud-backup", ExistingPeriodicWorkPolicy.UPDATE, periodic
        )
        syncSoon()
    }

    fun syncSoon() {
        enqueueSync(appContext)
    }

    suspend fun upload(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (id, secret) = ensureCredentials()
            val payload = JSONObject()
                .put("format", "girocerto-cloud-backup")
                .put("version", 1)
                .put("work", JSONObject(workRepository.backup()))
                .put("rides", org.json.JSONArray(rideRepository.backup()))
            val body = JSONObject().put("payload", payload).put("clientUpdatedAt", System.currentTimeMillis()).toString()
            request("PUT", id, secret, body)
            prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply()
            _status.value = "Dados protegidos na nuvem"
        }.onFailure { _status.value = "Backup pendente; nova tentativa será automática" }
    }

    suspend fun restore(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val credentials = parseRecoveryCode(code) ?: error("Código de recuperação inválido")
            val response = JSONObject(request("GET", credentials.first, credentials.second, null))
            val payload = response.getJSONObject("payload")
            require(payload.getString("format") == "girocerto-cloud-backup" && payload.getInt("version") == 1)
            workRepository.restoreBackup(payload.getJSONObject("work").toString())
            rideRepository.restoreBackup(payload.getJSONArray("rides").toString())
            prefs.edit().putString("id", credentials.first).putString("secret", credentials.second)
                .putLong("last_sync", System.currentTimeMillis()).commit()
            _status.value = "Backup restaurado e vinculado a este aparelho"
        }.onFailure { _status.value = "Não foi possível restaurar o backup" }
    }

    private fun request(method: String, id: String, secret: String, body: String?): String {
        val connection = URL("${BuildConfig.CLOUD_BACKUP_URL}?id=$id").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer $secret")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            check(connection.responseCode in 200..299) { JSONObject(response).optString("error", "Erro ${connection.responseCode}") }
            return response
        } finally { connection.disconnect() }
    }

    private fun ensureCredentials(): Pair<String, String> {
        val existingId = prefs.getString("id", null)
        val existingSecret = prefs.getString("secret", null)
        if (existingId != null && existingSecret != null) return existingId to existingSecret
        val id = UUID.randomUUID().toString()
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val secret = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        check(prefs.edit().putString("id", id).putString("secret", secret).commit())
        return id to secret
    }

    internal fun parseRecoveryCode(value: String): Pair<String, String>? {
        val parts = value.trim().split('.', limit = 2)
        if (parts.size != 2) return null
        val id = runCatching { UUID.fromString(parts[0]).toString() }.getOrNull() ?: return null
        val secret = parts[1]
        if (!secret.matches(Regex("[A-Za-z0-9_-]{40,64}"))) return null
        return id to secret
    }

    companion object {
        fun enqueueSync(context: Context) {
            val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "girocerto-cloud-backup-now", ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}
