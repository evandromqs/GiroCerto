package com.girocerto.monitoring

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityWindowInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object OfferOcrContext {
    // Synthetic-testable rule, supported by Gigu's decompiled reference (el0.b):
    // Ignore currency amounts in the top 7.5% of the screen (driver's wallet/balance).
    fun acceptsLine(text: String, top: Int?, height: Int): Boolean {
        if (top == null || height <= 0) return true
        val monetary = text.contains("$") || text.contains("€") || text.contains("BRL", true)
        return !monetary || top > (height * 0.075).toInt()
    }

    // The header is relative to the display, not the first 7.5% of a small offer popup.
    fun acceptsCapturedLine(text: String, top: Int?, imageHeight: Int,
                            windowTop: Int, windowHeight: Int, displayHeight: Int): Boolean {
        val screenTop = top?.let {
            windowTop + (it.toDouble() * windowHeight / imageHeight.coerceAtLeast(1)).toInt()
        }
        return acceptsLine(text, screenTop, displayHeight)
    }

    fun permits(lines: List<String>, packageName: String = ""): Boolean {
        val normalized = AccessibilityOfferReader.joinAdjacentMeasurementNodes(OfferTextNormalization.lines(DeliveryOfferRules.prepare(lines, packageName)))
        val text = normalized.joinToString(" ").lowercase(java.util.Locale.ROOT)
        if (Regex("""(?i)\b(seu saldo|rota atual)\b""").containsMatchIn(text)) return false
        val matches = Regex("""(?i)(?:R\$|BRL)\s*((?:[0-9]{1,3}(?:\.[0-9]{3})+,[0-9]{2}|[0-9]{1,4}[.,][0-9]{2}))(?![0-9.,])""")
            .findAll(text).toList()
        if (matches.size != Regex("(?i)(R\\$|BRL)").findAll(text).count()) return false
        val amounts = matches.map {
            val value = it.groupValues[1]
            (if (',' in value) value.replace(".", "").replace(',', '.') else value).toDouble()
        }
        val maxFare = amounts.maxOrNull() ?: return false
        val included = AccessibilityOfferReader.includedBonusFares(normalized)
        val prices = amounts.filterNot { fare ->
            fare < maxFare && included.any { kotlin.math.abs(it - fare) < 0.001 }
        }.toSet()
        if (prices.size > 1) return false
        return OfferScreenContext.hasOfferAction(normalized)
    }
}

/**
 * Local OCR of an identified application window. Frames are held only in memory.
 */
