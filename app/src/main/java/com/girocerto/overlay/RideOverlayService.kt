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
    private var dockRight = true

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private var currentAnalysis: RideAnalysis? = null
    private val recentOffers = RecentOffers()
    private val expiryTick = object : Runnable {
        override fun run() {
            val entries = recentOffers.active(android.os.SystemClock.elapsedRealtime())
            currentAnalysis = entries.firstOrNull()?.analysis
            if (currentMode == Mode.OFFER_CARD) {
                if (entries.isEmpty()) switchToBubble() else renderRecentOffers()
            }
            if (entries.isNotEmpty()) autoDismissHandler.postDelayed(this, 1000)
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
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
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
                currentAnalysis = RideCalculator.calculate(offer, settings).copy(decisionReason = reason)

                if (recentOffers.add(checkNotNull(currentAnalysis), android.os.SystemClock.elapsedRealtime())) {
                    showOfferCardMode()
                    autoDismissHandler.removeCallbacks(expiryTick)
                    autoDismissHandler.postDelayed(expiryTick, 1000)
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
                intArrayOf(Color.parseColor("#8066FF"), Color.parseColor("#371BE4"), Color.parseColor("#241078"))).apply {
                shape = GradientDrawable.OVAL
                setStroke(dpToPx(2), palette("#C9D5E6"))
            }
            elevation = dpToPx(10).toFloat()
        }
        bubble.addView(android.widget.ImageView(this).apply {
            setImageResource(com.girocerto.app.R.drawable.ic_launcher_monochrome)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams((dpToPx(96) * .8f).toInt(), (dpToPx(96) * .8f).toInt(), Gravity.CENTER))
        bubble.setOnClickListener {
            currentAnalysis = recentOffers.active(android.os.SystemClock.elapsedRealtime()).firstOrNull()?.analysis
            currentAnalysis?.let { analysis ->
                showOfferCardMode()
            } ?: showQuickMenuMode()
        }
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
                            dockRight = layout.x + view.width / 2 >= resources.displayMetrics.widthPixels / 2
                            layout.x = if (!dockRight) edge
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

        val collapseBtn = iconAction(com.girocerto.app.R.drawable.ic_close_panel, "Recolher painel") { switchToBubble() }

        header.addView(title)
        header.addView(collapseBtn, LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)))
        root.addView(header)

        // Status de monitoramento
        val statusText = TextView(this).apply {
            typeface = resources.getFont(com.girocerto.app.R.font.poppins_medium)
            gravity = Gravity.CENTER
            tag = "readerStatus"
            text = "Nenhuma oferta recente"
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
        // Expanded panels stay at the top; only the bubble can be dragged.

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
            checkNotNull(windowManager).addView(card, layoutParams)
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
        val inset = if (Build.VERSION.SDK_INT >= 30) windowManager?.currentWindowMetrics?.windowInsets
            ?.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.displayCutout())?.top ?: 0
        else resources.getIdentifier("status_bar_height", "dimen", "android").let { if (it > 0) resources.getDimensionPixelSize(it) else dpToPx(24) }
        return inset + dpToPx(8)
    }

    private fun panelHeight() = ((resources.displayMetrics.heightPixels - panelTop()) * .30f).toInt()

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
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#6246F0"), Color.parseColor("#371BE4"))).apply {
                cornerRadius = dpToPx(14).toFloat(); setStroke(dpToPx(1), Color.parseColor("#8D7BF4"))
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
        val title = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title.addView(label("Ofertas recentes · 30 s", 13f))
        title.addView(label("Disponibilidade não garantida", 10f))
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(iconAction(com.girocerto.app.R.drawable.ic_close_panel, "Recolher painel") { switchToBubble() },
            LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)))
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
            entries.forEach { entry -> list.addView(offerRow(entry), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpToPx(6) }) }
            renderedEntries = entries
        }
        entries.forEachIndexed { index, entry ->
            val decision = when (entry.analysis.trafficLight) {
                TrafficLightStatus.GREEN -> "Compensa"
                TrafficLightStatus.YELLOW -> "Atenção"
                TrafficLightStatus.RED -> "Não compensa"
                else -> "Dados incompletos"
            }
            list.getChildAt(index)?.findViewWithTag<TextView>("age")?.text = "$decision · vista há ${(now - entry.firstSeen) / 1000} s"

        }
    }

    private fun offerRow(entry: RecentOffers.Entry): View {
        val analysis = entry.analysis
        val offer = analysis.offer
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = panelSurface()
            setPadding(dpToPx(8), dpToPx(6), dpToPx(4), dpToPx(6))
        }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val icon = when {
            offer.appName.contains("uber", true) -> com.girocerto.app.R.drawable.platform_uber
            offer.appName.contains("99") || offer.appName.contains("didi", true) -> com.girocerto.app.R.drawable.platform_ninety_nine
            offer.appName.contains("ifood", true) -> com.girocerto.app.R.drawable.platform_ifood
            else -> com.girocerto.app.R.drawable.ic_launcher_monochrome
        }
        heading.addView(android.widget.ImageView(this).apply {
            setImageResource(icon); contentDescription = "Aplicativo detectado: ${offer.appName}"
        }, LinearLayout.LayoutParams(dpToPx(22), dpToPx(22)).apply { rightMargin = dpToPx(6) })
        val time = if (offer.durationMinutes <= 0) "tempo —" else if (offer.addedMinutes > 0)
            "${offer.durationMinutes - offer.addedMinutes} + ${offer.addedMinutes} min" else "${offer.durationMinutes} min"
        heading.addView(label(String.format(Locale.GERMANY, "%s · R$ %.2f", offer.appName, offer.fare), 13f), LinearLayout.LayoutParams(0, -2, 1f))
        info.addView(heading)
        info.addView(label(String.format(Locale.GERMANY, "%.1f km · %s", analysis.totalDistanceKm, time), 12f))
        val metrics = LinearLayout(this)
        val dark = com.girocerto.app.theme.AppearancePreferences.isDark(this)
        val color = Color.parseColor(when (analysis.trafficLight) {
            TrafficLightStatus.GREEN -> if (dark) "#6EE7B7" else "#087443"
            TrafficLightStatus.RED -> if (dark) "#FDA4AF" else "#B42336"
            else -> if (dark) "#FDE68A" else "#885600"
        })
        val amounts = listOf(String.format(Locale.GERMANY, "%.2f", analysis.grossPerKm),
            if (offer.durationMinutes > 0) String.format(Locale.GERMANY, "%.2f", analysis.grossPerHour) else "—",
            String.format(Locale.GERMANY, "%.2f", analysis.estimatedProfit))
        listOf("R$/km", "R$/hora", "Lucro est.").forEachIndexed { index, title ->
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            column.addView(label(amounts[index], 16f).apply {
                setTextColor(color); maxLines = 1
                contentDescription = listOf("Ganho por quilômetro", "Rendimento por hora", "Lucro estimado")[index]
                setAutoSizeTextTypeUniformWithConfiguration(12, 16, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }, LinearLayout.LayoutParams(-1, -2))
            column.addView(label(title, 11f))
            metrics.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
        }
        info.addView(metrics, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpToPx(4) })
        info.addView(label("", 10f).apply { tag = "age" })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(iconAction(com.girocerto.app.R.drawable.ic_open_app, "Abrir ${offer.appName}") { openPlatform(offer) },
            LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)).apply { leftMargin = dpToPx(4) })
        return row
    }

    private fun openPlatform(offer: RideOffer) {
        val candidates = if (offer.sourcePackage.isNotBlank()) listOf(offer.sourcePackage) else when {
            offer.appName.contains("uber", true) -> listOf("com.ubercab.driver", "com.ubermoto.driver", "com.ubereats.driver")
            offer.appName.contains("99") || offer.appName.contains("didi", true) -> listOf("com.taxis99", "com.app99.driver", "com.didiglobal.food.driver", "com.didiglobal.driver")
            offer.appName.contains("ifood", true) -> listOf("br.com.ifood.driver.app", "br.com.ifood.driver")
            else -> emptyList()
        }
        val intent = candidates.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (intent == null) { Toast.makeText(this, "Aplicativo indisponível neste aparelho.", Toast.LENGTH_SHORT).show(); return }
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); switchToBubble() }
            .onFailure { Toast.makeText(this, "Não foi possível abrir o aplicativo.", Toast.LENGTH_SHORT).show() }
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
            this.x = x.coerceIn(0, (resources.displayMetrics.widthPixels - width.coerceAtLeast(bubbleSize())).coerceAtLeast(0))
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
            "#101818" -> if (dark) "#19152D" else "#F9F8FF"
            "#243535" -> if (dark) "#302750" else "#FFFFFF"
            "#304242" -> "#483B75"
            "#4B5563" -> if (dark) "#65568B" else "#D9D2F2"
            "#F9FAFB" -> if (dark) "#FFFFFF" else "#10151D"
            "#B5C9C7" -> if (dark) "#CED8E7" else "#40536D"
            "#FACC15" -> "#DEE5EF"
            "#EF4444" -> "#080D15"
            "#6B7280" -> "#40536D"
            "#19BFB0", "#22C55E", "#10B981", "#06B6D4", "#EA1D2C", "#F97316", "#F59E0B" -> "#371BE4"
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

