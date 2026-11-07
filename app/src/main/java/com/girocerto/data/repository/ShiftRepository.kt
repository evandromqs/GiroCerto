package com.girocerto.data.repository

import android.content.Context
import com.girocerto.domain.model.ShiftRecord
import com.girocerto.domain.repository.ShiftTrackerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.LocalDate

class ShiftRepository(context: Context) : ShiftTrackerRepository {
    private val prefs = context.applicationContext.getSharedPreferences("work_shift_prefs", Context.MODE_PRIVATE)

    private val _currentShift = MutableStateFlow<ShiftRecord?>(null)
    override val currentShift: StateFlow<ShiftRecord?> = _currentShift.asStateFlow()

    init {
        loadShift()
    }

    private fun loadShift() {
        val raw = prefs.getString("active_shift", null) ?: return
        try {
            val json = JSONObject(raw)
            val shift = ShiftRecord(
                date = LocalDate.parse(json.getString("date")),
                startTime = json.getString("startTime"),
                startKm = if (json.has("startKm") && !json.isNull("startKm")) json.getDouble("startKm") else null,
                endTime = if (json.has("endTime") && !json.isNull("endTime")) json.getString("endTime") else null,
                endKm = if (json.has("endKm") && !json.isNull("endKm")) json.getDouble("endKm") else null,
                isActive = json.optBoolean("isActive", true)
            )
            _currentShift.value = shift
        } catch (_: Exception) {
            _currentShift.value = null
        }
    }

    override fun startShift(time: String, km: Double?): ShiftRecord {
        val shift = ShiftRecord(
            date = LocalDate.now(),
            startTime = time,
            startKm = km,
            isActive = true
        )
        save(shift)
        return shift
    }

    override fun endShift(time: String, km: Double?): ShiftRecord? {
        val active = _currentShift.value ?: return null
        val finished = active.copy(
            endTime = time,
            endKm = km,
            isActive = false
        )
        save(finished)
        return finished
    }

    override fun resetShift() {
        prefs.edit().remove("active_shift").apply()
        _currentShift.value = null
    }

    private fun save(shift: ShiftRecord) {
        val json = JSONObject().apply {
            put("date", shift.date.toString())
            put("startTime", shift.startTime)
            if (shift.startKm != null) put("startKm", shift.startKm)
            if (shift.endTime != null) put("endTime", shift.endTime)
            if (shift.endKm != null) put("endKm", shift.endKm)
            put("isActive", shift.isActive)
        }
        prefs.edit().putString("active_shift", json.toString()).apply()
        _currentShift.value = shift
    }
}
