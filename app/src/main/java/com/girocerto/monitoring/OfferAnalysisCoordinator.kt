package com.girocerto.monitoring

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import com.girocerto.domain.model.RideAnalysis
import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.TrafficLightStatus
import com.girocerto.domain.repository.RideHistoryRepository
import com.girocerto.domain.repository.VehicleSettingsRepository
import com.girocerto.domain.usecase.AnalyzeRideOffer
import com.girocerto.domain.usecase.OfferEvaluationPolicy
import com.girocerto.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Coordinates an already parsed offer with domain analysis and Android side effects. */
internal class OfferAnalysisCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val screenshot: OfferScreenshot,
    private val voice: () -> VoiceAnnouncer?,
    private val reject: (String) -> Boolean,
    private val report: (String) -> Unit,
    private val recordFailure: (String, Exception) -> Unit,
    private val publishAnalysis: (RideAnalysis) -> Unit,
    private val settingsRepository: VehicleSettingsRepository,
    private val rideHistoryRepository: RideHistoryRepository,
    private val analyze: AnalyzeRideOffer = AnalyzeRideOffer()
) {
    private val savedOffers = mutableMapOf<RideOffer, Long>()

    fun process(result: OfferReaderResult, packageName: String) {
        val offer = result.toOffer(packageName, CopilotPreferences(context).read()) ?: return
        scope.launch {
            try {
                val settings = settingsRepository.settings.value
                val policy = CopilotPreferences(context).read()
                if (!policy.monitors(packageName)) return@launch

                val analysis = analyze(
                    offer = offer,
                    settings = settings,
                    policy = OfferEvaluationPolicy(policy.minimumPassengerRating)
                )
                publishAnalysis(analysis)
                if (policy.captureOfferScreenshot) screenshot.capture(offer, packageName)

                val overlayStatus = when {
                    OverlayController.hidden(context) -> "janela ocultada; mostre novamente na tela Copiloto"
                    !OverlayController.canDrawOverlays(context) -> "falta permissão de sobreposição"
                    OverlayController.showOverlay(context, analysis) -> "janela solicitada"
                    else -> "Android recusou iniciar a janela flutuante"
                }

                if (policy.speaks(analysis.trafficLight)) voice()?.speakAnalysis(analysis)
                if (policy.notifyOffers) OfferNotifications.publish(context, analysis)
                if (policy.autoRejectLoss && analysis.trafficLight == TrafficLightStatus.RED) {
                    scope.launch {
                        delay(AUTO_REJECT_DELAY_MILLIS)
                        if (reject(packageName)) report("${result.appName}: oferta com prejuízo recusada automaticamente")
                    }
                }
                if (policy.autoSave) saveOnce(offer, analysis)
                report("${result.appName}: ganhos calculados • $overlayStatus")
            } catch (error: Exception) {
                recordFailure("Exibição da análise", error)
                report("Não foi possível exibir esta análise; aguardando nova leitura")
            }
        }
    }

    private suspend fun saveOnce(offer: RideOffer, analysis: RideAnalysis) {
        val now = SystemClock.elapsedRealtime()
        savedOffers.entries.removeAll { now - it.value >= SAVED_OFFER_TTL_MILLIS }
        if (savedOffers.containsKey(offer)) return
        savedOffers[offer] = now
        try {
            rideHistoryRepository.saveRide(analysis)
        } catch (error: java.io.IOException) {
            savedOffers.remove(offer)
            Toast.makeText(context, "Não foi possível salvar a oferta.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun OfferReaderResult.toOffer(packageName: String, policy: CopilotPolicy): RideOffer? {
        val readFare = fare ?: return null
        val readDistance = distanceKm ?: return null
        val originalDuration = durationMinutes ?: 0
        val adjustedDuration = policy.adjustedDuration(originalDuration, packageName)
        return RideOffer(
            fare = readFare,
            distanceKm = readDistance,
            durationMinutes = adjustedDuration,
            pickupDistanceKm = pickupDistanceKm ?: 0.0,
            appName = appName,
            destinationAddress = destination.orEmpty(),
            pickupAddress = pickupAddress.orEmpty(),
            passengerRating = passengerRating,
            sourcePackage = packageName,
            addedMinutes = adjustedDuration - originalDuration
        )
    }

    private companion object {
        const val AUTO_REJECT_DELAY_MILLIS = 800L
        const val SAVED_OFFER_TTL_MILLIS = 120_000L
    }
}
