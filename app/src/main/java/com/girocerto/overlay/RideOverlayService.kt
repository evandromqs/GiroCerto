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
import kotlinx.coroutines.cancel
import com.girocerto.monitoring.CopilotPreferences
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
    private var trashTarget: FrameLayout? = null
    private var trashHovered = false
    private var params: WindowManager.LayoutParams? = null

    // Posição na tela (persistida durante a sessão)
    private var posX: Int = 30
    private var posY: Int = 300

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private var currentAnalysis: RideAnalysis? = null

    private val autoCollapseRunnable = Runnable {
        if (CopilotPreferences(this@RideOverlayService).read().enabled) {
            switchToBubble()
        } else {
            removeOverlay()
            stopSelf()
        }
    }

    inner class LocalBinder : android.os.Binder() { val service get() = this@RideOverlayService }
    override fun onBind(intent: Intent?): IBinder = LocalBinder()
    fun ensureBubble() { if (!OverlayController.hidden(this) && currentView?.isAttachedToWindow != true) showBubbleMode() }
    fun updateReaderStatus(message: String) {
        currentView?.findViewWithTag<TextView>("readerStatus")?.text = message
    }
    fun dismiss() { removeOverlay(); stopSelf() }


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

        return START_NOT_STICKY
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. MODO BOLHA FLUTUANTE PERMANENTE
    // ──────────────────────────────────────────────────────────────────────────

    private fun showBubbleMode() {
        if (OverlayController.hidden(this)) { dismiss(); return }
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
            width = dpToPx(64),
            height = dpToPx(64),
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
            contentDescription = "Giro Certo. Toque para abrir; arraste até a lixeira para ocultar."
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(palette("#415A7D"), palette("#102746"), Color.BLACK)).apply {
                shape = GradientDrawable.OVAL
                setStroke(dpToPx(2), palette("#C9D5E6"))
            }
            elevation = dpToPx(10).toFloat()
        }
        bubble.addView(android.widget.ImageView(this).apply {
            setImageResource(com.girocerto.app.R.drawable.ic_launcher_monochrome)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(dpToPx(96), dpToPx(96), Gravity.CENTER))
        bubble.setOnClickListener { showQuickMenuMode() }
        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var dragging = false
            private val slop = android.view.ViewConfiguration.get(this@RideOverlayService).scaledTouchSlop
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val layout = params ?: return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layout.x; initialY = layout.y
                        touchX = event.rawX; touchY = event.rawY; dragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchX
                        val dy = event.rawY - touchY
                        if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                            dragging = true; showTrashTarget()
                        }
                        if (dragging) {
                            layout.x = (initialX + dx.toInt()).coerceIn(0, (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0))
                            layout.y = (initialY + dy.toInt()).coerceIn(0, (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0))
                            runCatching { windowManager?.updateViewLayout(view, layout) }
                            updateTrashHover(isOverTrash(view))
                            view.alpha = if (trashHovered) .65f else 1f
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val remove = dragging && isOverTrash(view)
                        hideTrashTarget(); view.alpha = 1f
                        if (remove) {
                            posX = initialX; posY = initialY
                            OverlayController.hideByUser(this@RideOverlayService)
                            Toast.makeText(this@RideOverlayService, "Janela ocultada. Para voltar, abra Copiloto.", Toast.LENGTH_SHORT).show()
                        } else if (dragging) {
                            val edge = dpToPx(12)
                            layout.x = if (layout.x + view.width / 2 < resources.displayMetrics.widthPixels / 2) edge
                                else (resources.displayMetrics.widthPixels - view.width - edge).coerceAtLeast(0)
                            posX = layout.x; posY = layout.y
                            runCatching { windowManager?.updateViewLayout(view, layout) }
                        } else view.performClick()
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        hideTrashTarget(); view.alpha = 1f
                        layout.x = initialX; layout.y = initialY
                        runCatching { windowManager?.updateViewLayout(view, layout) }
                        return true
                    }
                }
                return false
            }
        })
        return bubble
    }

    private fun showTrashTarget() {
        if (trashTarget != null) return
        val target = FrameLayout(this).apply {
            contentDescription = "Solte aqui para ocultar"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val icon = android.widget.ImageView(this).apply {
            tag = "trashIcon"
            setImageResource(com.girocerto.app.R.drawable.ic_trash)
            setPadding(dpToPx(18), dpToPx(18), dpToPx(18), dpToPx(18))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.WHITE, palette("#C9D5E6"))).apply {
                shape = GradientDrawable.OVAL; setStroke(dpToPx(2), palette("#102746"))
            }
            elevation = dpToPx(6).toFloat()
        }
        target.addView(icon, FrameLayout.LayoutParams(dpToPx(64), dpToPx(64), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dpToPx(8) })
        target.addView(TextView(this).apply {
            typeface = resources.getFont(com.girocerto.app.R.font.poppins_medium)
            tag = "trashLabel"; text = "Solte para ocultar"; textSize = 12f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(palette("#102746")); cornerRadius = dpToPx(8).toFloat() }
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dpToPx(32), Gravity.BOTTOM))
        val bottomInset = if (Build.VERSION.SDK_INT >= 30) windowManager?.currentWindowMetrics?.windowInsets
            ?.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars())?.bottom ?: 0 else dpToPx(24)
        val layout = getWindowLayoutParams(dpToPx(144), dpToPx(112), 0, 0).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0; y = bottomInset + dpToPx(16)
            // Keep this short-lived target discoverable by accessibility during the drag.
            // The pointer remains captured by the bubble until ACTION_UP.
        }
        try { windowManager?.addView(target, layout); trashTarget = target }
        catch (_: Exception) { trashTarget = null }
    }

    private fun isOverTrash(bubble: View): Boolean {
        val target = trashTarget ?: return false
        val location = IntArray(2); target.getLocationOnScreen(location)
        val bubbleLocation = IntArray(2); bubble.getLocationOnScreen(bubbleLocation)
        return BubbleDropZone.contains(bubbleLocation[0] + bubble.width / 2f, bubbleLocation[1] + bubble.height / 2f,
            location[0] + target.width / 2f, location[1] + dpToPx(40).toFloat(), dpToPx(50).toFloat())
    }

    private fun updateTrashHover(hovered: Boolean) {
        if (trashHovered == hovered) return
        trashHovered = hovered
        trashTarget?.findViewWithTag<View>("trashIcon")?.apply {
            scaleX = if (hovered) 1.15f else 1f; scaleY = scaleX
            if (hovered) performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
        trashTarget?.findViewWithTag<TextView>("trashLabel")?.text = if (hovered) "Solte agora" else "Solte para ocultar"
    }

    private fun hideTrashTarget() {
        trashTarget?.let { runCatching { windowManager?.removeView(it) } }
        trashTarget = null; trashHovered = false
    }

    fun refreshAppearance() {
        if (currentView == null) return
        val mode = currentMode
        removeCurrentViewOnly()
        when (mode) {
            Mode.BUBBLE -> ensureBubble()
            Mode.QUICK_MENU -> showQuickMenuMode()
            Mode.OFFER_CARD -> currentAnalysis?.let { showOfferCardMode(it.grossPerKm, it.grossPerHour, it.estimatedProfit,
                it.trafficLight, it.decisionReason, it.offer.appName, it.offer.destinationAddress) }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshAppearance()
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
                colors = intArrayOf(palette("#243535"), palette("#101818"))
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(2), palette("#4B5563"))
            }
            elevation = dpToPx(16).toFloat()
        }

        // Header com título e botão recolher
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            typeface = resources.getFont(com.girocerto.app.R.font.poppins_medium)
            gravity = Gravity.CENTER
            text = "Giro Certo Copiloto"
            setTextColor(palette("#F9FAFB"))
            textSize = 14f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        val collapseBtn = Button(this).apply {
            text = "━"
            setTextColor(palette("#B5C9C7"))
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
            typeface = resources.getFont(com.girocerto.app.R.font.poppins_medium)
            gravity = Gravity.CENTER
            tag = "readerStatus"
            text = com.girocerto.monitoring.GiroCertoAccessibilityService.readerStatus.value
            setTextColor(palette("#B5C9C7"))
            textSize = 12f
            setPadding(0, dpToPx(4), 0, dpToPx(10))
        }
        root.addView(statusText)

        val today = GiroCertoApp.instance.workRepository.days.value.firstOrNull { it.date == java.time.LocalDate.now() }
        val profitText = TextView(this).apply {
            typeface = resources.getFont(com.girocerto.app.R.font.poppins_medium)
            text = today?.let { String.format(Locale.GERMANY, "Saldo registrado hoje: R$ %.2f", it.net) }
                ?: "Hoje: ganhos ainda não registrados"
            setTextColor(palette("#F9FAFB"))
            gravity = Gravity.CENTER
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
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
            background = GradientDrawable().apply {
                setColor(palette("#19BFB0"))
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
            text = "Pausar copiloto"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
            background = GradientDrawable().apply {
                setColor(palette("#304242"))
                cornerRadius = dpToPx(8).toFloat()
            }
            setOnClickListener {
                val preferences = CopilotPreferences(this@RideOverlayService)
                preferences.write(preferences.read().copy(enabled = false))
                dismiss()
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
        val cardWidth = dpToPx(360).coerceAtMost(screenWidth - dpToPx(32))
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
        card.alpha = 0f; card.translationY = dpToPx(8).toFloat()
        card.animate().alpha(1f).translationY(0f).setDuration(180).start()
    }

    private fun createOfferCardView(): LinearLayout {
        fun label(value: String = "", name: String? = null, size: Float = 14f) = TextView(this).apply {
            typeface = resources.getFont(com.girocerto.app.R.font.poppins_medium)
            text = value; tag = name; textSize = size
            typeface = resources.getFont(com.girocerto.app.R.font.poppins_medium)
            includeFontPadding = false; setLineSpacing(dpToPx(3).toFloat(), 1f)
            setTextColor(palette("#F9FAFB"))
        }
        fun action(value: String, name: String, click: () -> Unit) = Button(this).apply {
            text = value; tag = name; textSize = 12f; isAllCaps = false
            typeface = resources.getFont(com.girocerto.app.R.font.poppins_medium)
            minHeight = dpToPx(48); minimumHeight = dpToPx(48)
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.parseColor("#102746")); cornerRadius = dpToPx(12).toFloat() }
            setOnClickListener { click() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(16))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(palette("#243535"), palette("#101818"))).apply {
                cornerRadius = dpToPx(24).toFloat(); setStroke(dpToPx(1), palette("#4B5563"))
            }
            elevation = dpToPx(12).toFloat()
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("Giro Certo • Copiloto", size = 12f))
        brand.addView(label(name = "appBadge", size = 16f))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(action("—", "minimizeBtn") { switchToBubble() }.apply {
            contentDescription = "Minimizar análise e manter copiloto ativo"
            textSize = 22f
        }, LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)).apply { leftMargin = dpToPx(12) })
        root.addView(header)
        attachDragListener(brand)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = object : android.widget.ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec((resources.displayMetrics.heightPixels * .48f).toInt(), View.MeasureSpec.AT_MOST))
            }
        }.apply { isFillViewport = false; addView(content) }
        root.addView(scroll)
        fun add(view: View, top: Int = 12) { content.addView(view, LinearLayout.LayoutParams(-1,-2).apply { topMargin = dpToPx(top) }) }
        add(label(name = "statusBadge", size = 14f).apply {
            gravity = Gravity.CENTER; setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
        })
        add(label(name = "offerSummary", size = 13f))
        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("valKm" to "R$/km", "valHour" to "R$/hora", "valProfit" to "Saldo¹").forEachIndexed { index, (tag, title) ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dpToPx(4), dpToPx(12), dpToPx(4), dpToPx(12))
                background = GradientDrawable().apply { setColor(palette("#101818")); cornerRadius = dpToPx(12).toFloat(); setStroke(dpToPx(1), palette("#4B5563")) }
                addView(label(name = tag, size = 18f).apply {
                    gravity = Gravity.CENTER; maxLines = 1
                    setAutoSizeTextTypeUniformWithConfiguration(12,18,1,android.util.TypedValue.COMPLEX_UNIT_SP)
                }, LinearLayout.LayoutParams(-1,-2))
                addView(label(title, size = 11f).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1,-2).apply { topMargin = dpToPx(4) })
            }
            metrics.addView(box, LinearLayout.LayoutParams(0,-2,1f).apply { if(index>0) leftMargin = dpToPx(8) })
        }
        add(metrics)
        add(label(name = "decisionReason", size = 13f).apply {
            justificationMode = android.graphics.text.LineBreaker.JUSTIFICATION_MODE_INTER_WORD
        })
        add(label("¹ Saldo estimado da oferta, antes do contrato semanal. Você decide se aceita a corrida.", size = 11f))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(action("Salvar análise", "saveBtn") {
            currentAnalysis?.let { analysis -> serviceScope.launch {
                try { GiroCertoApp.instance.rideRepository.saveRide(analysis); Toast.makeText(this@RideOverlayService,"Análise salva.",Toast.LENGTH_SHORT).show() }
                catch (_: Exception) { Toast.makeText(this@RideOverlayService,"Não foi possível salvar a análise.",Toast.LENGTH_SHORT).show() }
            } }
        }, LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(action("Ver destino", "mapsBtn") {
            currentAnalysis?.offer?.destinationAddress?.takeIf { it.isNotBlank() }?.let { destination ->
                runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q=${Uri.encode(destination)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { Toast.makeText(this,"Nenhum aplicativo de mapas disponível.",Toast.LENGTH_SHORT).show() }
            }
        }, LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin = dpToPx(8) })
        add(actions)
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

        appBadge?.text = appName
        currentAnalysis?.let { analysis ->
            root.findViewWithTag<TextView>("offerSummary")?.text = String.format(Locale.GERMANY,
                "R$ %.2f • %.1f km • %s", analysis.offer.fare, analysis.totalDistanceKm,
                if (analysis.offer.durationMinutes > 0) "${analysis.offer.durationMinutes} min" else "Tempo não lido")
        }
        val dark = com.girocerto.app.theme.AppearancePreferences.isDark(this)
        val gainColor = Color.parseColor(if(dark) "#6EE7B7" else "#087443")
        val lossColor = Color.parseColor(if(dark) "#FDA4AF" else "#B42336")
        val warningColor = Color.parseColor(if(dark) "#FDE68A" else "#885600")
        val decisionColor = when(status) { TrafficLightStatus.GREEN -> gainColor; TrafficLightStatus.YELLOW -> warningColor; TrafficLightStatus.RED -> lossColor; else -> palette("#B5C9C7") }
        valKm?.setTextColor(decisionColor); valHour?.setTextColor(decisionColor)
        valProfit?.setTextColor(if(profit > 0) gainColor else if(profit < 0) lossColor else warningColor)
        valKm?.text = String.format(Locale.GERMANY, "%.2f", kmRate)
        valHour?.text = if ((currentAnalysis?.offer?.durationMinutes ?: 0) > 0) String.format(Locale.GERMANY, "%.2f", hourRate) else "—"
        valHour?.contentDescription = if ((currentAnalysis?.offer?.durationMinutes ?: 0) > 0) "Rendimento por hora" else "Tempo não informado"
        valProfit?.text = String.format(Locale.GERMANY, "%.2f", profit)

        val (colorHex, text) = when (status) {
            TrafficLightStatus.GREEN -> Pair("#22C55E", "COMPENSA")
            TrafficLightStatus.YELLOW -> Pair("#FACC15", "ATENÇÃO / LIMITE")
            TrafficLightStatus.RED -> Pair("#EF4444", "NÃO COMPENSA")
            TrafficLightStatus.INVALID -> Pair("#6B7280", "DADOS INCOMPLETOS")
        }

        statusBadge?.apply {
            this.text = text
            background = GradientDrawable().apply {
                setColor(Color.parseColor(when(status) { TrafficLightStatus.GREEN -> "#087443"; TrafficLightStatus.YELLOW -> "#FDE68A"; TrafficLightStatus.RED -> "#B42336"; else -> "#40536D" }))
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
                            currentView?.let { root ->
                                currentParams.x = currentParams.x.coerceIn(0, (resources.displayMetrics.widthPixels - root.width).coerceAtLeast(0))
                                currentParams.y = currentParams.y.coerceIn(0, (resources.displayMetrics.heightPixels - root.height).coerceAtLeast(0))
                                windowManager?.updateViewLayout(root, currentParams)
                            }
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
            this.x = x.coerceIn(0, (resources.displayMetrics.widthPixels - width.coerceAtLeast(dpToPx(58))).coerceAtLeast(0))
            this.y = y.coerceIn(0, (resources.displayMetrics.heightPixels - dpToPx(180)).coerceAtLeast(0))
        }
    }

    private fun removeCurrentViewOnly() {
        hideTrashTarget()
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
        serviceScope.cancel()
    }

    private fun palette(hex: String): Int {
        val dark = com.girocerto.app.theme.AppearancePreferences.isDark(this)
        val color = when (hex.uppercase(Locale.ROOT)) {
            "#101818" -> if (dark) "#101B2D" else "#FFFFFF"
            "#243535" -> if (dark) "#293F5E" else "#F8FAFD"
            "#304242" -> "#263E5E"
            "#4B5563" -> if (dark) "#58708F" else "#B2C2D8"
            "#F9FAFB" -> if (dark) "#FFFFFF" else "#10151D"
            "#B5C9C7" -> if (dark) "#CED8E7" else "#40536D"
            "#FACC15" -> "#DEE5EF"
            "#EF4444" -> "#080D15"
            "#6B7280" -> "#40536D"
            "#19BFB0", "#22C55E", "#10B981", "#06B6D4", "#EA1D2C", "#F97316", "#F59E0B" -> "#102746"
            else -> hex
        }
        return Color.parseColor(color)
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

