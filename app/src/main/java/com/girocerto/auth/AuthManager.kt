package com.girocerto.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.girocerto.app.BuildConfig
import com.girocerto.app.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.tasks.await
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
                val activity = context.findActivity() ?: error("Não foi possível localizar a tela ativa para abrir o Google")
                val googleOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(appContext.getString(R.string.default_web_client_id))
                    .setAutoSelectEnabled(false)
                    .build()
                val credential = CredentialManager.create(activity).getCredential(
                    context = activity,
                    request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build(),
                ).credential
                check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    "Credencial do Google não reconhecida"
                }
                val googleToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val firebaseUser = FirebaseAuth.getInstance().signInWithCredential(
                    GoogleAuthProvider.getCredential(googleToken, null),
                ).await().user ?: error("Conta Google não encontrada")
                val firebaseToken = firebaseUser.getIdToken(false).await().token
                    ?: error("Token Firebase indisponível")
                val response = JSONObject(request("POST", "auth/firebase/exchange", authorization = firebaseToken))
                saveSession(response)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = googleErrorMessage(error),
                )
            }
        }
    }

    private fun Context.findActivity(): ComponentActivity? {
        var current: Context = this
        while (current is ContextWrapper) {
            if (current is ComponentActivity) return current
            val base = current.baseContext
            if (base === current) break
            current = base
        }
        return current as? ComponentActivity
    }

    private fun googleErrorMessage(error: Throwable): String {
        val detail = generateSequence(error) { it.cause }.mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull()
        val type = error.javaClass.simpleName.removeSuffix("Exception").ifBlank { "Falha desconhecida" }
        return "Google: $type" + (detail?.let { " — ${it.take(220)}" } ?: "")
    }

    fun signUp(name: String, email: String, password: String) = emailAuth("sign-up", name, email, password)

    fun signIn(email: String, password: String) = emailAuth("sign-in", "", email, password)

    fun clearError() { _state.value = _state.value.copy(error = null) }

    private fun emailAuth(action: String, name: String, email: String, password: String) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        scope.launch {
            runCatching {
                val body = JSONObject().put("email", email.trim()).put("password", password)
                if (action == "sign-up") body.put("name", name.trim())
                saveSession(JSONObject(request("POST", "auth/email/$action", body.toString())))
            }.onFailure { error ->
                _state.value = AuthState(error = error.message?.takeIf { it.isNotBlank() }
                    ?: if (action == "sign-up") "Não foi possível criar sua conta." else "E-mail ou senha incorretos.")
            }
        }
    }

    private fun saveSession(response: JSONObject) {
        val userJson = response.getJSONObject("user")
        val user = AuthUser(userJson.getString("id"), userJson.optString("email"), userJson.optString("name", "Motorista"))
        check(prefs.edit().putString("token", response.getString("token"))
            .putString("id", user.id).putString("email", user.email).putString("name", user.name).commit())
        _state.value = AuthState(user = user)
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
        prefs.edit().clear().apply()
        _state.value = AuthState()
    }

    private fun loadLocal(): AuthState {
        val token = prefs.getString("token", null) ?: return AuthState()
        val id = prefs.getString("id", null) ?: return AuthState()
        return AuthState(user = AuthUser(id, prefs.getString("email", "").orEmpty(), prefs.getString("name", "Motorista").orEmpty()))
    }

    private fun request(method: String, path: String, body: String? = null, authorization: String? = null): String {
        val connection = URL("${BuildConfig.CLOUD_BACKUP_URL}$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            authorization?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
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
