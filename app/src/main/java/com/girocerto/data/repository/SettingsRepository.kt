package com.girocerto.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.girocerto.domain.model.FuelType
import com.girocerto.domain.model.VehicleSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repositório responsável por armazenar e fornecer as configurações do veículo e metas do motorista.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<VehicleSettings> = _settings.asStateFlow()

    private fun loadSettings(): VehicleSettings {
        val fuelTypeName = prefs.getString(KEY_FUEL_TYPE, FuelType.GASOLINA.name) ?: FuelType.GASOLINA.name
        val fuelType = try {
            FuelType.valueOf(fuelTypeName)
        } catch (_: Exception) {
            FuelType.GASOLINA
        }

        return VehicleSettings(
            fuelType = fuelType,
            fuelPricePerLiter = readDecimal(KEY_FUEL_PRICE, 5.80),
            fuelConsumptionKmL = readDecimal(KEY_FUEL_CONSUMPTION, 10.0),
            additionalCostPerKm = readDecimal(KEY_ADDITIONAL_COST, 0.30),
            minTargetPerKm = readDecimal(KEY_MIN_TARGET_KM, 2.00),
            minTargetPerHour = readDecimal(KEY_MIN_TARGET_HOUR, 35.00),
            fixedCostPerRide = readDecimal(KEY_FIXED_COST, 0.00),
            dailyFixedCost = readDecimal(KEY_DAILY_FIXED_COST, 0.00),
            isVoiceEnabled = prefs.getBoolean(KEY_VOICE_ENABLED, true),
            speechRate = prefs.getFloat(KEY_SPEECH_RATE, 1.1f),
            cardDurationSeconds = prefs.getInt(KEY_CARD_DURATION, 18),
            isFloatingBubbleEnabled = prefs.getBoolean(KEY_FLOATING_BUBBLE, true)
        )
    }

    /**
     * Atualiza e persiste as novas configurações do motorista.
     */
    fun updateSettings(newSettings: VehicleSettings) {
        prefs.edit()
            .putString(KEY_FUEL_TYPE, newSettings.fuelType.name)
            .putString(KEY_FUEL_PRICE, newSettings.fuelPricePerLiter.toString())
            .putString(KEY_FUEL_CONSUMPTION, newSettings.fuelConsumptionKmL.toString())
            .putString(KEY_ADDITIONAL_COST, newSettings.additionalCostPerKm.toString())
            .putString(KEY_MIN_TARGET_KM, newSettings.minTargetPerKm.toString())
            .putString(KEY_MIN_TARGET_HOUR, newSettings.minTargetPerHour.toString())
            .putString(KEY_FIXED_COST, newSettings.fixedCostPerRide.toString())
            .putString(KEY_DAILY_FIXED_COST, newSettings.dailyFixedCost.toString())
            .putBoolean(KEY_VOICE_ENABLED, newSettings.isVoiceEnabled)
            .putFloat(KEY_SPEECH_RATE, newSettings.speechRate)
            .putInt(KEY_CARD_DURATION, newSettings.cardDurationSeconds)
            .putBoolean(KEY_FLOATING_BUBBLE, newSettings.isFloatingBubbleEnabled)
            .apply()

        _settings.value = newSettings
    }

    // Decode old Float preferences through their decimal text to avoid precision artifacts.
    private fun readDecimal(key: String, default: Double): Double =
        StoredDecimal.decode(prefs.all[key], default)

    companion object {
        private const val PREFS_NAME = "girocerto_vehicle_settings"
        private const val KEY_FUEL_TYPE = "fuel_type"
        private const val KEY_FUEL_PRICE = "fuel_price"
        private const val KEY_FUEL_CONSUMPTION = "fuel_consumption"
        private const val KEY_ADDITIONAL_COST = "additional_cost"
        private const val KEY_MIN_TARGET_KM = "min_target_km"
        private const val KEY_MIN_TARGET_HOUR = "min_target_hour"
        private const val KEY_FIXED_COST = "fixed_cost"
        private const val KEY_DAILY_FIXED_COST = "daily_fixed_cost"
        private const val KEY_VOICE_ENABLED = "voice_enabled"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_CARD_DURATION = "card_duration"
        private const val KEY_FLOATING_BUBBLE = "floating_bubble_enabled"
    }
}
