package com.girocerto.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.LocalDate

/** Persistencia Android dos rascunhos, isolada do ViewModel. */
class LedgerDraftStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ledger_drafts_v1", Context.MODE_PRIVATE)

    suspend fun load(revision: Long): Map<String, List<String>> = withContext(Dispatchers.IO) {
        if (prefs.getLong("revision", 0L) != revision) {
            check(prefs.edit().clear().putLong("revision", revision).commit())
        }
        prefs.all.mapNotNull { (key, value) ->
            if (key == "revision") return@mapNotNull null
            runCatching {
                val array = JSONArray(value as String)
                require(array.length() == FIELD_COUNT)
                key to (0 until array.length()).map { array.getString(it).take(MAX_FIELD_LENGTH) }
            }.getOrNull()
        }.toMap()
    }

    suspend fun save(revision: Long, date: LocalDate, fields: List<String>) = withContext(Dispatchers.IO) {
        check(fields.size == FIELD_COUNT)
        check(prefs.edit().putLong("revision", revision).putString(date.toString(), JSONArray(fields).toString()).commit())
    }

    suspend fun clear(revision: Long) = withContext(Dispatchers.IO) {
        check(prefs.edit().clear().putLong("revision", revision).commit())
    }

    private companion object {
        const val FIELD_COUNT = 20
        const val MAX_FIELD_LENGTH = 18
    }
}
