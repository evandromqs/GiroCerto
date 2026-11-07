package com.girocerto.monitoring

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.girocerto.app.GiroCertoApp
import com.girocerto.domain.model.RideAnalysis
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
    private val screenshot by lazy { OfferScreenshot(this) }
    private val windowOwners = WindowOwnerHints()
    private val detector = AccessibilityOfferDetector()
    private val actions by lazy { AccessibilityActionExecutor(this, windowOwners, TARGET_PACKAGES) }
    private val analysisCoordinator by lazy {
        OfferAnalysisCoordinator(
            context = this,
            scope = serviceScope,
            screenshot = screenshot,
            voice = { voiceAnnouncer },
            reject = actions::reject,
            report = ::reportReader,
            recordFailure = ::recordFailure,
            publishAnalysis = { _lastAnalyzedRide.value = it },
            settingsRepository = GiroCertoApp.instance.settingsRepository,
            rideHistoryRepository = GiroCertoApp.instance.rideRepository
        )
    }
    private val ocrDelegate = lazy { OfferOcrReader(this, ::reportReader, ::acceptParsedOffer, windowOwners) }
    private var voiceAnnouncer: VoiceAnnouncer? = null

    // Cache para evitar reprocessamento repetitivo da mesma oferta em intervalos curtos
    private val deliveryGate = OfferDeliveryGate()

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isServiceRunning.value = true
        serviceInstance = this
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
                    windows.sortedByDescending { it.layer }.forEach { window ->
                        val root = window.root
                        val pkg = windowOwners.owner(window.id, root?.packageName?.toString(), android.os.SystemClock.elapsedRealtime())
                        @Suppress("DEPRECATION")
                        root?.recycle()
                        if (pkg in TARGET_PACKAGES) packages.putIfAbsent(pkg!!, window.id)
                    }
                    scanDelay = if (packages.isEmpty()) 2_000L else 1_000L
                    packages.forEach { (pkg, id) -> readOfferWindow(pkg, id) }
                }
            } catch (error: Exception) {
                recordFailure("Observação de janelas", error)
                reportReader("Janela temporariamente indisponível; tentando novamente")
            }
            finally { readHandler.postDelayed(this, scanDelay) }
        }
    }
    private val lastEventReads = mutableMapOf<String, Long>()
    private var scanDelay = 3_000L
    private var lastFormatTrace: String? = null

    private fun reportReader(message: String) {
        if (_readerStatus.value != message) {
            _readerStatus.value = message
            OverlayController.updateReaderStatus(message)
            android.util.Log.i("GiroReader", message)
        }
    }

    private fun recordFailure(stage: String, error: Exception) {
        android.util.Log.w("GiroReader", ReaderDiagnostics.record(stage, error))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            readHandler.removeCallbacks(scanVisibleWindows)
            readHandler.post(scanVisibleWindows)
        }
        val packageName = event.packageName?.toString() ?: return
        if (!TARGET_PACKAGES.contains(packageName)) return
        windowOwners.observe(event.windowId, packageName, android.os.SystemClock.elapsedRealtime())
        if (!CopilotPreferences(this).read().monitors(packageName)) {
            reportReader("Monitoramento pausado para $packageName")
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            readNotificationOffer(event, packageName)
            readHandler.removeCallbacks(scanVisibleWindows)
            readHandler.postDelayed(scanVisibleWindows, 100L)
            return
        }
        if (ocrDelegate.isInitialized() && event.eventType in listOf(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOWS_CHANGED)) ocrDelegate.value.invalidate(packageName)
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - (lastEventReads[packageName] ?: -500L) < 400L) return
        lastEventReads[packageName] = now
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

    /**
     * Driver apps may publish a heads-up notification while Maps, music or another app owns
     * the foreground. Notification text is attributed to the emitting package and is parsed
     * without reading unrelated notification content.
     */
    private fun readNotificationOffer(event: AccessibilityEvent, packageName: String) {
        val lines = mutableListOf<String>()
        lines += event.text.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
        val notification = event.parcelableData as? android.app.Notification
        notification?.extras?.let { extras ->
            listOf(
                android.app.Notification.EXTRA_TITLE,
                android.app.Notification.EXTRA_TEXT,
                android.app.Notification.EXTRA_BIG_TEXT,
                android.app.Notification.EXTRA_SUB_TEXT,
                android.app.Notification.EXTRA_SUMMARY_TEXT,
            ).mapNotNullTo(lines) { key -> extras.getCharSequence(key)?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)
                ?.mapNotNullTo(lines) { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
        }
        val distinct = lines.distinct().take(24)
        if (distinct.isEmpty()) return
        val parsed = AccessibilityOfferReader.parse(distinct, packageName)
        _readerObservation.value = "$packageName • notificação de oferta • ${distinct.size} textos"
        if (parsed.isComplete && OfferScreenContext.hasOfferAction(distinct)) {
            acceptParsedOffer(parsed, packageName)
        } else {
            reportReader("${parsed.appName}: popup detectado; aguardando dados completos da oferta")
        }
    }

    // Some floating windows expose their event source before appearing in getWindows().
    // Use only this event's own package; never retain nodes across asynchronous retries.
    private fun eventRoot(event: AccessibilityEvent, packageName: String): AccessibilityNodeInfo? {
        return try { detector.eventRoot(event, packageName) } catch (error: Exception) {
            recordFailure("Origem do evento", error)
            null
        }
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
            val visible = windows.sortedWith(compareByDescending<android.view.accessibility.AccessibilityWindowInfo> { it.id == windowId }.thenByDescending { it.layer })
            visible.forEach { window ->
                val root = window.root ?: return@forEach
                val owner = windowOwners.owner(window.id, root.packageName?.toString(), android.os.SystemClock.elapsedRealtime())
                if (owner == packageName || root.packageName?.toString() == packageName) candidates.add(root)
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
                readImageFallback(packageName, windowId)
                return false
            }
            for (root in candidates) {
                val texts = detector.collectTexts(root)
                if (texts == null) {
                    reportReader("Janela extensa: leitura parcial descartada; tentando OCR")
                    continue
                }
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
                if (!OfferScreenContext.hasOfferAction(texts)) {
                    reportReader("Valores lidos, mas sem confirmação de uma oferta na tela")
                    continue
                }
                if (ocrDelegate.isInitialized()) ocrDelegate.value.invalidate(packageName)
                acceptParsedOffer(parsed, packageName)
                return true
            }
            readImageFallback(packageName, windowId)
            return false
        } catch (error: Exception) {
            recordFailure("Leitura de texto", error)
            reportReader("Texto temporariamente indisponível; tentando leitura por imagem")
            readImageFallback(packageName, windowId)
            return false
        } finally {
            candidates.forEach { @Suppress("DEPRECATION") it.recycle() }
        }
    }

    private fun readImageFallback(packageName: String, windowId: Int) {
        try {
            if (CopilotPreferences(this).read().ocrEnabled) ocrDelegate.value.read(packageName, windowId)
            else reportReader("Oferta sem dados suficientes pelo texto. Ative Leitura local de imagem (OCR) no Copiloto para tentar ler o popup.")
        } catch (error: Exception) {
            recordFailure("Inicialização do OCR", error)
            reportReader("OCR temporariamente indisponível; nova tentativa automática")
        }
    }

    private fun acceptParsedOffer(parsed: OfferReaderResult, packageName: String) {
        if (!CopilotPreferences(this).read().monitors(packageName)) return
        if (!deliveryGate.accepts(packageName, parsed, android.os.SystemClock.elapsedRealtime())) return
        reportReader("${parsed.appName}: oferta lida; calculando ganhos")
        processOffer(parsed, packageName)
    }

    private fun processOffer(result: OfferReaderResult, packageName: String) {
        analysisCoordinator.process(result, packageName)
    }

    fun clickAcceptCurrentOffer(targetPackage: String = ""): Boolean {
        return actions.accept(targetPackage)
    }

    fun clickRejectCurrentOffer(targetPackage: String = ""): Boolean {
        return actions.reject(targetPackage)
    }

    fun clickDisconnectCurrentApp(targetPackage: String = ""): Boolean {
        return actions.disconnect(targetPackage)
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
        serviceInstance = null
        screenshot.close()
        if (ocrDelegate.isInitialized()) ocrDelegate.value.close()
        voiceAnnouncer?.shutdown()
        voiceAnnouncer = null
    }

    companion object {
        @Volatile
        var serviceInstance: GiroCertoAccessibilityService? = null

        fun requestAcceptOffer(targetPackage: String): Boolean =
            serviceInstance?.clickAcceptCurrentOffer(targetPackage) ?: false

        fun requestRejectOffer(targetPackage: String): Boolean =
            serviceInstance?.clickRejectCurrentOffer(targetPackage) ?: false

        fun requestDisconnect(targetPackage: String): Boolean =
            serviceInstance?.clickDisconnectCurrentApp(targetPackage) ?: false

        fun currentOfferPackage(): String = _lastAnalyzedRide.value?.offer?.sourcePackage.orEmpty()

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
            // Keeta e rede DiDi/Meituan
            "com.sankuai.meituan.dispatch.driver",
            "com.keeta.driver",
            "com.sankuai.sailor.courier"
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


