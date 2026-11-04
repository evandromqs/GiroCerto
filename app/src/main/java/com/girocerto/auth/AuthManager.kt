package com.girocerto.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.girocerto.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthUser(val id: String, val email: String, val name: String)
data class AuthState(val loading: Boolean = false, val user: AuthUser? = null, val error: String? = null)

class AuthManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("auth_session_v1", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(loadLocal())
    val state = _state.asStateFlow()

    fun startGoogleLogin(context: Context) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        scope.launch {
            runCatching {
                val response = JSONObject(request("GET", "auth/start"))
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(response.getString("url"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }.onFailure { _state.value = _state.value.copy(loading = false, error = "Não foi possível abrir o login do Google.") }
        }
    }

    fun exchange(code: String) {
        _state.value = _state.value.copy(loading = true, error = null)
        scope.launch {
            runCatching {
                val body = JSONObject().put("code", code).toString()
                val response = JSONObject(request("POST", "auth/exchange", body))
                val userJson = response.getJSONObject("user")
                val user = AuthUser(userJson.getString("id"), userJson.optString("email"), userJson.optString("name", "Motorista"))
                check(prefs.edit().putString("token", response.getString("token"))
                    .putString("id", user.id).putString("email", user.email).putString("name", user.name).commit())
                _state.value = AuthState(user = user)
            }.onFailure { _state.value = AuthState(error = "O login expirou. Tente novamente.") }
        }
    }

    fun signOut() {
        prefs.edit().clear().apply()
        _state.value = AuthState()
    }

    private fun loadLocal(): AuthState {
        val token = prefs.getString("token", null) ?: return AuthState()
        val id = prefs.getString("id", null) ?: return AuthState()
        return AuthState(user = AuthUser(id, prefs.getString("email", "").orEmpty(), prefs.getString("name", "Motorista").orEmpty()))
    }

    private fun request(method: String, path: String, body: String? = null): String {
        val connection = URL("${BuildConfig.CLOUD_BACKUP_URL}$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter().use { it.write(body) }
            }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(connection.responseCode in 200..299) { JSONObject(text).optString("error", "Erro de autenticação") }
            return text
        } finally { connection.disconnect() }
    }
}