class OfferOcrReader(
    private val service: AccessibilityService,
    private val status: (String) -> Unit,
    private val offerReady: (OfferReaderResult, String) -> Unit,
    private val windowOwners: WindowOwnerHints = WindowOwnerHints()
) {
    data class Target(val id: Int, val bounds: Rect, val masks: List<Rect>)

    private val recognizerDelegate = lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val recognizer get() = recognizerDelegate.value
    private val handler = Handler(Looper.getMainLooper())
    private val preferredWindows = mutableMapOf<String, Int>()
    private var retry: Runnable? = null
    private var busy = false
    private var closed = false
    private val epochs = OfferReadEpoch(maximumAgeMs = 5_000L)
    private var lastAttempt = -2_000L
    private var lastStatus = ""
    private var lastStatusTime = -5_000L

    fun invalidate(pkg: String) {
        epochs.invalidate(pkg)
    }

    private fun report(message: String) {
        val now = SystemClock.elapsedRealtime()
        if (!closed && (message != lastStatus || now - lastStatusTime >= 5_000L)) {
            lastStatus = message
            lastStatusTime = now
            status(message)
        }
    }

    private fun allowed(pkg: String): Boolean {
        val policy = CopilotPreferences(service).read()
        return !closed && policy.ocrEnabled && policy.monitors(pkg) && pkg in GiroCertoAccessibilityService.TARGET_PACKAGES
    }

    private fun findTarget(pkg: String): Target? {
        if (!allowed(pkg)) return null
        val windows = try { service.windows.sortedWith(compareByDescending<AccessibilityWindowInfo> { it.id == preferredWindows[pkg] }.thenByDescending { it.layer }) } catch (_: Exception) { emptyList() }
        // If the emitting popup still exists, never substitute a background
        // activity from the same package when that popup cannot be captured.
        val preferred = windows.firstOrNull { it.id == preferredWindows[pkg] }
        for (window in if (preferred != null) listOf(preferred) else windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION &&
                window.type != AccessibilityWindowInfo.TYPE_SYSTEM) continue
            val root = window.root
            val matches = try {
                windowOwners.owner(window.id, root?.packageName?.toString(), SystemClock.elapsedRealtime()) == pkg
            } catch (_: Exception) {
                false
            } finally {
                @Suppress("DEPRECATION")
                root?.recycle()
            }
            if (!matches) continue
            val bounds = Rect().also { window.getBoundsInScreen(it) }
            if (bounds.isEmpty) continue
            val masks = windows.filter { it.layer > window.layer &&
                !(Build.VERSION.SDK_INT >= 34 && it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)
            }.mapNotNull { above ->
                val area = Rect().also { above.getBoundsInScreen(it) }
                if (Rect.intersects(bounds, area)) area else null
            }
            if (OcrWindowRegion.visibleParts(bounds.region(), masks.map { it.region() }).isNotEmpty()) {
                return Target(window.id, bounds, masks)
            }
        }
        return null
    }

    fun isObservable(pkg: String): Boolean = Build.VERSION.SDK_INT >= 30 && findTarget(pkg) != null

    fun read(pkg: String, windowId: Int = -1) {
        if (windowId >= 0) preferredWindows[pkg] = windowId
        attempt(pkg, 2)
    }

    private fun attempt(pkg: String, remaining: Int) {
        if (closed) return
        if (Build.VERSION.SDK_INT < 30) {
            report("OCR indisponível: exige Android 11")
            return
        }
        if (!allowed(pkg)) {
            report("OCR desativado ou aplicativo pausado")
            return
        }
        val now = SystemClock.elapsedRealtime()
        val wait = (500L - (now - lastAttempt)).coerceAtLeast(0L)
        if (busy || wait > 0) {
            if (remaining > 0 && retry == null) {
                retry = Runnable {
                    retry = null
                    attempt(pkg, remaining - 1)
                }
                handler.postDelayed(retry!!, if (busy) 500L else wait + 50L)
            }
            return
        }
        retry?.let { handler.removeCallbacks(it) }
        retry = null
        val activeTarget = findTarget(pkg)
        if (activeTarget == null) {
            report("OCR: aguardando janela identificada do aplicativo")
            return
        }
        val generation = epochs.current(pkg)
        val windowCapture = Build.VERSION.SDK_INT >= 34
        busy = true
        lastAttempt = now
        report("OCR: analisando tela da oferta...")

        try {
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    var hardware: Bitmap? = null
                    var mutableSource: Bitmap? = null
                    var crop: Bitmap? = null
                    var submitted = false
                    var preparationStage = "leitura do buffer da captura"
                    try {
                        val colorSpace = result.colorSpace
                        hardware = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                        val hw = hardware ?: throw IllegalStateException("wrapHardwareBuffer returned null")

                        // A software Canvas cannot draw a hardware bitmap. Copy its pixels first.
                        preparationStage = "conversão dos pixels da captura"
                        mutableSource = OcrBitmapPreparation.mutableCopy(hw)
                        preparationStage = "criação da superfície de imagem"
                        val canvas = Canvas(mutableSource)

                        // Mask out floating windows above the app (e.g. Giro Certo floating bubble)
                        val paint = Paint().apply { color = Color.BLACK }
                        preparationStage = "revalidação da janela capturada"
                        val current = findTarget(pkg)
                        if (current == null || current.id != activeTarget.id || current.bounds != activeTarget.bounds ||
                            !epochs.accepts(pkg, generation, now, SystemClock.elapsedRealtime())) return
                        val targetBounds = if (windowCapture) Rect(0, 0, mutableSource.width, mutableSource.height) else activeTarget.bounds
                        val targetRegion = targetBounds.region()
                        // Window captures exclude overlays; only display captures need masking.
                        val maskRegions = if (windowCapture) emptyList() else
                            (activeTarget.masks + current.masks).map { it.region() }
                        preparationStage = "máscara das sobreposições"
                        OcrWindowRegion.masks(targetRegion, maskRegions, false, mutableSource.width, mutableSource.height).forEach {
                            canvas.drawRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat(), paint)
                        }

                        // Never submit the whole display when the target bounds are invalid.
                        val left = targetBounds.left.coerceIn(0, mutableSource.width)
                        val top = targetBounds.top.coerceIn(0, mutableSource.height)
                        val right = targetBounds.right.coerceIn(left, mutableSource.width)
                        val bottom = targetBounds.bottom.coerceIn(top, mutableSource.height)
                        val width = right - left
                        val height = bottom - top
                        if (width <= 0 || height <= 0) {
                            report("OCR: limites da janela incompatíveis com captura")
                            return
                        }

                        preparationStage = "recorte da oferta"
                        crop = if (width < mutableSource.width || height < mutableSource.height) {
                            Bitmap.createBitmap(mutableSource, left, top, width, height)
                        } else {
                            mutableSource
                        }

                        val bitmap = checkNotNull(crop)
                        preparationStage = "inicialização do reconhecedor"
                        val localRecognizer = recognizer
                        preparationStage = "criação da entrada do OCR"
                        val inputImage = InputImage.fromBitmap(bitmap, 0)
                        preparationStage = "envio da imagem ao reconhecedor"
                        localRecognizer.process(inputImage)
                            .addOnSuccessListener { recognized ->
                                try {
                                    val latest = findTarget(pkg)
                                    if (latest == null || latest.id != activeTarget.id || latest.bounds != activeTarget.bounds ||
                                        !epochs.accepts(pkg, generation, now, SystemClock.elapsedRealtime())) {
                                        report("OCR: resultado descartado; janela mudou")
                                        return@addOnSuccessListener
                                    }
                                    val lines = recognized.textBlocks.flatMap { it.lines }
                                        .sortedWith(compareBy({ it.boundingBox?.top ?: Int.MAX_VALUE }, { it.boundingBox?.left ?: Int.MAX_VALUE }))
                                        .filter { OfferOcrContext.acceptsCapturedLine(it.text, it.boundingBox?.top, bitmap.height,
                                            activeTarget.bounds.top, activeTarget.bounds.height(), resourcesDisplayHeight()) }
                                        .map { it.text }
                                    val parsed = AccessibilityOfferReader.parse(lines, pkg)
                                    if (OfferOcrContext.permits(lines, pkg) && parsed.isComplete) {
                                        report("OCR: oferta detectada com sucesso (${parsed.appName})")
                                        offerReady(parsed.copy(source = OfferSource.OCR_FALLBACK), pkg)
                                    } else {
                                        report(if (!parsed.isComplete)
                                            "OCR: ${lines.size} textos; faltam ${parsed.missingFields.joinToString().ifEmpty { "dados da oferta" }}"
                                            else "OCR: valores ambíguos ou botão Aceitar não reconhecido")
                                    }
                                } catch (error: Exception) {
                                    reportFailure("interpretação da imagem", error)
                                }
                            }
                            .addOnFailureListener { reportFailure("reconhecimento local", it) }
                            .addOnCompleteListener {
                                if (crop !== mutableSource) crop.recycle()
                                mutableSource.recycle()
                                hardware.recycle()
                                buffer.close()
                                finish()
                            }
                        submitted = true
                    } catch (error: Exception) {
                        reportFailure(preparationStage, error)
                    } finally {
                        if (!submitted) {
                            if (crop !== mutableSource) crop?.recycle()
                            mutableSource?.recycle()
                            hardware?.recycle()
                            buffer.close()
                            finish()
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    report(when (errorCode) {
                        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
                            "OCR: Android não liberou captura. Desative e reative a acessibilidade do Giro Certo após atualizar."
                        AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
                            "OCR: esta janela protege o conteúdo contra captura"
                        else -> "OCR: captura indisponível (código $errorCode)"
                    })
                    finish()
                }
            }

            if (Build.VERSION.SDK_INT >= 34) service.takeScreenshotOfWindow(activeTarget.id, service.mainExecutor, callback)
            else service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, callback)
        } catch (error: Exception) {
            reportFailure("captura", error)
            finish()
        }
    }

    private fun Rect.region() = OcrRegion(left, top, right, bottom)

    private fun resourcesDisplayHeight() = service.resources.displayMetrics.heightPixels

    private fun reportFailure(stage: String, error: Exception) {
        Log.w("GiroReader", ReaderDiagnostics.record(stage, error))
        report("OCR: falha em $stage (${error.javaClass.simpleName}); copie o diagnóstico no Copiloto")
    }

    private fun finish() {
        busy = false
        if (closed && recognizerDelegate.isInitialized()) recognizer.close()
    }

    fun close() {
        closed = true
        retry?.let { handler.removeCallbacks(it) }
        retry = null
        if (!busy && recognizerDelegate.isInitialized()) recognizer.close()
    }
}
