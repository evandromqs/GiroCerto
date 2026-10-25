package com.girocerto.overlay

import android.app.Service
import android.util.Log
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.girocerto.app.GiroCertoApp
import com.girocerto.app.MainActivity
import com.girocerto.domain.calculator.RideCalculator
import com.girocerto.domain.model.RideAnalysis
import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.TrafficLightStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/**
 * Serviço de Janela Flutuante (Overlay) do Giro Certo.
 *
 * Suporta três modos visuais:
 * 1. [Mode.BUBBLE]: Botão flutuante compacto e permanente na lateral da tela ("GC" com indicador verde).
 * 2. [Mode.OFFER_CARD]: Card completo de análise expandido automaticamente ao receber oferta da Uber, 99, iFood, etc.
 * 3. [Mode.QUICK_MENU]: Painel rápido de status e atalhos exibido ao tocar na bolha.
 */
class RideOverlayService : Service() {

    private enum class Mode {
        BUBBLE,
        OFFER_CARD,
        QUICK_MENU
    }

    private var windowManager: WindowManager? = null
    private var currentView: View? = null
    private var currentMode: Mode = Mode.BUBBLE
    private var params: WindowManager.LayoutParams? = null

    // Posição na tela (persistida durante a sessão)
    private var posX: Int = 30
    private var posY: Int = 300

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private var currentAnalysis: RideAnalysis? = null

    private val autoCollapseRunnable = Runnable {
        val settings = GiroCertoApp.instance.settingsRepository.settings.value
        if (settings.isFloatingBubbleEnabled) {
            switchToBubble()
        } else {
            removeOverlay()
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Log.i("GiroOverlay", "Overlay service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_STOP -> {
                removeOverlay()
                stopSelf()
            }
            ACTION_START_BUBBLE -> {
                showBubbleMode()
            }
            ACTION_SHOW -> {
                Log.i("GiroOverlay", "Offer service request received")
                val kmRate = intent.getDoubleExtra(EXTRA_KM_RATE, 0.0)
                val hourRate = intent.getDoubleExtra(EXTRA_HOUR_RATE, 0.0)
                val profit = intent.getDoubleExtra(EXTRA_PROFIT, 0.0)
                val statusName = intent.getStringExtra(EXTRA_STATUS) ?: TrafficLightStatus.INVALID.name
                val status = try {
                    TrafficLightStatus.valueOf(statusName)
                } catch (_: Exception) {
                    TrafficLightStatus.INVALID
                }
                val reason = intent.getStringExtra(EXTRA_REASON) ?: ""
                val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "Giro Certo"
                val destination = intent.getStringExtra(EXTRA_DESTINATION) ?: ""
                val fare = intent.getDoubleExtra(EXTRA_FARE, 0.0)
                val distance = intent.getDoubleExtra(EXTRA_DISTANCE, 0.0)
                val duration = intent.getIntExtra(EXTRA_DURATION, 0)

                val offer = RideOffer(
                    fare = fare,
                    distanceKm = distance,
                    durationMinutes = duration,
                    appName = appName,
                    destinationAddress = destination,
                    pickupDistanceKm = intent.getDoubleExtra("pickup_distance", 0.0),
                    pickupAddress = intent.getStringExtra("pickup_address") ?: "",
                    category = intent.getStringExtra("category") ?: "Corrida",
                    passengerRating = if (intent.hasExtra("rating")) intent.getDoubleExtra("rating", 0.0) else null
                )
                val settings = GiroCertoApp.instance.settingsRepository.settings.value
                currentAnalysis = RideCalculator.calculate(offer, settings).copy(decisionReason = reason)

                showOfferCardMode(kmRate, hourRate, profit, status, reason, appName, destination)
            }
        }

        return START_STICKY
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. MODO BOLHA FLUTUANTE PERMANENTE
    // ──────────────────────────────────────────────────────────────────────────

    private fun showBubbleMode() {
        if (!OverlayController.canDrawOverlays(this)) {
            Log.w("GiroOverlay", "Window blocked: overlay permission missing")
            stopSelf()
            return
        }

        autoDismissHandler.removeCallbacks(autoCollapseRunnable)
        currentMode = Mode.BUBBLE
        removeCurrentViewOnly()

        val bubble = createBubbleView()
        currentView = bubble

        val layoutParams = getWindowLayoutParams(
            width = dpToPx(58),
            height = dpToPx(58),
            x = posX,
            y = posY
        )
        params = layoutParams

        try {
            checkNotNull(windowManager).addView(bubble, layoutParams)
            OverlayController.setOverlayActive(true)
            Log.i("GiroOverlay", "Window attached: " + currentMode.name)
        } catch (error: Exception) {
            Log.e("GiroOverlay", "Window attachment failed: " + currentMode.name + " / " + error.javaClass.simpleName)
            removeOverlay()
            stopSelf()
        }
    }

    private fun createBubbleView(): View {
        val bubble = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#101818"))
                setStroke(dpToPx(3), Color.parseColor("#19BFB0"))
            }
            background = bg
            elevation = dpToPx(12).toFloat()
        }

