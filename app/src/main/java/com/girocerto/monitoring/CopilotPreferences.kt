package com.girocerto.monitoring

import android.content.Context
import com.girocerto.domain.model.TrafficLightStatus

import com.girocerto.overlay.OverlayCorner

data class CopilotPolicy(
    val enabled: Boolean = true,
    val uber: Boolean = true,
    val ninetyNine: Boolean = true,
    val inDrive: Boolean = true,
    val otherApps: Boolean = true,
    val speakGreen: Boolean = true,
    val speakYellow: Boolean = true,
    val speakRed: Boolean = true,
    val notifyOffers: Boolean = false,
    val minimumPassengerRating: Double = 0.0,
    val captureOfferScreenshot: Boolean = false,
    val ocrEnabled: Boolean = false,
    val autoSave: Boolean = false,
    val restingCorner: OverlayCorner = OverlayCorner.TOP_RIGHT
) {
    fun isBelowPreferredRating(rating: Double?): Boolean =
        minimumPassengerRating > 0.0 && rating != null && rating.isFinite() &&
            rating in 0.0..5.0 && rating < minimumPassengerRating

    fun monitors(packageName: String): Boolean = enabled && when {
        packageName.contains("uber") -> uber
        packageName == "com.app99.driver" || packageName.contains("taxis99") || packageName.contains("didiglobal") -> ninetyNine
        packageName.contains("indriver") -> inDrive
        else -> otherApps
    }
    fun speaks(status: TrafficLightStatus): Boolean = enabled && when (status) {
        TrafficLightStatus.GREEN -> speakGreen
        TrafficLightStatus.YELLOW -> speakYellow
        TrafficLightStatus.RED -> speakRed
        TrafficLightStatus.INVALID -> false
    }
}

class CopilotPreferences(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("copilot_preferences", Context.MODE_PRIVATE)
    fun read(): CopilotPolicy {
        val cornerStr = prefs.getString("resting_corner", OverlayCorner.TOP_RIGHT.name) ?: OverlayCorner.TOP_RIGHT.name
        val corner = runCatching { OverlayCorner.valueOf(cornerStr) }.getOrDefault(OverlayCorner.TOP_RIGHT)
        return CopilotPolicy(
            enabled = prefs.getBoolean("enabled", true),
            uber = prefs.getBoolean("uber", true),
            ninetyNine = prefs.getBoolean("99", true),
            inDrive = prefs.getBoolean("indrive", true),
            otherApps = prefs.getBoolean("others", true),
            speakGreen = prefs.getBoolean("green", true),
            speakYellow = prefs.getBoolean("yellow", true),
            speakRed = prefs.getBoolean("red", true),
            notifyOffers = prefs.getBoolean("notifications", false),
            minimumPassengerRating = prefs.getString("minimum_rating", "0.0")?.toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceIn(0.0, 5.0) ?: 0.0,
            captureOfferScreenshot = prefs.getBoolean("capture_screenshot", false),
            ocrEnabled = prefs.getBoolean("ocr_enabled", false),
            autoSave = prefs.getBoolean("autosave", false),
            restingCorner = corner
        )
    }
    fun write(policy: CopilotPolicy) {
        prefs.edit().putBoolean("enabled", policy.enabled).putBoolean("uber", policy.uber)
            .putBoolean("99", policy.ninetyNine).putBoolean("indrive", policy.inDrive)
            .putBoolean("others", policy.otherApps).putBoolean("green", policy.speakGreen)
            .putBoolean("yellow", policy.speakYellow).putBoolean("red", policy.speakRed)
            .putString("minimum_rating", policy.minimumPassengerRating.toString())
            .putBoolean("capture_screenshot", policy.captureOfferScreenshot)
            .putBoolean("ocr_enabled", policy.ocrEnabled)
            .putBoolean("notifications", policy.notifyOffers).putBoolean("autosave", policy.autoSave)
            .putString("resting_corner", policy.restingCorner.name).apply()
        com.girocerto.overlay.OverlayController.syncCopilot(context)
    }
}
