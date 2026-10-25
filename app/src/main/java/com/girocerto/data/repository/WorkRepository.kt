package com.girocerto.data.repository

import android.content.Context
import com.girocerto.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class WorkRepository(context: Context) {
    private val prefs = context.getSharedPreferences("work_ledger_v1", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _profile = MutableStateFlow(readProfile())
    val profile = _profile.asStateFlow()
    private val _days = MutableStateFlow(readDays())
    val days = _days.asStateFlow()

    private fun readProfile(): ElectricProfile? = runCatching {
        val j = JSONObject(prefs.getString("profile", null) ?: return null)
        ElectricProfile(j.getString("name"), j.getDouble("rent"), j.getDouble("kmCost"),
            j.getDouble("extra"), j.getInt("days"), j.getDouble("hours"),
            j.getDouble("km"), j.getDouble("goal")).takeIf { it.valid() }
    }.getOrNull()

    private fun readDays(): List<WorkDay> = prefs.all.keys.filter { it.startsWith("day:") }.mapNotNull { key ->
        runCatching {
            val j = JSONObject(prefs.getString(key, null)!!)
            val a = j.getJSONArray("entries")
            val entries = (0 until a.length()).map { index ->
                val row = a.getJSONObject(index)
                PlatformWork(WorkPlatform.valueOf(row.getString("app")), row.getDouble("revenue"), row.getDouble("km"), row.getDouble("hours"))
            }
            WorkDay(LocalDate.parse(key.removePrefix("day:")), entries, j.getDouble("km"), j.getDouble("hours"),
                j.getDouble("fixed"), j.getDouble("variable"), j.getDouble("goal")).takeIf { it.valid() }
        }.getOrNull()
    }.sortedByDescending { it.date }

    suspend fun saveProfile(value: ElectricProfile) = withContext(Dispatchers.IO) { mutex.withLock {
        require(value.valid())
        val j = JSONObject().put("name", value.name).put("rent", value.weeklyRent).put("kmCost", value.chargedPerKm)
            .put("extra", value.extraPerKm).put("days", value.workDays).put("hours", value.hoursPerDay)
            .put("km", value.kmPerDay).put("goal", value.weeklyNetGoal)
        check(prefs.edit().putString("profile", j.toString()).commit()) { "Não foi possível salvar o perfil." }
        _profile.value = value
    } }

    suspend fun saveDay(value: WorkDay) = withContext(Dispatchers.IO) { mutex.withLock {
        require(value.valid())
        val a = JSONArray()
        value.entries.forEach { a.put(JSONObject().put("app", it.platform.name).put("revenue", it.revenue).put("km", it.km).put("hours", it.hours)) }
        val j = JSONObject().put("entries", a).put("km", value.totalKm).put("hours", value.activeHours)
            .put("fixed", value.fixedCost).put("variable", value.variablePerKm).put("goal", value.grossGoal)
        check(prefs.edit().putString("day:${value.date}", j.toString()).commit()) { "Não foi possível salvar o dia." }
        _days.value = (_days.value.filterNot { it.date == value.date } + value).sortedByDescending { it.date }
    } }

    suspend fun deleteDay(date: LocalDate) = withContext(Dispatchers.IO) { mutex.withLock {
        check(prefs.edit().remove("day:$date").commit()) { "Não foi possível excluir o dia." }
        _days.value = _days.value.filterNot { it.date == date }
    } }
}
