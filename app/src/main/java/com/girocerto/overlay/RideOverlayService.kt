package com.girocerto.overlay

import android.app.Service
import android.util.Log
import android.content.Intent
import android.graphics.Color
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
import com.girocerto.domain.usecase.AnalyzeRideOffer
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

    private lateinit var windowHost: OverlayWindowHost
    private var currentView: View? = null
    private var currentMode: Mode = Mode.BUBBLE
    private var trashTarget: FrameLayout? = null
    private var trashHovered = false
    private var params: WindowManager.LayoutParams? = null

    // Posição na tela (persistida durante a sessão)
    private var posX: Int = 30
    private var posY: Int = 300
    private var dockRight = true

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private var currentAnalysis: RideAnalysis? = null
    private val recentOffers = RecentOffers()
    private val analyzeRideOffer = AnalyzeRideOffer()
    private val acceptedRideRecorder by lazy {
        AcceptedRideRecorder(
            GiroCertoApp.instance.rideRepository,
            GiroCertoApp.instance.workRepository
        )
    }
    private val expiryTick = object : Runnable {
        override fun run() {
            val entries = recentOffers.active(android.os.SystemClock.elapsedRealtime())
            currentAnalysis = entries.firstOrNull()?.analysis
            if (currentMode == Mode.OFFER_CARD) {
                if (entries.isEmpty()) switchToBubble() else renderRecentOffers()
            }
            if (entries.isNotEmpty()) autoDismissHandler.postDelayed(this, if (currentMode == Mode.OFFER_CARD) 100 else 1000)
        }
    }

    inner class LocalBinder : android.os.Binder() { val service get() = this@RideOverlayService }
    override fun onBind(intent: Intent?): IBinder = LocalBinder()
    fun ensureBubble() { if (!OverlayController.hidden(this) && currentView?.isAttachedToWindow != true) showBubbleMode() }
    fun updateReaderStatus(message: String) {
        // Technical reader details remain in Copilot diagnostics, outside the driving panel.
    }
    fun dismiss() { removeOverlay(); stopSelf() }


    override fun onCreate() {
        super.onCreate()
        windowHost = OverlayWindowHost(this)
        val corner = CopilotPreferences(this).read().restingCorner
        dockRight = corner == OverlayCorner.TOP_RIGHT || corner == OverlayCorner.BOTTOM_RIGHT
        posX = if (corner == OverlayCorner.TOP_RIGHT || corner == OverlayCorner.BOTTOM_RIGHT)
            resources.displayMetrics.widthPixels - bubbleSize() - dpToPx(12) else dpToPx(12)
        posY = if (corner == OverlayCorner.BOTTOM_LEFT || corner == OverlayCorner.BOTTOM_RIGHT)
            resources.displayMetrics.heightPixels - bubbleSize() - dpToPx(80) else dpToPx(100)
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
                    passengerRating = if (intent.hasExtra("rating")) intent.getDoubleExtra("rating", 0.0) else null,
                    sourcePackage = intent.getStringExtra("source_package") ?: "",
                    addedMinutes = intent.getIntExtra("added_minutes", 0)
                )
                val settings = GiroCertoApp.instance.settingsRepository.settings.value
                currentAnalysis = analyzeRideOffer(offer, settings).let { analysis ->
                    if (reason.isBlank()) analysis else analysis.copy(decisionReason = reason)
                }

                if (recentOffers.add(checkNotNull(currentAnalysis), android.os.SystemClock.elapsedRealtime(), if (settings.cardDurationSeconds == 30) 30_000L else 15_000L)) {
                    showOfferCardMode()
                    autoDismissHandler.removeCallbacks(expiryTick)
                    autoDismissHandler.postDelayed(expiryTick, 100)
                }
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

        currentMode = Mode.BUBBLE
        removeCurrentViewOnly()

        val bubble = createBubbleView()
        currentView = bubble
        posX = if (dockRight) (resources.displayMetrics.widthPixels - bubbleSize() - dpToPx(12)).coerceAtLeast(0) else dpToPx(12)

        val layoutParams = getWindowLayoutParams(
            width = bubbleSize(),
            height = bubbleSize(),
            x = posX,
            y = posY
        )
        params = layoutParams

        try {
            windowHost.attach(bubble, layoutParams)
            OverlayController.setOverlayActive(true)
            Log.i("GiroOverlay", "Window attached: " + currentMode.name)
        } catch (error: Exception) {
            Log.e("GiroOverlay", "Window attachment failed: " + currentMode.name + " / " + error.javaClass.simpleName)
            removeOverlay()
            stopSelf()
        }
    }

    private fun createBubbleView(): View {
        return BubbleViewFactory(this, ::dpToPx, object : BubbleViewFactory.Callbacks {
            override fun onClick() {
                currentAnalysis = recentOffers.active(android.os.SystemClock.elapsedRealtime()).firstOrNull()?.analysis
                if (currentAnalysis != null) showOfferCardMode() else showQuickMenuMode()
            }
            override fun layoutParams() = params
            override fun onDragStarted() = showTrashTarget()
            override fun onDrag(view: View, layout: WindowManager.LayoutParams) {
                layout.x = layout.x.coerceIn(0, (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0))
                layout.y = layout.y.coerceIn(0, (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0))
                runCatching { windowHost.update(view, layout) }
                updateTrashHover(isOverTrash(view)); view.alpha = if (trashHovered) .65f else 1f
            }
            override fun onDrop(view: View, layout: WindowManager.LayoutParams, initialX: Int, initialY: Int) {
                val remove = isOverTrash(view); hideTrashTarget(); view.alpha = 1f
                if (remove) {
                    posX = initialX; posY = initialY
                    OverlayController.hideByUser(this@RideOverlayService)
                    Toast.makeText(this@RideOverlayService, "Janela ocultada. Para voltar, abra Copiloto.", Toast.LENGTH_SHORT).show()
                } else {
                    val edge = dpToPx(12)
                    dockRight = layout.x + view.width / 2 >= resources.displayMetrics.widthPixels / 2
                    layout.x = if (!dockRight) edge else (resources.displayMetrics.widthPixels - view.width - edge).coerceAtLeast(0)
                    posX = layout.x; posY = layout.y
                    runCatching { windowHost.update(view, layout) }
                }
            }
            override fun onDragCancelled(view: View, layout: WindowManager.LayoutParams, initialX: Int, initialY: Int) {
                hideTrashTarget(); view.alpha = 1f; layout.x = initialX; layout.y = initialY
                runCatching { windowHost.update(view, layout) }
            }
        }).create()
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
        val bottomInset = windowHost.navigationBarInset(dpToPx(24))
        val layout = getWindowLayoutParams(dpToPx(144), dpToPx(112), 0, 0).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0; y = bottomInset + dpToPx(16)
            // Keep this short-lived target discoverable by accessibility during the drag.
            // The pointer remains captured by the bubble until ACTION_UP.
        }
        try { windowHost.attach(target, layout); trashTarget = target }
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
        trashTarget?.let { runCatching { windowHost.remove(it) } }
        trashTarget = null; trashHovered = false
    }

    fun refreshAppearance() {
        if (currentView == null) return
        val mode = currentMode
        removeCurrentViewOnly()
        when (mode) {
            Mode.BUBBLE -> ensureBubble()
            Mode.QUICK_MENU -> showQuickMenuMode()
            Mode.OFFER_CARD -> if (recentOffers.active(android.os.SystemClock.elapsedRealtime()).isEmpty()) showBubbleMode() else showOfferCardMode()
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
        val cardWidth = dpToPx(440).coerceAtMost(screenWidth - dpToPx(24))

        val layoutParams = getWindowLayoutParams(
            width = cardWidth,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = 0,
            y = 0
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; x = 0; y = panelTop() }
        params = layoutParams

        try {
            windowHost.attach(card, layoutParams)
            OverlayController.setOverlayActive(true)
            Log.i("GiroOverlay", "Window attached: " + currentMode.name)
        } catch (error: Exception) {
            Log.e("GiroOverlay", "Window attachment failed: " + currentMode.name + " / " + error.javaClass.simpleName)
            switchToBubble()
        }
    }

    private fun createQuickMenuView(): View {
        val root = FrameLayout(this).apply {
            contentDescription = "Menu rápido do Giro Certo"
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelSurface()
            elevation = dpToPx(12).toFloat()
            setPadding(dpToPx(16), dpToPx(48), dpToPx(16), dpToPx(16))
        }

        // Close button at top right
        val closeRow = LinearLayout(this).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        val closeBtn = iconAction(com.girocerto.app.R.drawable.ic_close_panel, "Recolher painel") { switchToBubble() }.apply {
            setPadding(dpToPx(5), dpToPx(5), dpToPx(5), dpToPx(5))
        }
        closeRow.addView(closeBtn, LinearLayout.LayoutParams(dpToPx(26), dpToPx(26)))
        card.addView(closeRow)

        // Summary Data (Meta do dia, Total feito, Kms rodados)
        val today = GiroCertoApp.instance.workRepository.days.value.firstOrNull { it.date == java.time.LocalDate.now() }
        val profile = GiroCertoApp.instance.workRepository.profile.value
        val goal = today?.grossGoal ?: profile?.dailyGrossGoal ?: 0.0
        val revenue = today?.revenue ?: 0.0
        val km = today?.totalKm ?: 0.0

        val summaryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(2), 0, dpToPx(10))
        }

        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun summaryCol(title: String, value: String): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(label(value, 17f).apply {
                    paint.isFakeBoldText = true
                    setTextColor(palette("#F9FAFB"))
                    gravity = Gravity.CENTER
                })
                addView(label(title, 11f).apply {
                    setTextColor(palette("#B5C9C7"))
                    gravity = Gravity.CENTER
                })
            }
        }

        val goalStr = if (goal > 0) String.format(Locale.GERMANY, "R$ %.0f", goal) else "—"
        val revStr = String.format(Locale.GERMANY, "R$ %.2f", revenue)
        val kmStr = String.format(Locale.GERMANY, "%.1f km", km)

        metricsRow.addView(summaryCol("Meta do dia", goalStr), LinearLayout.LayoutParams(0, -2, 1f))
        metricsRow.addView(summaryCol("Total feito", revStr), LinearLayout.LayoutParams(0, -2, 1f))
        metricsRow.addView(summaryCol("Kms rodados", kmStr), LinearLayout.LayoutParams(0, -2, 1f))
        summaryContainer.addView(metricsRow)

        val progress = if (goal > 0) (revenue / goal).toFloat().coerceIn(0f, 1f) else 0f
        summaryContainer.addView(android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            setProgress((progress * 1000).toInt())
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1D4ED8"))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(palette("#4B5563"))
        }, LinearLayout.LayoutParams(-1, dpToPx(5)).apply { topMargin = dpToPx(10) })
        card.addView(summaryContainer)

        // Divider
        card.addView(View(this).apply {
            setBackgroundColor(palette("#4B5563"))
        }, LinearLayout.LayoutParams(-1, dpToPx(1)).apply { topMargin = dpToPx(4); bottomMargin = dpToPx(14) })

        // 3 Buttons aligned horizontally centered
        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun menuButton(iconRes: Int, text: String, activeBg: Int? = null, strokeColor: Int? = null, onClick: () -> Unit): LinearLayout {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
            val iconBtn = android.widget.ImageView(this).apply {
                setImageResource(iconRes)
                imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(activeBg ?: Color.parseColor("#1D4ED8"))
                    setStroke(dpToPx(1), strokeColor ?: Color.parseColor("#60A5FA"))
                }
                elevation = dpToPx(3).toFloat()
            }
            col.addView(iconBtn, LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)))
            col.addView(label(text, 11f).apply {
                gravity = Gravity.CENTER
                setTextColor(palette("#F9FAFB"))
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpToPx(6) })
            return col
        }

        // Button 1: Registrar Ponto
        buttonsRow.addView(menuButton(com.girocerto.app.R.drawable.ic_trip_clock, "Registrar Ponto", Color.parseColor("#1D4ED8"), Color.parseColor("#60A5FA")) {
            val intent = Intent(this@RideOverlayService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("target_screen", "SHIFT")
            }
            startActivity(intent)
            switchToBubble()
        }, LinearLayout.LayoutParams(0, -2, 1f))

        // Button 2: Histórico de Corridas
        buttonsRow.addView(menuButton(com.girocerto.app.R.drawable.ic_history, "Histórico", Color.parseColor("#1D4ED8"), Color.parseColor("#60A5FA")) {
            val intent = Intent(this@RideOverlayService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("target_screen", "HISTORY")
            }
            startActivity(intent)
            switchToBubble()
        }, LinearLayout.LayoutParams(0, -2, 1f))

        // Button 3: Ativar / Desativar Cálculo (SEM COR VERDE - visual azul e slate refinado)
        val prefs = CopilotPreferences(this)
        val isCalcEnabled = prefs.read().enabled
        val calcBg = if (isCalcEnabled) Color.parseColor("#1D4ED8") else Color.parseColor("#1E293B")
        val calcStroke = if (isCalcEnabled) Color.parseColor("#60A5FA") else Color.parseColor("#475569")
        val calcLabel = if (isCalcEnabled) "Cálculo Ativo" else "Cálculo Pausado"
        buttonsRow.addView(menuButton(com.girocerto.app.R.drawable.ic_copilot, calcLabel, calcBg, calcStroke) {
            val current = prefs.read()
            val toggled = !current.enabled
            prefs.write(current.copy(enabled = toggled))
            Toast.makeText(this@RideOverlayService, if (toggled) "Cálculo de ofertas ativado!" else "Cálculo pausado", Toast.LENGTH_SHORT).show()
            showQuickMenuMode()
        }, LinearLayout.LayoutParams(0, -2, 1f))

        card.addView(buttonsRow)

        // Card com topMargin de 42dp para acomodar o ícone superior de 84dp sobreposto pela metade
        root.addView(card, FrameLayout.LayoutParams(-1, -2).apply { topMargin = dpToPx(42) })

        // Central Top Icon com girocerto.webp aumentado em 50% (84dp)
        val topIconContainer = FrameLayout(this).apply {
            elevation = dpToPx(16).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1D4ED8"))
                setStroke(dpToPx(3), Color.parseColor("#93B4FF"))
            }
            isClickable = true
            isFocusable = true
            contentDescription = "Abrir Giro Certo em primeiro plano"
            setOnClickListener {
                val intent = Intent(this@RideOverlayService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                switchToBubble()
            }
        }
        topIconContainer.addView(android.widget.ImageView(this).apply {
            setImageResource(com.girocerto.app.R.drawable.ic_girocerto_brand)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(dpToPx(72), dpToPx(72), Gravity.CENTER))

        root.addView(topIconContainer, FrameLayout.LayoutParams(dpToPx(84), dpToPx(84), Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        return root
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. MODO CARD COMPLETO DE OFERTA (Ao receber chamada de corrida)
    // ──────────────────────────────────────────────────────────────────────────

    private fun showOfferCardMode() {
        if (!OverlayController.canDrawOverlays(this)) return

        if (currentMode == Mode.OFFER_CARD && currentView?.isAttachedToWindow == true) {
            renderRecentOffers()
            return
        }

        currentMode = Mode.OFFER_CARD
        removeCurrentViewOnly()

        val card = createOfferCardView()
        currentView = card

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val cardWidth = dpToPx(440).coerceAtMost(screenWidth - dpToPx(24))

        val layoutParams = getWindowLayoutParams(
            width = cardWidth,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = 0,
            y = 0
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; x = 0; y = panelTop() }
        params = layoutParams

        try {
            windowHost.attach(card, layoutParams)
            OverlayController.setOverlayActive(true)
            Log.i("GiroOverlay", "Window attached: " + currentMode.name)
        } catch (error: Exception) {
            Log.e("GiroOverlay", "Window attachment failed: " + currentMode.name + " / " + error.javaClass.simpleName)
            removeOverlay()
            stopSelf()
            return
        }

        renderRecentOffers()
        card.alpha = 0f; card.translationY = dpToPx(8).toFloat()
        card.animate().alpha(1f).translationY(0f).setDuration(180).start()
    }

    private fun panelTop(): Int {
        val fallback = resources.getIdentifier("status_bar_height", "dimen", "android")
            .let { if (it > 0) resources.getDimensionPixelSize(it) else dpToPx(24) }
        val inset = windowHost.topSystemInset(fallback)
        return inset + dpToPx(8)
    }

    private fun panelHeight() = ((resources.displayMetrics.heightPixels - panelTop()) * .50f).toInt()

    private fun panelSurface() = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(palette("#243535"), palette("#101818"))).apply {
        cornerRadius = dpToPx(20).toFloat()
        setStroke(dpToPx(1), palette("#4B5563"))
    }

    private fun label(value: String, size: Float = 12f) = TextView(this).apply {
        text = value; textSize = size; includeFontPadding = false
        typeface = resources.getFont(com.girocerto.app.R.font.poppins_medium)
        setTextColor(palette("#F9FAFB"))
    }

    private fun iconAction(icon: Int, description: String, click: () -> Unit) = android.widget.ImageButton(this).apply {
        setImageResource(icon); contentDescription = description
        imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        setPadding(dpToPx(13), dpToPx(13), dpToPx(13), dpToPx(13))
        background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x40FFFFFF),
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#2563EB"), Color.parseColor("#1D4ED8"))).apply {
                cornerRadius = dpToPx(14).toFloat(); setStroke(dpToPx(1), Color.parseColor("#60A5FA"))
            }, null)
        elevation = dpToPx(2).toFloat()
        setOnClickListener { click() }
    }

    private fun createOfferCardView(): LinearLayout {
        val root = object : LinearLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(panelHeight(), View.MeasureSpec.AT_MOST))
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(8))
            contentDescription = "Análise da oferta"
            background = panelSurface(); elevation = dpToPx(8).toFloat()
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(android.widget.ImageView(this).apply {
            setImageResource(com.girocerto.app.R.drawable.ic_launcher_monochrome)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(
                if (com.girocerto.app.theme.AppearancePreferences.isDark(this@RideOverlayService)) "#93B4FF" else "#1D4ED8"))
            contentDescription = "Giro Certo"
        }, LinearLayout.LayoutParams(dpToPx(24), dpToPx(24)).apply { rightMargin = dpToPx(8) })
        header.addView(label("Giro Certo", 16f), LinearLayout.LayoutParams(0, -2, 1f))
        // 24dp visible close control (half size), with a 48dp touch target.
        val close = FrameLayout(this).apply {
            contentDescription = "Recolher painel"
            setOnClickListener { switchToBubble() }
            addView(iconAction(com.girocerto.app.R.drawable.ic_close_panel, "") { switchToBubble() }.apply {
                setPadding(dpToPx(5), dpToPx(5), dpToPx(5), dpToPx(5))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(dpToPx(24), dpToPx(24), Gravity.CENTER))
        }
        header.addView(close, LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)))
        root.addView(header)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; tag = "recentOffers" }
        root.addView(android.widget.ScrollView(this).apply { addView(list); isFillViewport = false }, LinearLayout.LayoutParams(-1, -2))
        return root
    }

    private var renderedEntries: List<RecentOffers.Entry> = emptyList()
    private fun renderRecentOffers() {
        val list = currentView?.findViewWithTag<LinearLayout>("recentOffers") ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        val entries = recentOffers.active(now)
        if (entries != renderedEntries || list.childCount == 0) {
            list.removeAllViews()
            entries.forEach { entry -> list.addView(offerRow(entry), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpToPx(10) }) }
            renderedEntries = entries
        }
        entries.forEachIndexed { index, entry ->
            list.getChildAt(index)?.findViewWithTag<android.widget.ProgressBar>("offerLifetime")?.progress = (entry.remainingFraction(now) * 1000).toInt()
        }
    }

    private fun offerRow(entry: RecentOffers.Entry): View {
        val analysis = entry.analysis
        val offer = analysis.offer
        val dark = com.girocerto.app.theme.AppearancePreferences.isDark(this)
        val gain = Color.parseColor(if (dark) "#6EE7B7" else "#087443")
        val loss = Color.parseColor(if (dark) "#FDA4AF" else "#B42336")
        val warning = Color.parseColor(if (dark) "#FDE68A" else "#885600")
        val decisionColor = when (analysis.trafficLight) { TrafficLightStatus.GREEN -> gain; TrafficLightStatus.RED -> loss; else -> warning }
        val decision = when (analysis.trafficLight) {
            TrafficLightStatus.GREEN -> "Compensa"
            TrafficLightStatus.RED -> "Prejuízo"
            else -> "Atenção"
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelSurface()
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
        }
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val icon = when {
            offer.appName.contains("uber", true) -> com.girocerto.app.R.drawable.platform_uber
            offer.appName.contains("99") || offer.appName.contains("didi", true) -> com.girocerto.app.R.drawable.platform_ninety_nine
            offer.appName.contains("ifood", true) -> com.girocerto.app.R.drawable.platform_ifood
            else -> com.girocerto.app.R.drawable.ic_launcher_monochrome
        }
        heading.addView(android.widget.ImageView(this).apply {
            setImageResource(icon); contentDescription = "Aplicativo detectado: ${offer.appName}"
        }, LinearLayout.LayoutParams(dpToPx(24), dpToPx(24)).apply { rightMargin = dpToPx(8) })
        heading.addView(label(String.format(Locale.GERMANY, "R$ %.2f", offer.fare), 18f).apply { paint.isFakeBoldText = true },
            LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(label(decision, 12f).apply {
            setTextColor(if (analysis.trafficLight == TrafficLightStatus.YELLOW || analysis.trafficLight == TrafficLightStatus.INVALID) Color.BLACK else Color.WHITE)
            setPadding(dpToPx(8), dpToPx(5), dpToPx(8), dpToPx(5))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(when (analysis.trafficLight) { TrafficLightStatus.GREEN -> "#087443"; TrafficLightStatus.RED -> "#B42336"; else -> "#FDE68A" }))
                cornerRadius = dpToPx(20).toFloat()
            }
        })
        card.addView(heading)
        val body = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val information = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val measures = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun measure(iconId: Int, text: String) {
            val group = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            group.addView(android.widget.ImageView(this).apply {
                setImageResource(iconId); imageTintList = android.content.res.ColorStateList.valueOf(palette("#B5C9C7"))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dpToPx(16), dpToPx(16)).apply { rightMargin = dpToPx(4) })
            group.addView(label(text, 16f))
            measures.addView(group, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val time = if (offer.durationMinutes <= 0) "— min" else if (offer.addedMinutes > 0)
            "${offer.durationMinutes - offer.addedMinutes}+${offer.addedMinutes} min" else "${offer.durationMinutes} min"
        measure(com.girocerto.app.R.drawable.ic_route_distance, String.format(Locale.GERMANY, "%.1f km", analysis.totalDistanceKm))
        measure(com.girocerto.app.R.drawable.ic_trip_clock, time)
        information.addView(measures)
        val metrics = LinearLayout(this)
        val amounts = listOf(String.format(Locale.GERMANY, "%.2f", analysis.grossPerKm),
            if (offer.durationMinutes > 0) String.format(Locale.GERMANY, "%.2f", analysis.grossPerHour) else "—",
            String.format(Locale.GERMANY, "%.2f", analysis.estimatedProfit))
        listOf("R$/km", "R$/hora", "Lucro Médio").forEachIndexed { index, title ->
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            column.addView(label(amounts[index], 16f).apply {
                setTextColor(if (index != 2) decisionColor else if (analysis.estimatedProfit > 0) gain else if (analysis.estimatedProfit < 0) loss else warning)
                contentDescription = listOf("Ganho por quilômetro", "Rendimento por hora", "Lucro estimado")[index]
            })
            column.addView(label(title, 11f))
            metrics.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
        }
        information.addView(metrics, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpToPx(10) })
        body.addView(information, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(iconAction(com.girocerto.app.R.drawable.ic_open_app, "Abrir ${offer.appName}") { openPlatform(offer) },
            LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)).apply { leftMargin = dpToPx(8) })
        card.addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpToPx(12) })

        // Botão Aceitar Corrida com clique automatizado e registro contábil imediato
        val acceptBtn = Button(this).apply {
            text = "Aceitar Corrida • R$ " + String.format(Locale.GERMANY, "%.2f", offer.fare)
            textSize = 13f
            paint.isFakeBoldText = true
            setTextColor(Color.WHITE)
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#087443"), Color.parseColor("#10B981"))).apply {
                cornerRadius = dpToPx(20).toFloat()
            }
            elevation = dpToPx(2).toFloat()
            setOnClickListener {
                acceptAndRecordOffer(analysis)
            }
        }
        card.addView(acceptBtn, LinearLayout.LayoutParams(-1, dpToPx(40)).apply { topMargin = dpToPx(8) })
        card.addView(android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            tag = "offerLifetime"; max = 1000; progress = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1D4ED8"))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(palette("#4B5563"))
            contentDescription = "Tempo restante da oferta"
        }, LinearLayout.LayoutParams(-1, dpToPx(4)).apply { topMargin = dpToPx(12) })
        return card
    }

    private fun acceptAndRecordOffer(analysis: RideAnalysis) {
        val offer = analysis.offer
        // 1. Traz o aplicativo de motorista para o primeiro plano
        openPlatform(offer)
        // 2. Dispara o clique automático de aceitar via acessibilidade com retentativas
        val targetPkg = offer.sourcePackage
        var attempts = 0
        val maxAttempts = 8
        val clickRunnable = object : Runnable {
            override fun run() {
                val success = com.girocerto.monitoring.GiroCertoAccessibilityService.requestAcceptOffer(targetPkg)
                if (!success && attempts < maxAttempts) {
                    attempts++
                    autoDismissHandler.postDelayed(this, 300)
                }
            }
        }
        autoDismissHandler.postDelayed(clickRunnable, 250)
        // 3. Salva a corrida no histórico e registra no fechamento diário
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            acceptedRideRecorder.record(analysis)
        }
        Toast.makeText(this, "Aceitando corrida...", Toast.LENGTH_SHORT).show()
        switchToBubble()
    }

    private fun openPlatform(offer: RideOffer) {
        val candidates = buildList {
            if (offer.sourcePackage.isNotBlank()) add(offer.sourcePackage)
            addAll(when {
                offer.appName.contains("uber", true) -> listOf("com.ubercab.driver", "com.ubermoto.driver", "com.ubereats.driver")
                offer.appName.contains("99") || offer.appName.contains("didi", true) -> listOf("com.taxis99", "com.app99.driver", "com.didiglobal.food.driver", "com.didiglobal.driver")
                offer.appName.contains("ifood", true) -> listOf("br.com.ifood.driver.app", "br.com.ifood.driver")
                else -> emptyList()
            })
        }.distinct()
        for (pkg in candidates) {
            // 1. Try standard launcher intent (which works for Uber, 99, etc.)
            try {
                val launch = packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
                    switchToBubble()
                    return
                }
            } catch (error: Exception) {
                com.girocerto.monitoring.ReaderDiagnostics.record("Abrir plataforma launchIntent $pkg", error)
            }
            // 2. Try LaunchIntentSender (Android 13+)
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    val sender = packageManager.getLaunchIntentSenderForPackage(pkg)
                    if (sender != null) {
                        val options = if (Build.VERSION.SDK_INT >= 34) android.app.ActivityOptions.makeBasic().apply {
                            setPendingIntentBackgroundActivityStartMode(if (Build.VERSION.SDK_INT >= 36)
                                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                            else android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        }.toBundle() else null
                        startIntentSender(sender, null, 0, 0, 0, options)
                        switchToBubble()
                        return
                    }
                }
            } catch (error: Exception) {
                com.girocerto.monitoring.ReaderDiagnostics.record("Abrir plataforma intentSender $pkg", error)
            }
        }
        Toast.makeText(this, "Não foi possível abrir ${offer.appName}. Confira se o aplicativo está instalado.", Toast.LENGTH_LONG).show()
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
                                windowHost.update(root, currentParams)
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
        return windowHost.layoutParams(width, height, x, y, bubbleSize(), dpToPx(180))
    }

    private fun removeCurrentViewOnly() {
        hideTrashTarget()
        if (currentView != null) {
            try {
                currentView?.let(windowHost::remove)
            } catch (_: Exception) {}
            currentView = null
        }
    }

    private fun removeOverlay() {
        autoDismissHandler.removeCallbacks(expiryTick)
        recentOffers.clear(); currentAnalysis = null
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
            "#101818" -> if (dark) "#0B1120" else "#F5F7FB"
            "#243535" -> if (dark) "#121B2C" else "#FFFFFF"
            "#304242" -> if (dark) "#1A263A" else "#EEF2F7"
            "#4B5563" -> if (dark) "#2B3A52" else "#D6DEE9"
            "#F9FAFB" -> if (dark) "#F4F7FD" else "#0F172A"
            "#B5C9C7" -> if (dark) "#9BA9BE" else "#5B6678"
            "#FACC15" -> if (dark) "#F4F7FD" else "#0F172A"
            "#EF4444" -> if (dark) "#F4F7FD" else "#0F172A"
            "#6B7280" -> if (dark) "#9BA9BE" else "#5B6678"
            "#19BFB0", "#22C55E", "#10B981", "#06B6D4", "#EA1D2C", "#F97316", "#F59E0B" -> "#1D4ED8"
            else -> hex
        }
        return Color.parseColor(color)
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun bubbleSize() = (dpToPx(64) * .8f).toInt()

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

