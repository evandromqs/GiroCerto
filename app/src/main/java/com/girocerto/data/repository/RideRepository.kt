package com.girocerto.data.repository

import android.content.Context
import android.util.AtomicFile
import com.girocerto.domain.model.DailySummary
import com.girocerto.domain.model.RideAnalysis
import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.TrafficLightStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * Repositório responsável por persistir e gerenciar o histórico de corridas analisadas.
 */
class RideRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val historyFile: File by lazy {
        File(context.filesDir, "girocerto_rides_history.json")
    }

    private val writeMutex = Mutex()
    private val atomicHistory by lazy { AtomicFile(historyFile) }

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()

    private val _rides = MutableStateFlow<List<RideAnalysis>>(emptyList())
    val rides: StateFlow<List<RideAnalysis>> = _rides.asStateFlow()

    init {
        loadHistory()
    }

    fun retryLoad() { _loadError.value = null; loadHistory() }

    private fun loadHistory() {
        try {
            if (!historyFile.exists() && !File(historyFile.path + ".bak").exists()) {
                _rides.value = emptyList()
                return
            }
            val content = atomicHistory.openRead().bufferedReader().use { it.readText() }
            if (content.isBlank()) {
                _rides.value = emptyList()
                return
            }
            val jsonArray = JSONArray(content)
            val list = mutableListOf<RideAnalysis>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(fromJson(obj))
            }
            _rides.value = list.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            _loadError.value = "Não foi possível abrir o histórico. O arquivo original foi preservado; novos salvamentos estão bloqueados."
            _rides.value = emptyList()
        }
    }

    private fun persistToFile(items: List<RideAnalysis>) {
        if (_loadError.value != null) throw java.io.IOException("Histórico indisponível; original preservado")
        val jsonArray = JSONArray()
        items.forEach { jsonArray.put(toJson(it)) }
        val bytes = jsonArray.toString().toByteArray(Charsets.UTF_8)
        val stream = atomicHistory.startWrite()
        try {
            stream.write(bytes)
            atomicHistory.finishWrite(stream)
        } catch (error: Exception) {
            atomicHistory.failWrite(stream)
            throw error
        }
    }

    /** Publish changes only after persistence succeeds; serialize the entire transaction. */
    suspend fun saveRide(analysis: RideAnalysis): RideAnalysis = withContext(ioDispatcher) {
        writeMutex.withLock {
            val highestId = _rides.value.maxOfOrNull { it.id } ?: 0L
            val assignedId = if (analysis.id > 0 && _rides.value.none { it.id == analysis.id }) {
                analysis.id
            } else {
                maxOf(System.currentTimeMillis(), highestId + 1)
            }
            val saved = analysis.copy(id = assignedId)
            val updated = listOf(saved) + _rides.value
            persistToFile(updated)
            _rides.value = updated
            saved
        }
    }

    suspend fun deleteRide(id: Long) = withContext(ioDispatcher) {
        writeMutex.withLock {
            val updated = _rides.value.filterNot { it.id == id }
            persistToFile(updated)
            _rides.value = updated
        }
    }

    suspend fun clearHistory() = withContext(ioDispatcher) {
        writeMutex.withLock {
            persistToFile(emptyList())
            _rides.value = emptyList()
        }
    }

    /**
     * Calcula o resumo das corridas analisadas hoje.
     */
    fun getTodaySummary(dailyFixedCost: Double = 0.0): DailySummary {
        val todayCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = todayCalendar.timeInMillis

        val endOfDay = (todayCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        val todayRides = _rides.value.filter { it.timestamp >= startOfDay && it.timestamp < endOfDay }
        if (todayRides.isEmpty()) {
            return DailySummary()
        }

        val totalRides = todayRides.size
        val totalProfit = todayRides.sumOf { it.estimatedProfit } -
            (dailyFixedCost.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0)
        val totalFare = todayRides.sumOf { it.offer.fare }
        val totalKm = todayRides.sumOf { it.totalDistanceKm }
        val avgGrossPerKm = if (totalKm > 0) totalFare / totalKm else 0.0

        val greenCount = todayRides.count { it.trafficLight == TrafficLightStatus.GREEN }
        val yellowCount = todayRides.count { it.trafficLight == TrafficLightStatus.YELLOW }
        val redCount = todayRides.count { it.trafficLight == TrafficLightStatus.RED }

        return DailySummary(
            totalRides = totalRides,
            totalEstimatedProfit = totalProfit,
            totalGrossFare = totalFare,
            totalDistanceKm = totalKm,
            averageGrossPerKm = avgGrossPerKm,
            greenCount = greenCount,
            yellowCount = yellowCount,
            redCount = redCount
        )
    }

    private fun toJson(item: RideAnalysis): JSONObject {
        val obj = JSONObject()
        obj.put("id", item.id)
        obj.put("timestamp", item.timestamp)
        obj.put("fare", item.offer.fare)
        obj.put("distanceKm", item.offer.distanceKm)
        obj.put("durationMinutes", item.offer.durationMinutes)
        obj.put("pickupDistanceKm", item.offer.pickupDistanceKm)
        obj.put("appName", item.offer.appName)
        obj.put("destinationAddress", item.offer.destinationAddress)
        obj.put("pickupAddress", item.offer.pickupAddress)
        obj.put("category", item.offer.category)
        if (item.offer.passengerRating != null) {
            obj.put("passengerRating", item.offer.passengerRating)
        }
        obj.put("totalDistanceKm", item.totalDistanceKm)
        obj.put("grossPerKm", item.grossPerKm)
        obj.put("grossPerHour", item.grossPerHour)
        obj.put("fuelCost", item.fuelCost)
        obj.put("additionalOperationalCost", item.additionalOperationalCost)
        obj.put("fixedCost", item.fixedCost)
        obj.put("totalCost", item.totalCost)
        obj.put("estimatedProfit", item.estimatedProfit)
        obj.put("estimatedMarginPercent", item.estimatedMarginPercent)
        obj.put("trafficLight", item.trafficLight.name)
        obj.put("decisionReason", item.decisionReason)
        return obj
    }

    private fun fromJson(obj: JSONObject): RideAnalysis {
        val offer = RideOffer(
            fare = obj.optDouble("fare", 0.0),
            distanceKm = obj.optDouble("distanceKm", 0.0),
            durationMinutes = obj.optInt("durationMinutes", 0),
            pickupDistanceKm = obj.optDouble("pickupDistanceKm", 0.0),
            appName = obj.optString("appName", ""),
            destinationAddress = obj.optString("destinationAddress", ""),
            pickupAddress = obj.optString("pickupAddress", ""),
            category = obj.optString("category", "Corrida"),
            passengerRating = if (obj.has("passengerRating")) obj.optDouble("passengerRating") else null
        )
        val statusName = obj.optString("trafficLight", TrafficLightStatus.INVALID.name)
        val status = try {
            TrafficLightStatus.valueOf(statusName)
        } catch (_: Exception) {
            TrafficLightStatus.INVALID
        }

        return RideAnalysis(
            id = obj.optLong("id", 0L),
            timestamp = obj.optLong("timestamp", 0L),
            offer = offer,
            totalDistanceKm = obj.optDouble("totalDistanceKm", 0.0),
            grossPerKm = obj.optDouble("grossPerKm", 0.0),
            grossPerHour = obj.optDouble("grossPerHour", 0.0),
            fuelCost = obj.optDouble("fuelCost", 0.0),
            additionalOperationalCost = obj.optDouble("additionalOperationalCost", 0.0),
            fixedCost = obj.optDouble("fixedCost", 0.0),
            totalCost = obj.optDouble("totalCost", 0.0),
            estimatedProfit = obj.optDouble("estimatedProfit", 0.0),
            estimatedMarginPercent = obj.optDouble("estimatedMarginPercent", 0.0),
            trafficLight = status,
            decisionReason = obj.optString("decisionReason", "")
        )
    }
}
