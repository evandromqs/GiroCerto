package com.girocerto.data.repository

import android.content.Context
import com.girocerto.domain.model.*
import com.girocerto.domain.repository.DriverProfileRepository
import com.girocerto.domain.repository.WorkLedgerRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class WorkRepository(context: Context, deferLoading: Boolean = false) : WorkLedgerRepository {
    private val appContext = context.applicationContext
    private val prefs by lazy { context.getSharedPreferences("work_ledger_v1", Context.MODE_PRIVATE) }
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val damaged = mutableSetOf<String>()
    private val _loading = MutableStateFlow(true)
    override val loading = _loading.asStateFlow()
    private val _loadError = MutableStateFlow<String?>(null)
    override val loadError = _loadError.asStateFlow()
    private val _selectedPlatforms = MutableStateFlow(setOf(WorkPlatform.UBER, WorkPlatform.NINETY_NINE, WorkPlatform.IFOOD))
    override val selectedPlatforms = _selectedPlatforms.asStateFlow()
    private val _restoreRevision = MutableStateFlow(0L)
    override val restoreRevision = _restoreRevision.asStateFlow()
    private val _profile = MutableStateFlow<ElectricProfile?>(null)
    override val profile = _profile.asStateFlow()
    private val _days = MutableStateFlow<List<WorkDay>>(emptyList())
    override val days = _days.asStateFlow()

    init {
        if (deferLoading) scope.launch { load() } else load()
    }
    override suspend fun awaitLoaded() { ready.await() }
    private fun load() {
        try {
            damaged.clear()
            _selectedPlatforms.value = prefs.getStringSet("selected_platforms", null)?.mapNotNull { name ->
                WorkPlatform.entries.find { it.name == name }
            }?.toSet()?.takeIf { it.isNotEmpty() } ?: setOf(WorkPlatform.UBER, WorkPlatform.NINETY_NINE, WorkPlatform.IFOOD)
            _profile.value = readSafely("profile") { decodeProfile(it) }
            _days.value = prefs.all.keys.filter { it.startsWith("day:") }.mapNotNull { key ->
                readSafely(key) { decodeDay(LocalDate.parse(key.removePrefix("day:")), it) }
            }.sortedByDescending { it.date }
            _loadError.value = if (damaged.isEmpty()) null else
                "Há registros que não puderam ser lidos. Os originais foram preservados e não serão sobrescritos. Exporte o backup para recuperação."
        } catch (_: Exception) {
            damaged.add("*")
            _loadError.value = "Não foi possível abrir seus dados. Nenhum registro será alterado até a recuperação."
        } finally {
            _restoreRevision.value = runCatching { prefs.getLong("restore_revision", 0L) }.getOrDefault(_restoreRevision.value)
            _loading.value = false; ready.complete(Unit)
        }
    }
    private fun <T> readSafely(key: String, decode: (JSONObject) -> T): T? {
        if (!prefs.contains(key)) return null
        return try { decode(JSONObject(checkNotNull(prefs.getString(key, null)))) }
        catch (_: Exception) { damaged.add(key); null }
    }
    private fun writable(key: String) { check("*" !in damaged && key !in damaged) { "Registro protegido para recuperação. Exporte o backup original." } }
    override fun retryLoad() { scope.launch { ready.await(); mutex.withLock { _loading.value = true; load() } } }

    override suspend fun recordAcceptedRide(platform: WorkPlatform, fare: Double, distanceKm: Double) = withContext(Dispatchers.IO) {
        ready.await(); mutex.withLock {
            val today = LocalDate.now()
            val existing = _days.value.firstOrNull { it.date == today }
            val profile = _profile.value
            val entries = existing?.entries?.toMutableList() ?: mutableListOf()
            val idx = entries.indexOfFirst { it.platform == platform }
            if (idx >= 0) {
                val current = entries[idx]
                entries[idx] = current.copy(revenue = current.revenue + fare, km = current.km + distanceKm)
            } else {
                entries.add(PlatformWork(platform, revenue = fare, km = distanceKm, hours = 0.0))
            }
            val totalKm = (existing?.totalKm ?: 0.0) + distanceKm
            val goal = existing?.grossGoal ?: profile?.dailyGrossGoal ?: 0.0
            val fixed = existing?.fixedCost ?: profile?.dailyFixedCost ?: 0.0
            val variable = existing?.variablePerKm ?: profile?.variablePerKm ?: 0.0
            val activeHours = existing?.activeHours ?: 0.0
            val updatedDay = WorkDay(
                date = today,
                entries = entries,
                totalKm = totalKm,
                activeHours = activeHours,
                fixedCost = fixed,
                variablePerKm = variable,
                grossGoal = goal,
                weeklyRent = existing?.weeklyRent ?: profile?.weeklyRent
            )
            val key = "day:${updatedDay.date}"; writable(key)
            check(prefs.edit().putString(key, encodeDay(updatedDay).toString()).commit())
            _days.value = (_days.value.filterNot { it.date == updatedDay.date } + updatedDay).sortedByDescending { it.date }
            queueCloudBackup()
        }
    }

    override suspend fun saveSelectedPlatforms(platforms: Set<WorkPlatform>) = withContext(Dispatchers.IO) { ready.await(); mutex.withLock {
        require(platforms.isNotEmpty()); writable("selected_platforms")
        check(prefs.edit().putStringSet("selected_platforms", platforms.map { it.name }.toSet()).commit())
        _selectedPlatforms.value = platforms.toSet()
        queueCloudBackup()
    } }
    override suspend fun saveProfile(profile: ElectricProfile) = withContext(Dispatchers.IO) { ready.await(); mutex.withLock {
        require(profile.valid()); writable("profile")
        check(prefs.edit().putString("profile", encodeProfile(profile).toString()).commit())
        _profile.value = profile
        queueCloudBackup()
    } }
    override suspend fun saveDay(day: WorkDay) = withContext(Dispatchers.IO) { ready.await(); mutex.withLock {
        require(day.valid()); val key = "day:${day.date}"; writable(key)
        check(prefs.edit().putString(key, encodeDay(day).toString()).commit())
        _days.value = (_days.value.filterNot { it.date == day.date } + day).sortedByDescending { it.date }
        queueCloudBackup()
    } }
    override suspend fun deleteDay(date: LocalDate) = withContext(Dispatchers.IO) { ready.await(); mutex.withLock {
        writable("day:$date")
        check(prefs.edit().remove("day:$date").commit())
        _days.value = _days.value.filterNot { it.date == date }
        queueCloudBackup()
    } }
    override suspend fun backup(): String = withContext(Dispatchers.IO) { ready.await(); mutex.withLock {
        val records = JSONObject()
        prefs.all.filterKeys { it == "profile" || it.startsWith("day:") }.forEach { (k,v) -> records.put(k,v) }
        JSONObject().put("format", "girocerto-ledger").put("version", 1)
            .put("platforms", JSONArray(_selectedPlatforms.value.map { it.name })).put("records", records).toString(2)
    } }
    /** Fully validate before committing. Existing dates are replaced only after UI confirmation. */
    override suspend fun restoreBackup(text: String) = withContext(Dispatchers.IO) { ready.await(); mutex.withLock {
        require(text.length <= 5_000_000)
        val json = JSONObject(text)
        require(json.getString("format") == "girocerto-ledger" && json.getInt("version") == 1)
        val records = json.getJSONObject("records")
        val entries = records.keys().asSequence().associateWith { key ->
            val raw = records.getString(key)
            if (key == "profile") decodeProfile(JSONObject(raw))
            else { require(key.startsWith("day:")); decodeDay(LocalDate.parse(key.removePrefix("day:")), JSONObject(raw)) }
            writable(key); raw
        }
        val platforms = json.getJSONArray("platforms").let { a ->
            (0 until a.length()).mapNotNull { decodePlatform(a.getString(it)) }.toSet()
        }.ifEmpty { setOf(WorkPlatform.UBER, WorkPlatform.NINETY_NINE, WorkPlatform.IFOOD) }
        val editor = prefs.edit().putLong("restore_revision", _restoreRevision.value + 1).putStringSet("selected_platforms", platforms.map { it.name }.toSet())
        entries.forEach { (key,value) -> editor.putString(key,value) }
        check(editor.commit()); load()
        queueCloudBackup()
    } }
    private fun queueCloudBackup() {
        runCatching { com.girocerto.data.cloud.CloudBackupManager.enqueueSync(appContext) }
    }
    private fun decodeProfile(j: JSONObject) = ElectricProfile(
        j.getString("name"), j.getDouble("rent"), j.getDouble("kmCost"),
        j.getDouble("extra"), j.getInt("days"), j.getDouble("hours"), j.getDouble("km"), j.getDouble("goal"),
        runCatching { VehicleType.valueOf(j.optString("vehicle", VehicleType.ELECTRIC_MOTO.name)) }
            .getOrDefault(VehicleType.ELECTRIC_MOTO),
        j.optDouble("fuelPrice", 0.0), j.optDouble("fuelKm", 0.0), j.optDouble("weeklyFixed", 0.0),
        j.optString("vehicleName", "").take(40)
    ).also { require(it.valid()) }
    private fun encodeProfile(v: ElectricProfile) = JSONObject().put("name",v.name).put("rent",v.weeklyRent).put("kmCost",v.chargedPerKm)
        .put("extra",v.extraPerKm).put("days",v.workDays).put("hours",v.hoursPerDay).put("km",v.kmPerDay).put("goal",v.weeklyNetGoal)
        .put("vehicle",v.vehicleType.name).put("fuelPrice",v.fuelPricePerLiter).put("fuelKm",v.fuelConsumptionKmL)
        .put("weeklyFixed",v.weeklyFixedExpenses).put("vehicleName",v.vehicleName)
    private fun decodeDay(date: LocalDate, j: JSONObject): WorkDay {
        val a = j.getJSONArray("entries")
        val entries = (0 until a.length()).mapNotNull { i -> val r = a.getJSONObject(i)
            decodePlatform(r.getString("app"))?.let { PlatformWork(it,r.getDouble("revenue"),r.getDouble("km"),r.getDouble("hours")) } }
        return WorkDay(date, entries,j.getDouble("km"),j.getDouble("hours"),j.getDouble("fixed"),j.getDouble("variable"),j.getDouble("goal"),
            if (j.has("weeklyRent")) j.getDouble("weeklyRent") else null).also { require(it.valid()) }
    }
    private fun decodePlatform(name: String): WorkPlatform? = if (name == "QIDA") null else WorkPlatform.valueOf(name)
    private fun encodeDay(v: WorkDay): JSONObject {
        val a = JSONArray(); v.entries.forEach { a.put(JSONObject().put("app",it.platform.name).put("revenue",it.revenue).put("km",it.km).put("hours",it.hours)) }
        return JSONObject().put("entries",a).put("km",v.totalKm).put("hours",v.activeHours).put("fixed",v.fixedCost)
            .put("variable",v.variablePerKm).put("goal",v.grossGoal).apply { v.weeklyRent?.let { put("weeklyRent",it) } }
    }
}