        // Texto "GC" no centro da bolha
        val label = TextView(this).apply {
            text = "GC"
            setTextColor(Color.WHITE)
            textSize = 15f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        bubble.addView(label)

        // Ponto verde indicador de copiloto ativo no canto
        val statusDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#22C55E"))
                setStroke(dpToPx(1), Color.WHITE)
            }
            val dotSize = dpToPx(11)
            layoutParams = FrameLayout.LayoutParams(dotSize, dotSize).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, dpToPx(3), dpToPx(3), 0)
            }
        }
        bubble.addView(statusDot)

        // Drag & Click listener para a bolha
        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val currentParams = params ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = currentParams.x
                        initialY = currentParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 12 || abs(dy) > 12) {
                            isDragging = true
                        }
                        if (isDragging) {
                            currentParams.x = initialX + dx
                            currentParams.y = initialY + dy
                            posX = currentParams.x
                            posY = currentParams.y
                            try {
                                windowManager?.updateViewLayout(bubble, currentParams)
                            } catch (_: Exception) {}
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // Toque simples: alterna para o menu rápido
                            showQuickMenuMode()
                        }
                        return true
                    }
                }
                return false
            }
        })

        return bubble
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. MODO MENU RÁPIDO DO COPILOTO (Ao tocar na bolha)
    // ──────────────────────────────────────────────────────────────────────────

    private fun showQuickMenuMode() {
        if (!OverlayController.canDrawOverlays(this)) {
            Log.w("GiroOverlay", "Window blocked: overlay permission missing")
            return
        }

        currentMode = Mode.QUICK_MENU
        removeCurrentViewOnly()

        val card = createQuickMenuView()
        currentView = card

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val cardWidth = dpToPx(280)
        val targetX = (posX - cardWidth / 2).coerceIn(dpToPx(16), screenWidth - cardWidth - dpToPx(16))

        val layoutParams = getWindowLayoutParams(
            width = cardWidth,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = targetX,
            y = posY.coerceAtLeast(dpToPx(80))
        )
        params = layoutParams

        try {
            checkNotNull(windowManager).addView(card, layoutParams)
            OverlayController.setOverlayActive(true)
            Log.i("GiroOverlay", "Window attached: " + currentMode.name)
        } catch (error: Exception) {
            Log.e("GiroOverlay", "Window attachment failed: " + currentMode.name + " / " + error.javaClass.simpleName)
            switchToBubble()
        }
    }

    private fun createQuickMenuView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(Color.parseColor("#243535"), Color.parseColor("#101818"))
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(2), Color.parseColor("#19BFB0"))
            }
            elevation = dpToPx(16).toFloat()
        }

        // Header com título e botão recolher
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "Giro Certo Copiloto"
            setTextColor(Color.WHITE)
            textSize = 14f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        val collapseBtn = Button(this).apply {
            text = "━"
            setTextColor(Color.parseColor("#B5C9C7"))
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 14f
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
            setOnClickListener { switchToBubble() }
        }

        header.addView(title)
        header.addView(collapseBtn)
        root.addView(header)

        // Status de monitoramento
        val statusText = TextView(this).apply {
            text = "Leitura de ofertas • controle manual"
            setTextColor(Color.parseColor("#B5C9C7"))
            textSize = 11f
            setPadding(0, dpToPx(4), 0, dpToPx(10))
        }
        root.addView(statusText)

        // Lucro acumulado de hoje
        val summary = GiroCertoApp.instance.rideRepository.getTodaySummary(
            GiroCertoApp.instance.settingsRepository.settings.value.dailyFixedCost
        )
        val profitText = TextView(this).apply {
            text = String.format(Locale.GERMANY, "Estimativa hoje: R$ %.2f (%d ofertas)", summary.totalEstimatedProfit, summary.totalRides)
            setTextColor(if (summary.totalEstimatedProfit >= 0) Color.parseColor("#22C55E") else Color.parseColor("#EF4444"))
            textSize = 12f
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, dpToPx(10))
        }
        root.addView(profitText)

        // Botões de ação rápida
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val openAppBtn = Button(this).apply {
            text = "Abrir App"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#19BFB0"))
                cornerRadius = dpToPx(8).toFloat()
            }
            setOnClickListener {
                val intent = Intent(this@RideOverlayService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                switchToBubble()
            }
        }

        val closeBtn = Button(this).apply {
            text = "Fechar Flutuante"
            textSize = 11f
            setTextColor(Color.parseColor("#EF4444"))
            setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#304242"))
                cornerRadius = dpToPx(8).toFloat()
            }
            setOnClickListener {
                removeOverlay()
                stopSelf()
            }
        }

        val btnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
            setMargins(dpToPx(3), 0, dpToPx(3), 0)
        }
        actions.addView(openAppBtn, btnParams)
        actions.addView(closeBtn, btnParams)
        root.addView(actions)

        // Drag listener para mover o menu rápido
        attachDragListener(root)

        return root
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. MODO CARD COMPLETO DE OFERTA (Ao receber chamada de corrida)
    // ──────────────────────────────────────────────────────────────────────────

    private fun showOfferCardMode(
        kmRate: Double,
        hourRate: Double,
        profit: Double,
        status: TrafficLightStatus,
        reason: String,
        appName: String,
        destination: String
    ) {
        if (!OverlayController.canDrawOverlays(this)) return

        val settings = GiroCertoApp.instance.settingsRepository.settings.value
        val durationMs = (settings.cardDurationSeconds.coerceIn(5, 60)) * 1000L

        autoDismissHandler.removeCallbacks(autoCollapseRunnable)
        autoDismissHandler.postDelayed(autoCollapseRunnable, durationMs)

        // Keep the attached card and its dragged position when another reading arrives.
        if (currentMode == Mode.OFFER_CARD && currentView?.isAttachedToWindow == true) {
            updateOfferCardContents(kmRate, hourRate, profit, status, reason, appName, destination)
            Log.i("GiroOverlay", "Attached offer card updated")
            return
        }

        currentMode = Mode.OFFER_CARD
        removeCurrentViewOnly()

        val card = createOfferCardView()
        currentView = card

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val cardWidth = dpToPx(320).coerceAtMost(screenWidth - dpToPx(32))
        val targetX = (screenWidth - cardWidth) / 2

        val layoutParams = getWindowLayoutParams(
            width = cardWidth,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = targetX,
            y = dpToPx(80)
        )
        params = layoutParams

        try {
            checkNotNull(windowManager).addView(card, layoutParams)
            OverlayController.setOverlayActive(true)
            Log.i("GiroOverlay", "Window attached: " + currentMode.name)
        } catch (error: Exception) {
            Log.e("GiroOverlay", "Window attachment failed: " + currentMode.name + " / " + error.javaClass.simpleName)
            removeOverlay()
            stopSelf()
            return
        }

        updateOfferCardContents(kmRate, hourRate, profit, status, reason, appName, destination)
    }

    private fun createOfferCardView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(14), dpToPx(20), dpToPx(14))
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(Color.parseColor("#243535"), Color.parseColor("#101818"))
                cornerRadius = dpToPx(20).toFloat()
                setStroke(dpToPx(3), Color.parseColor("#304242"))
            }
            elevation = dpToPx(20).toFloat()
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val appBadge = TextView(this).apply {
            tag = "appBadge"
            text = "UBER"
            setTextColor(Color.WHITE)
            textSize = 11f
            paint.isFakeBoldText = true
            setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#19BFB0"))
                cornerRadius = dpToPx(6).toFloat()
            }
        }

        val title = TextView(this).apply {
            text = "Giro Certo"
            setTextColor(Color.parseColor("#F9FAFB"))
            textSize = 13f
            paint.isFakeBoldText = true
            setPadding(dpToPx(10), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        val closeBtn = Button(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#B5C9C7"))
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 14f
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
            setOnClickListener {
                val settings = GiroCertoApp.instance.settingsRepository.settings.value
                if (settings.isFloatingBubbleEnabled) {
                    switchToBubble()
                } else {
                    removeOverlay()
                    stopSelf()
                }
            }
        }

        header.addView(appBadge)
        header.addView(title)
        header.addView(closeBtn)
        root.addView(header)

        // Badge do Semáforo
        val statusBadge = TextView(this).apply {
            tag = "statusBadge"
            textSize = 13f
            paint.isFakeBoldText = true
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
        }
        val badgeParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, dpToPx(8), 0, dpToPx(8))
        }
        root.addView(statusBadge, badgeParams)
        root.addView(TextView(this).apply {
            tag = "decisionReason"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(0, dpToPx(4), 0, dpToPx(8))
        })

        // Métricas
        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun createMetricView(tag: String, label: String): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(8), 0, dpToPx(8), 0)

                val valText = TextView(this@RideOverlayService).apply {
                    this.tag = tag
                    textSize = 14f
                    paint.isFakeBoldText = true
                    setTextColor(Color.parseColor("#F9FAFB"))
                }
                val labelText = TextView(this@RideOverlayService).apply {
                    text = label
                    textSize = 10f
                    setTextColor(Color.parseColor("#B5C9C7"))
                }
                addView(valText)
                addView(labelText)
            }
        }

        metricsRow.addView(createMetricView("valKm", "R$/km"))
        metricsRow.addView(createMetricView("valHour", "R$/h"))
        metricsRow.addView(createMetricView("valProfit", "Lucro"))
        root.addView(metricsRow)

        // Ações: Salvar e Maps
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(8), 0, 0)
        }

        val saveBtn = Button(this).apply {
            tag = "saveBtn"
            text = "Salvar análise"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#304242"))
                cornerRadius = dpToPx(8).toFloat()
            }
            setOnClickListener {
                currentAnalysis?.let { analysis ->
                    serviceScope.launch {
                        try {
                            GiroCertoApp.instance.rideRepository.saveRide(analysis)
                            Toast.makeText(this@RideOverlayService, "Análise salva no Giro Certo!", Toast.LENGTH_SHORT).show()
                        } catch (_: java.io.IOException) {
                            Toast.makeText(this@RideOverlayService, "Não foi possível salvar. Tente novamente.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        val mapsBtn = Button(this).apply {
            tag = "mapsBtn"
            text = "Ver destino"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#19BFB0"))
                cornerRadius = dpToPx(8).toFloat()
            }
            setOnClickListener {
                val dest = currentAnalysis?.offer?.destinationAddress
                if (!dest.isNullOrBlank()) {
                    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(dest)}")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        setPackage("com.google.android.apps.maps")
                    }
                    try {
                        startActivity(mapIntent)
                    } catch (_: Exception) {
                        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(dest)}")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try { startActivity(fallback) } catch (_: Exception) {}
                    }
                }
            }
        }

        val btnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
            setMargins(dpToPx(3), 0, dpToPx(3), 0)
        }
        actionsRow.addView(saveBtn, btnParams)
        actionsRow.addView(mapsBtn, btnParams)
        root.addView(actionsRow)

        attachDragListener(root)

        return root
    }

    private fun updateOfferCardContents(
        kmRate: Double,
        hourRate: Double,
        profit: Double,
        status: TrafficLightStatus,
        reason: String,
        appName: String,
        destination: String
    ) {
        val root = currentView as? LinearLayout ?: return

        val appBadge = root.findViewWithTag<TextView>("appBadge")
        val statusBadge = root.findViewWithTag<TextView>("statusBadge")
        root.findViewWithTag<TextView>("decisionReason")?.text = reason
        val valKm = root.findViewWithTag<TextView>("valKm")
        val valHour = root.findViewWithTag<TextView>("valHour")
        val valProfit = root.findViewWithTag<TextView>("valProfit")
        val mapsBtn = root.findViewWithTag<Button>("mapsBtn")

        appBadge?.apply {
            text = appName.uppercase(Locale.ROOT)
            val badgeBgColor = when {
                appName.contains("uber eats", ignoreCase = true) -> "#06B6D4"
                appName.contains("uber moto", ignoreCase = true) -> "#304242"
                appName.contains("uber", ignoreCase = true) -> "#000000"
                appName.contains("99food", ignoreCase = true) || appName.contains("didi food", ignoreCase = true) -> "#F97316"
                appName.contains("99", ignoreCase = true) || appName.contains("didi", ignoreCase = true) -> "#F97316"
                appName.contains("ifood", ignoreCase = true) -> "#EA1D2C"
                appName.contains("indrive", ignoreCase = true) -> "#10B981"
                appName.contains("keeta", ignoreCase = true) -> "#F59E0B"
                else -> "#19BFB0"
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor(badgeBgColor))
                cornerRadius = dpToPx(6).toFloat()
                setStroke(1, Color.parseColor("#4B5563"))
            }
        }

        valKm?.text = String.format(Locale.GERMANY, "R$ %.2f", kmRate)
        valHour?.text = if ((currentAnalysis?.offer?.durationMinutes ?: 0) > 0) String.format(Locale.GERMANY, "R$ %.2f", hourRate) else "—"
        valHour?.contentDescription = if ((currentAnalysis?.offer?.durationMinutes ?: 0) > 0) "Rendimento por hora" else "Tempo não informado"
        valProfit?.text = String.format(Locale.GERMANY, "R$ %.2f", profit)

        val (colorHex, text) = when (status) {
            TrafficLightStatus.GREEN -> Pair("#22C55E", "OFERTA COMPATÍVEL (VERDE)")
            TrafficLightStatus.YELLOW -> Pair("#FACC15", "ATENÇÃO / LIMITE (AMARELO)")
            TrafficLightStatus.RED -> Pair("#EF4444", "NÃO COMPENSA (VERMELHO)")
            TrafficLightStatus.INVALID -> Pair("#6B7280", "DADOS INCOMPLETOS")
        }

        statusBadge?.apply {
            this.text = text
            background = GradientDrawable().apply {
                setColor(Color.parseColor(colorHex))
                cornerRadius = dpToPx(8).toFloat()
            }
            setTextColor(if (status == TrafficLightStatus.YELLOW) Color.BLACK else Color.WHITE)
        }

        mapsBtn?.visibility = if (destination.isNotBlank()) View.VISIBLE else View.GONE
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers e Ciclo de Vida
    // ──────────────────────────────────────────────────────────────────────────

    private fun switchToBubble() {
        showBubbleMode()
    }

    private fun attachDragListener(view: View) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val currentParams = params ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = currentParams.x
                        initialY = currentParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        currentParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        currentParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager?.updateViewLayout(view, currentParams)
                        } catch (_: Exception) {}
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun getWindowLayoutParams(width: Int, height: Int, x: Int, y: Int): WindowManager.LayoutParams {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            width,
            height,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun removeCurrentViewOnly() {
        if (currentView != null) {
            try {
                windowManager?.removeView(currentView)
            } catch (_: Exception) {}
            currentView = null
        }
    }

    private fun removeOverlay() {
        autoDismissHandler.removeCallbacks(autoCollapseRunnable)
        removeCurrentViewOnly()
        OverlayController.setOverlayActive(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    companion object {
        const val ACTION_START_BUBBLE = "com.girocerto.overlay.ACTION_START_BUBBLE"
        const val ACTION_SHOW = "com.girocerto.overlay.ACTION_SHOW"
        const val ACTION_STOP = "com.girocerto.overlay.ACTION_STOP"

        const val EXTRA_KM_RATE = "extra_km_rate"
        const val EXTRA_HOUR_RATE = "extra_hour_rate"
        const val EXTRA_PROFIT = "extra_profit"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_DESTINATION = "extra_destination"
        const val EXTRA_FARE = "extra_fare"
        const val EXTRA_DISTANCE = "extra_distance"
        const val EXTRA_DURATION = "extra_duration"
    }
}

