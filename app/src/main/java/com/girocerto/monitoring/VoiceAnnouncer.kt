package com.girocerto.monitoring

import android.content.Context
import android.speech.tts.TextToSpeech
import com.girocerto.app.GiroCertoApp
import com.girocerto.domain.model.RideAnalysis
import com.girocerto.domain.model.TrafficLightStatus
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Sintetizador de voz nativo (Text-To-Speech) em Português do Brasil para alertar o motorista
 * sobre os dados cruciais da corrida sem desviar o olhar do trânsito.
 *
 * Respeita as preferências de voz definidas pelo motorista em [VehicleSettings.isVoiceEnabled]
 * e [VehicleSettings.speechRate].
 */
class VoiceAnnouncer(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.forLanguageTag("pt-BR"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = true
                // Taxa de fala padrão — será sobrescrita em speakAnalysis() com a preferência do motorista
                tts?.setSpeechRate(1.1f)
                tts?.setPitch(1.0f)
            }
        }
    }

    /**
     * Anuncia a análise da oferta de forma concisa e objetiva, respeitando configurações do motorista.
     * Exemplo: "Uber. Vinte e oito reais. Três e dez por quilômetro. Sinal verde."
     */
    fun speakAnalysis(analysis: RideAnalysis) {
        if (!isInitialized || tts == null) return

        // Verifica preferência de voz do motorista em tempo real
        val settings = try {
            GiroCertoApp.instance.settingsRepository.settings.value
        } catch (_: Exception) {
            null
        }

        if (settings?.isVoiceEnabled == false) return

        // Aplica velocidade de fala configurada pelo motorista (padrão: 1.1x)
        val speechRate = settings?.speechRate ?: 1.1f
        tts?.setSpeechRate(speechRate)

        val appName = resolveAppNameForSpeech(analysis.offer.appName)
        val fareFormatted = formatCurrencyForSpeech(analysis.offer.fare)
        val kmRateFormatted = formatRateForSpeech(analysis.grossPerKm)

        val statusText = when (analysis.trafficLight) {
            TrafficLightStatus.GREEN -> "Compensa. Dentro das metas."
            TrafficLightStatus.YELLOW -> "Atenção. Confira as metas."
            TrafficLightStatus.RED -> "Não compensa. Abaixo das metas."
            TrafficLightStatus.INVALID -> "Dados insuficientes."
        }

        val durationNote = if (analysis.offer.durationMinutes <= 0) " Tempo não informado." else ""
        val textToSpeak = "$appName. $fareFormatted. $kmRateFormatted por quilômetro. $statusText$durationNote"
        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "girocerto_offer_${analysis.id}")
    }

    private fun resolveAppNameForSpeech(appName: String): String {
        return when (appName.lowercase(Locale.ROOT)) {
            "uber" -> "Uber"
            "uber moto" -> "Uber Moto"
            "uber eats" -> "Uber Eats"
            "99" -> "Noventa e nove"
            "99food" -> "Noventa e nove Fud"
            "didi" -> "Di Di"
            "indrive" -> "In drive"
            "ifood" -> "I fud"
            "keeta" -> "Kíta"
            else -> appName.ifBlank { "Nova oferta" }
        }
    }

    private fun formatCurrencyForSpeech(value: Double): String {
        val totalCents = (value * 100).roundToInt()
        val reais = totalCents / 100
        val centavos = totalCents % 100

        return when {
            reais > 0 && centavos > 0 -> "$reais reais e $centavos centavos"
            reais > 0 -> "$reais reais"
            centavos > 0 -> "$centavos centavos"
            else -> "zero reais"
        }
    }

    private fun formatRateForSpeech(rate: Double): String {
        val totalCents = (rate * 100).roundToInt()
        val reais = totalCents / 100
        val centavos = totalCents % 100

        return if (reais > 0 && centavos > 0) {
            "$reais e ${String.format(Locale.ROOT, "%02d", centavos)}"
        } else if (reais > 0) {
            "$reais reais"
        } else {
            "$centavos centavos"
        }
    }

    /**
     * Libera os recursos do motor TextToSpeech ao encerrar.
     */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (_: Exception) {}
    }
}

