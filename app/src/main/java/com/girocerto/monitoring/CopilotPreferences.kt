package com.girocerto.monitoring

import android.content.Context
import com.girocerto.domain.model.TrafficLightStatus

import com.girocerto.overlay.OverlayCorner

data class CopilotPolicy(
    val enabled: Boolean = true,
    val voiceCommandsEnabled: Boolean = false,
    val uber: Boolean = true,
    val ninetyNine: Boolean = true,
    val ifood: Boolean = true,
    val inDrive: Boolean = true,
    val keeta: Boolean = true,
    val otherApps: Boolean = false,
    val speakGreen: Boolean = true,
    val speakYellow: Boolean = true,
    val speakRed: Boolean = true,
    val notifyOffers: Boolean = false,
    val minimumPassengerRating: Double = 0.0,
    val captureOfferScreenshot: Boolean = false,
    val ocrEnabled: Boolean = false,
    val autoSave: Boolean = false,
    val addFiveMinutes: Boolean = false,
    val autoRejectLoss: Boolean = false,
    val restingCorner: OverlayCorner = OverlayCorner.TOP_RIGHT
) {
    fun adjustedDuration(minutes: Int, packageName: String): Int {
        val supported = packageName.contains("uber") || packageName == "com.app99.driver" ||
            packageName == "com.taxis99" || packageName.startsWith("com.didiglobal")
        return if (addFiveMinutes && supported && minutes > 0) minutes + 5 else minutes
    }
    fun isBelowPreferredRating(rating: Double?): Boolean =
        minimumPassengerRating > 0.0 && rating != null && rating.isFinite() &&
            rating in 0.0..5.0 && rating < minimumPassengerRating

    fun monitors(packageName: String): Boolean = enabled && when {
        packageName.contains("uber") -> uber
        packageName == "com.app99.driver" || packageName.contains("taxis99") || packageName.contains("didiglobal") -> ninetyNine
        packageName.contains("ifood") -> ifood
        packageName.contains("indriver") -> inDrive
        packageName.contains("keeta") || packageName.contains("sankuai") || packageName.contains("meituan") -> keeta
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
            voiceCommandsEnabled = prefs.getBoolean("voice_commands", false),
            uber = prefs.getBoolean("uber", true),
            ninetyNine = prefs.getBoolean("99", true),
            ifood = prefs.getBoolean("ifood", true),
            inDrive = prefs.getBoolean("indrive", false),
            keeta = prefs.getBoolean("keeta", true),
            otherApps = prefs.getBoolean("others", false),
            speakGreen = prefs.getBoolean("green", true),
            speakYellow = prefs.getBoolean("yellow", true),
            speakRed = prefs.getBoolean("red", true),
            notifyOffers = prefs.getBoolean("notifications", false),
            minimumPassengerRating = prefs.getString("minimum_rating", "0.0")?.toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceIn(0.0, 5.0) ?: 0.0,
            captureOfferScreenshot = prefs.getBoolean("capture_screenshot", false),
            ocrEnabled = prefs.getBoolean("ocr_enabled", false),
            autoSave = prefs.getBoolean("autosave", false),
            addFiveMinutes = prefs.getBoolean("add_five_minutes", false),
            autoRejectLoss = prefs.getBoolean("auto_reject_loss", false),
            restingCorner = corner
        )
    }
    fun write(policy: CopilotPolicy) {
        prefs.edit().putBoolean("enabled", policy.enabled).putBoolean("uber", policy.uber)
            .putBoolean("voice_commands", policy.voiceCommandsEnabled)
            .putBoolean("99", policy.ninetyNine).putBoolean("ifood", policy.ifood)
            .putBoolean("indrive", policy.inDrive).putBoolean("keeta", policy.keeta)
            .putBoolean("others", policy.otherApps)
            .putBoolean("green", policy.speakGreen)
            .putBoolean("yellow", policy.speakYellow).putBoolean("red", policy.speakRed)
            .putString("minimum_rating", policy.minimumPassengerRating.toString())
            .putBoolean("capture_screenshot", policy.captureOfferScreenshot)
            .putBoolean("ocr_enabled", policy.ocrEnabled)
            .putBoolean("add_five_minutes", policy.addFiveMinutes)
            .putBoolean("auto_reject_loss", policy.autoRejectLoss)
            .putBoolean("notifications", policy.notifyOffers).putBoolean("autosave", policy.autoSave)
            .putString("resting_corner", policy.restingCorner.name).apply()
        com.girocerto.overlay.OverlayController.syncCopilot(context)
    }
}
