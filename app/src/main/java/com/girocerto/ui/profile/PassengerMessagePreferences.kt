package com.girocerto.ui.profile

import android.content.Context

/** A local draft only; no passenger identity or messaging service is stored. */
class PassengerMessagePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("passenger_message", Context.MODE_PRIVATE)
    fun read(): String = preferences.getString("template", DEFAULT_MESSAGE) ?: DEFAULT_MESSAGE
    fun save(message: String) { preferences.edit().putString("template", message.trim()).apply() }
    companion object {
        const val DEFAULT_MESSAGE = "Olá! Sou seu motorista. Podemos confirmar o ponto de encontro?"
    }
}
