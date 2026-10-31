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

    fun permits(lines: List<String>): Boolean {
        val normalized = OfferTextNormalization.lines(lines)
        val text = normalized.joinToString(" ").lowercase(java.util.Locale.ROOT)
        if (Regex("""(?i)\b(seu saldo|rota atual)\b""").containsMatchIn(text)) return false
        val matches = Regex("""(?i)(?:R\$|BRL)\s*([0-9]{1,4}[.,][0-9]{2})(?![0-9.,])""")
            .findAll(text).toList()
        val amounts = matches.map { it.groupValues[1].replace(',', '.').toDouble() }
        val maxFare = amounts.maxOrNull() ?: return false
        val included = AccessibilityOfferReader.includedBonusFares(normalized)
        val prices = amounts.filterNot { fare ->
            fare < maxFare && included.any { kotlin.math.abs(it - fare) < 0.001 }
        }.toSet()
        if (prices.size > 1) return false
        return Regex("""(?i)\b(aceitar|selecionar|nova entrega|nova corrida|nova viagem|solicitação de viagem|solicitação de entrega|envios moto|entrega food|pedido|pedidos)\b""").containsMatchIn(text)
    }
}

/**
 * Local OCR of the screen display using ML Kit, aligned with Gigu's real-time capture architecture.
 * Takes a screenshot of Display.DEFAULT_DISPLAY, crops to active application bounds,
 * filters status bar balance, extracts offer metrics, and immediately delivers calculation.
 */
class OfferOcrReader(
    private val service: AccessibilityService,
    private val status: (String) -> Unit,
    private val offerReady: (OfferReaderResult, String) -> Unit
) {
    data class Target(val id: Int, val bounds: Rect, val masks: List<Rect>)

    private val recognizerDelegate = lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val recognizer get() = recognizerDelegate.value
    private val handler = Handler(Looper.getMainLooper())
    private var retry: Runnable? = null
    private var busy = false
    private var closed = false
    private var lastAttempt = -2_000L
    private var lastStatus = ""
    private var lastStatusTime = -5_000L

    fun invalidate(pkg: String) {
        // No-op for epoch wiping, avoiding discarding valid in-flight OCR results
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
        val windows = try { service.windows.sortedByDescending { it.layer } } catch (_: Exception) { emptyList() }
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION &&
                window.type != AccessibilityWindowInfo.TYPE_SYSTEM) continue
            val root = window.root
            val matches = try {
                root?.packageName?.toString() == pkg
            } catch (_: Exception) {
                false
            } finally {
                @Suppress("DEPRECATION")
                root?.recycle()
            }
            if (!matches) continue
            val bounds = Rect().also { window.getBoundsInScreen(it) }
            if (bounds.isEmpty) continue
            val masks = windows.filter { it.layer > window.layer }.mapNotNull { above ->
                val area = Rect().also { above.getBoundsInScreen(it) }
                if (Rect.intersects(bounds, area)) area else null
            }
            if (OcrWindowRegion.visibleParts(bounds.region(), masks.map { it.region() }).isNotEmpty()) {
                return Target(window.id, bounds, masks)
            }
        }
        return null
    }

    fun isObservable(pkg: String): Boolean = Build.VERSION.SDK_INT >= 30 && allowed(pkg)

    fun read(pkg: String) { attempt(pkg, 1) }

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
        busy = true
        lastAttempt = now
        report("OCR: analisando tela da oferta...")

        val activeTarget = findTarget(pkg)

        try {
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    var hardware: Bitmap? = null
                    var mutableSource: Bitmap? = null
                    var crop: Bitmap? = null
                    var submitted = false
                    try {
                        val colorSpace = result.colorSpace
                        hardware = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                        val hw = hardware ?: throw IllegalStateException("wrapHardwareBuffer returned null")

                        // Hardware bitmaps cannot be modified; draw onto a mutable ARGB_8888 bitmap.
                        mutableSource = Bitmap.createBitmap(hw.width, hw.height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(mutableSource)
                        canvas.drawBitmap(hw, 0f, 0f, null)

                        // Mask out floating windows above the app (e.g. Giro Certo floating bubble)
                        val paint = Paint().apply { color = Color.BLACK }
                        val targetBounds = activeTarget?.bounds ?: Rect(0, 0, mutableSource.width, mutableSource.height)
                        val targetRegion = targetBounds.region()
                        val maskRegions = activeTarget?.masks?.map { it.region() } ?: emptyList()
                        OcrWindowRegion.masks(targetRegion, maskRegions, false, mutableSource.width, mutableSource.height).forEach {
                            canvas.drawRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat(), paint)
                        }

                        // Determine target bounds (fallback to full display if window root wasn't accessible)
                        val left = targetBounds.left.coerceIn(0, mutableSource.width)
                        val top = targetBounds.top.coerceIn(0, mutableSource.height)
                        val right = targetBounds.right.coerceIn(left, mutableSource.width)
                        val bottom = targetBounds.bottom.coerceIn(top, mutableSource.height)
                        val width = right - left
                        val height = bottom - top

                        crop = if (width > 0 && height > 0 && (width < mutableSource.width || height < mutableSource.height)) {
                            Bitmap.createBitmap(mutableSource, left, top, width, height)
                        } else {
                            mutableSource
                        }

                        val bitmap = checkNotNull(crop)
                        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                            .addOnSuccessListener { recognized ->
                                try {
                                    val lines = recognized.textBlocks.flatMap { it.lines }
                                        .filter { OfferOcrContext.acceptsLine(it.text, it.boundingBox?.top, bitmap.height) }
                                        .map { it.text }
                                    val parsed = AccessibilityOfferReader.parse(lines, pkg)
                                    if (OfferOcrContext.permits(lines) && parsed.isComplete) {
                                        report("OCR: oferta detectada com sucesso (${parsed.appName})")
                                        // Gigu immediate delivery: deliver directly without artificial double-read delay
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
                        reportFailure("preparação da imagem", error)
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

            // Always take screenshot of Display.DEFAULT_DISPLAY like Gigu does
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, callback)
        } catch (error: Exception) {
            reportFailure("captura", error)
            finish()
        }
    }

    private fun Rect.region() = OcrRegion(left, top, right, bottom)

    private fun reportFailure(stage: String, error: Exception) {
        Log.w("GiroReader", ReaderDiagnostics.record(stage, error))
        report("OCR: falha temporária em $stage; nova tentativa automática")
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
