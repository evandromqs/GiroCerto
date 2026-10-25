package com.girocerto.monitoring

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.girocerto.app.GiroCertoApp
import com.girocerto.domain.calculator.RideCalculator
import com.girocerto.domain.calculator.TrafficLightEvaluator
import com.girocerto.domain.model.RideAnalysis
import com.girocerto.domain.model.RideOffer
import com.girocerto.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/**
 * Serviço de Acessibilidade central do Giro Certo.
 *
 * Responsável por:
 * 1. Detectar quando ofertas de corrida/entrega surgem na tela (Uber, 99, iFood, inDrive, Keeta).
 * 2. Extrair dados brutos da hierarquia de visualização sem interferir nas ações do usuário.
 * 3. Processar cálculos de rentabilidade instantaneamente.
 * 4. Exibir o card flutuante e emitir alerta de voz em menos de 1 segundo.
 */
class GiroCertoAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val savedOffers = mutableMapOf<RideOffer, Long>()
    private val screenshot by lazy { OfferScreenshot(this) }
    private val ocrDelegate = lazy { OfferOcrReader(this, ::reportReader, ::acceptParsedOffer) }
    private var voiceAnnouncer: VoiceAnnouncer? = null

    // Cache para evitar reprocessamento repetitivo da mesma oferta em intervalos curtos
    private val deliveryGate = OfferDeliveryGate()

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isServiceRunning.value = true
        voiceAnnouncer = VoiceAnnouncer(this)
        reportReader("Leitor conectado • aguardando oferta dos apps habilitados")

        OverlayController.connect(this)
        readHandler.removeCallbacks(scanVisibleWindows)
        readHandler.post(scanVisibleWindows)
    }

    private val readHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var retryRead: Runnable? = null
    private val scanVisibleWindows = object : Runnable {
        override fun run() {
            try {
                OverlayController.syncCopilot(this@GiroCertoAccessibilityService)
                val power = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val lock = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                if (power.isInteractive && !lock.isKeyguardLocked && CopilotPreferences(this@GiroCertoAccessibilityService).read().enabled) {
                    // Popup windows may belong to a driver app while another app has focus.
                    val packages = linkedMapOf<String, Int>()
                    windows.forEach { window ->
                        val root = window.root
                        val pkg = root?.packageName?.toString()
                        @Suppress("DEPRECATION")
                        root?.recycle()
                        if (pkg in TARGET_PACKAGES) packages[pkg!!] = window.id
                    }
                    packages.forEach { (pkg, id) -> readOfferWindow(pkg, id) }
                }
            } catch (error: Exception) { reportReader("Falha ao observar janelas: ${error.javaClass.simpleName}") }
            finally { readHandler.postDelayed(this, 1_500L) }
        }
    }
    private var lastFormatTrace: String? = null

    private fun reportReader(message: String) {
        if (_readerStatus.value != message) {
            _readerStatus.value = message
            OverlayController.updateReaderStatus(message)
            android.util.Log.i("GiroReader", message)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        if (!TARGET_PACKAGES.contains(packageName)) return
        if (!CopilotPreferences(this).read().monitors(packageName)) {
            reportReader("Monitoramento pausado para $packageName")
            return
        }
        retryRead?.let { readHandler.removeCallbacks(it) }
        // Content can arrive in several frames; retry a bounded read, without
        // retaining AccessibilityEvent or mixing text from different offers.
        val windowId = event.windowId
        if (!readOfferWindow(packageName, windowId, eventRoot(event, packageName))) {
            retryRead = Runnable {
                if (!readOfferWindow(packageName, windowId)) {
                    retryRead = Runnable { readOfferWindow(packageName, windowId) }
                    readHandler.postDelayed(retryRead!!, 700)
                }
            }
            readHandler.postDelayed(retryRead!!, 250)
        }
    }

    // Some floating windows expose their event source before appearing in getWindows().
    // Use only this event's own package; never retain nodes across asynchronous retries.
    private fun eventRoot(event: AccessibilityEvent, packageName: String): AccessibilityNodeInfo? {
        var node = event.source ?: return null
        if (node.packageName?.toString() != packageName) {
            @Suppress("DEPRECATION") node.recycle()
            return null
        }
        repeat(32) {
            val parent = node.parent ?: return node
            if (parent.packageName?.toString() != packageName) {
                @Suppress("DEPRECATION") parent.recycle()
                return node
            }
            @Suppress("DEPRECATION") node.recycle()
            node = parent
        }
        return node
    }

    private fun readOfferWindow(packageName: String, windowId: Int, sourceRoot: AccessibilityNodeInfo? = null): Boolean {
        if (!CopilotPreferences(this).read().monitors(packageName)) {
            @Suppress("DEPRECATION") sourceRoot?.recycle()
            return true
        }
        // Inspect the emitting application's window, including its offer popup,
        // rather than assuming the focused root belongs to that application.
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        sourceRoot?.let { candidates.add(it) }
        try {
            windows.sortedByDescending { it.id == windowId }.forEach { window ->
                val root = window.root ?: return@forEach
                if (root.packageName?.toString() == packageName) candidates.add(root)
                else { @Suppress("DEPRECATION") root.recycle() }
            }
            if (candidates.isEmpty()) {
                rootInActiveWindow?.let { root ->
                    if (root.packageName?.toString() == packageName) candidates.add(root)
                    else { @Suppress("DEPRECATION") root.recycle() }
                }
            }
            if (candidates.isEmpty()) {
                _readerObservation.value = "$packageName • evento recebido, janela sem texto acessível"
                reportReader("$packageName: aguardando janela legível")
                readImageFallback(packageName)
                return false
            }
            for (root in candidates) {
                val texts = mutableListOf<String>()
                collectAllTexts(root, texts)
                _readerObservation.value = "$packageName • ${texts.size} textos acessíveis • " +
                    java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                val parsed = AccessibilityOfferReader.parse(texts, packageName)
                if (!parsed.isComplete) {
                    if (parsed.distanceKm != null && parsed.durationMinutes != null && parsed.fare == null) {
                        // Only monetary/numeric tokens; never persist names, addresses or the full screen.
                        val tokens = texts.filter { text ->
                            text.length <= 40 && text.any { it.isDigit() } &&
                                text.all { it.isDigit() || it.isWhitespace() || it in "R\$BRLrbrl.,+-\u200E\u200F\u202A\u202C" }
                        }.take(12).joinToString(" | ")
                        if (tokens != lastFormatTrace) {
                            lastFormatTrace = tokens
                            android.util.Log.i("GiroReader", "Formato monetário ($packageName): " +
                                tokens.ifEmpty { "nenhum token monetário acessível" })
                        }
                    }
                    reportReader("${parsed.appName}: ${texts.size} textos; " +
                        if (texts.isEmpty()) "tela sem texto acessível" else "aguardando ${parsed.missingFields.joinToString()}")
                    continue
                }
                acceptParsedOffer(parsed, packageName)
                return true
            }
            readImageFallback(packageName)
            return false
        } catch (error: Exception) {
            reportReader("Falha de leitura: ${error.javaClass.simpleName}")
            return false
        } finally {
            candidates.forEach { @Suppress("DEPRECATION") it.recycle() }
        }
    }

    private fun readImageFallback(packageName: String) {
        if (CopilotPreferences(this).read().ocrEnabled) ocrDelegate.value.read(packageName)
        else reportReader("Oferta sem dados suficientes pelo texto. Ative Leitura local de imagem (OCR) no Copiloto para tentar ler o popup.")
    }

    private fun acceptParsedOffer(parsed: OfferReaderResult, packageName: String) {
        if (!CopilotPreferences(this).read().monitors(packageName)) return
        if (!deliveryGate.accepts(packageName, parsed, android.os.SystemClock.elapsedRealtime())) return
        reportReader("${parsed.appName}: oferta lida; calculando ganhos")
        processOffer(parsed, packageName)
    }

    private fun processOffer(result: OfferReaderResult, packageName: String) {
        val fare = result.fare ?: return
        val distanceKm = result.distanceKm ?: return
        val durationMinutes = result.durationMinutes ?: 0
        val pickupDistance = result.pickupDistanceKm ?: 0.0

        val offer = RideOffer(
            fare = fare,
            distanceKm = distanceKm,
            durationMinutes = durationMinutes,
            pickupDistanceKm = pickupDistance,
            appName = result.appName,
            destinationAddress = result.destination ?: "",
            pickupAddress = result.pickupAddress ?: "",
            passengerRating = result.passengerRating
        )

        serviceScope.launch {
            try {
                val app = GiroCertoApp.instance
                val settings = app.settingsRepository.settings.value
                val policy = CopilotPreferences(this@GiroCertoAccessibilityService).read()
                if (!policy.monitors(packageName)) return@launch

                // 1. Calcula a rentabilidade e semáforo através das fórmulas financeiras puras
                val calculated = RideCalculator.calculate(offer, settings)
                val finalAnalysis = if (policy.isBelowPreferredRating(offer.passengerRating)) {
                    calculated.copy(decisionReason = calculated.decisionReason + " • Nota do passageiro abaixo da sua preferência. Você decide.")
                } else calculated

                _lastAnalyzedRide.value = finalAnalysis
                if (policy.captureOfferScreenshot) screenshot.capture(offer, packageName)

                // 2. Exibe o card flutuante sobre a tela do aplicativo de transporte
                val overlayStatus = if (OverlayController.hidden(this@GiroCertoAccessibilityService)) {
                    "janela ocultada; mostre novamente na tela Copiloto"
                } else if (!OverlayController.canDrawOverlays(this@GiroCertoAccessibilityService)) {
                    "falta permissão de sobreposição"
                } else if (OverlayController.showOverlay(this@GiroCertoAccessibilityService, finalAnalysis)) {
                    "janela solicitada"
                } else {
                    "Android recusou iniciar a janela flutuante"
                }

                // 3. Anúncio por voz Text-To-Speech (se habilitado)
                if (policy.speaks(finalAnalysis.trafficLight)) voiceAnnouncer?.speakAnalysis(finalAnalysis)
                if (policy.notifyOffers) OfferNotifications.publish(this@GiroCertoAccessibilityService, finalAnalysis)
                if (policy.autoSave) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    savedOffers.entries.removeAll { now - it.value >= 120_000L }
                    if (!savedOffers.containsKey(offer)) {
                        // Reserve before suspension so concurrent accessibility events cannot duplicate it.
                        savedOffers[offer] = now
                        try { app.rideRepository.saveRide(finalAnalysis) }
                        catch (error: java.io.IOException) {
                            savedOffers.remove(offer)
                            android.widget.Toast.makeText(this@GiroCertoAccessibilityService, "Não foi possível salvar a oferta.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                reportReader("${result.appName}: ganhos calculados • $overlayStatus")
            } catch (error: Exception) { reportReader("Falha ao exibir análise: ${error.javaClass.simpleName}") }
        }
    }

    private fun collectAllTexts(node: AccessibilityNodeInfo?, texts: MutableList<String>) {
        if (node == null) return

        val text = node.text?.toString()?.trim()
        val contentDesc = node.contentDescription?.toString()?.trim()

        if (!text.isNullOrBlank()) {
            texts.add(text)
        }
        if (!contentDesc.isNullOrBlank() && contentDesc != text) {
            texts.add(contentDesc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllTexts(child, texts)
            @Suppress("DEPRECATION")
            child.recycle()
        }
    }

    override fun onInterrupt() {
        // Interrupção solicitada pelo sistema operacional
    }

    override fun onDestroy() {
        super.onDestroy()
        readHandler.removeCallbacks(scanVisibleWindows)
        OverlayController.disconnect()
        retryRead?.let { readHandler.removeCallbacks(it) }
        serviceScope.cancel()
        _isServiceRunning.value = false
        screenshot.close()
        if (ocrDelegate.isInitialized()) ocrDelegate.value.close()
        voiceAnnouncer?.shutdown()
        voiceAnnouncer = null
    }

    companion object {
        // Aplicativos suportados para leitura de ofertas
        val TARGET_PACKAGES = setOf(
            // Uber (motorista, moto e entregador)
            "com.ubercab.driver",
            "com.ubermoto.driver",
            "com.ubereats.driver",
            // 99 / DiDi (motorista e entregador)
            "com.taxis99",
            "com.app99.driver",
            "com.didiglobal.driver",
            "com.didiglobal.food.driver",
            // iFood para Entregadores
            "br.com.ifood.driver.app",
            "br.com.ifood.driver",
            // inDrive
            "sinet.startup.indriver",
            // Keeta
            "com.sankuai.meituan.dispatch.driver",
            "com.keeta.driver"
        )

        private val _readerObservation = MutableStateFlow("Nenhuma janela de app de trabalho identificada nesta sessão")
        val readerObservation: StateFlow<String> = _readerObservation.asStateFlow()

        private val _readerStatus = MutableStateFlow("Aguardando oferta real de um app habilitado")
        val readerStatus: StateFlow<String> = _readerStatus.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _lastAnalyzedRide = MutableStateFlow<RideAnalysis?>(null)
        val lastAnalyzedRide: StateFlow<RideAnalysis?> = _lastAnalyzedRide.asStateFlow()

        /**
         * Verifica se o serviço de acessibilidade está habilitado nas Configurações do Android.
         */
        fun isAccessibilityEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${GiroCertoAccessibilityService::class.java.canonicalName}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)

            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }
}


