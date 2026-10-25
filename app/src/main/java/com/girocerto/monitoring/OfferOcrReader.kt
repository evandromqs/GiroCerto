package com.girocerto.monitoring

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.Display
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object OfferOcrContext {
    // Synthetic-testable rule, supported by the shipped reference's header filtering.
    fun acceptsLine(text: String, top: Int?, height: Int): Boolean {
        if (top == null || height <= 0) return true
        val monetary = text.contains("$") || text.contains("€") || text.contains("BRL", true)
        return !monetary || top > (height * 0.075).toInt()
    }

    fun permits(lines: List<String>): Boolean {
        val text = lines.joinToString(" ").lowercase(java.util.Locale.ROOT)
        val currencyTokens = Regex("""(?i)(?:R\$|BRL)""").findAll(text).count()
        val matches = Regex("""(?i)(?:R\$|BRL)\s*([0-9]{1,4}[.,][0-9]{2})(?![0-9.,])""")
            .findAll(text).toList()
        val prices = matches.map { it.groupValues[1].replace(',', '.').toDouble() }.toSet()
        // Unknown currency formatting is ambiguous too: do not silently ignore a balance.
        if (matches.size != currencyTokens || prices.size != 1) return false
        return Regex("""\b(aceitar|selecionar|nova entrega|nova corrida|nova viagem|solicitação de viagem)\b""").containsMatchIn(text)
    }
}

/** Local OCR of one observable application window, never persisted. */
class OfferOcrReader(
    private val service: AccessibilityService,
    private val status: (String) -> Unit,
    private val offerReady: (OfferReaderResult, String) -> Unit
) {
    private data class Target(val id: Int, val bounds: android.graphics.Rect, val masks: List<android.graphics.Rect>)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var retry: Runnable? = null
    private var busy = false
    private var closed = false
    private var lastAttempt = -2_000L
    private var lastStatus = ""
    private var lastStatusTime = -5_000L

    private fun report(message: String) {
        val now = SystemClock.elapsedRealtime()
        if (!closed && (message != lastStatus || now - lastStatusTime >= 5_000L)) {
            lastStatus = message; lastStatusTime = now; status(message)
        }
    }
    private fun allowed(pkg: String): Boolean {
        val policy = CopilotPreferences(service).read()
        return !closed && policy.ocrEnabled && policy.monitors(pkg) && pkg in GiroCertoAccessibilityService.TARGET_PACKAGES
    }
    private fun target(pkg: String, expected: Target? = null): Target? {
        if (!allowed(pkg)) return null
        val windows = service.windows.sortedByDescending { it.layer }
        for (window in windows) {
            if (window.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION &&
                window.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM) continue
            if (expected != null && window.id != expected.id) continue
            val root = window.root ?: continue
            val matches = try { root.packageName?.toString() == pkg }
                finally { @Suppress("DEPRECATION") root.recycle() }
            if (!matches) continue
            val bounds = android.graphics.Rect().also { window.getBoundsInScreen(it) }
            if (bounds.isEmpty || (expected != null && bounds != expected.bounds)) continue
            val masks = windows.filter { it.layer > window.layer }.mapNotNull { above ->
                val area = android.graphics.Rect().also { above.getBoundsInScreen(it) }
                if (android.graphics.Rect.intersects(bounds, area)) area else null
            }
            // A notification or floating button only hides its own area, not the whole offer.
            if (OcrWindowRegion.visibleParts(bounds.region(), masks.map { it.region() }).isNotEmpty()) {
                return Target(window.id, bounds, masks)
            }

        }
        return null
    }
    fun isObservable(pkg: String): Boolean = Build.VERSION.SDK_INT >= 30 && target(pkg) != null
    fun read(pkg: String) { attempt(pkg, 2) }
    private fun attempt(pkg: String, remaining: Int) {
        if (closed) return
        if (Build.VERSION.SDK_INT < 30) { report("OCR indisponível: exige Android 11"); return }
        if (!allowed(pkg)) { report("OCR desativado ou aplicativo pausado"); return }
        val selected = target(pkg)
        if (selected == null) { report("OCR: janela alvo indisponível ou encoberta"); return }
        val now = SystemClock.elapsedRealtime()
        val wait = (2_000L - (now - lastAttempt)).coerceAtLeast(0L)
        if (busy || wait > 0) {
            report(if (busy) "OCR: processamento em andamento" else "OCR: aguardando intervalo de captura")
            if (remaining > 0 && retry == null) {
                retry = Runnable {
                    retry = null
                    if (target(pkg, selected) != null) attempt(pkg, remaining - 1)
                    else report("OCR: tentativa cancelada; janela mudou")
                }
                handler.postDelayed(retry!!, if (busy) 2_000L else wait + 50L)
            }
            return
        }
        retry?.let { handler.removeCallbacks(it) }; retry = null
        busy = true; lastAttempt = now
        report("OCR: lendo imagem local da janela da oferta")
        try {
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    var hardware: Bitmap? = null
                    var full: Bitmap? = null
                    var crop: Bitmap? = null
                    var submitted = false
                    try {
                        val current = target(pkg, selected)
                        if (current == null) { report("OCR: captura descartada; janela mudou"); return }
                        hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        full = hardware?.copy(Bitmap.Config.ARGB_8888, true)
                        val source = full ?: throw IllegalStateException("No bitmap")
                        // Exclude covers from both snapshots, including notifications and our bubble.
                        // API 34 window captures need coordinates relative to the target window.
                        val canvas = android.graphics.Canvas(source)
                        val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }
                        OcrWindowRegion.masks(selected.bounds.region(),
                            (selected.masks + current.masks).map { it.region() },
                            Build.VERSION.SDK_INT >= 34, source.width, source.height).forEach {
                            canvas.drawRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat(), paint)
                        }
                        if (Build.VERSION.SDK_INT >= 34) {
                            crop = source; full = null
                        } else {
                            val b = selected.bounds
                            if (b.left < 0 || b.top < 0 || b.right > source.width || b.bottom > source.height) {
                                report("OCR: limites da janela incompatíveis com captura"); return
                            }
                            crop = Bitmap.createBitmap(source, b.left, b.top, b.width(), b.height())
                            if (crop === source) full = null
                        }
                        val bitmap = checkNotNull(crop)
                        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                            .addOnSuccessListener { recognized ->
                                if (target(pkg, selected) != null) {
                                    val lines = recognized.textBlocks.flatMap { it.lines }
                                        .filter { OfferOcrContext.acceptsLine(it.text, it.boundingBox?.top, bitmap.height) }
                                        .map { it.text }
                                    val parsed = AccessibilityOfferReader.parse(lines, pkg)
                                    if (OfferOcrContext.permits(lines) && parsed.isComplete) {
                                        report("OCR: oferta completa reconhecida")
                                        offerReady(parsed.copy(source = OfferSource.OCR_FALLBACK), pkg)
                                    } else report(if (!parsed.isComplete)
                                        "OCR: ${lines.size} textos; faltam ${parsed.missingFields.joinToString().ifEmpty { "dados da oferta" }}"
                                        else "OCR: valores ambíguos ou botão Aceitar não reconhecido")
                                } else report("OCR: resultado descartado; janela mudou")
                            }
                            .addOnFailureListener { report("OCR: falha no reconhecimento local") }
                            .addOnCompleteListener { bitmap.recycle(); finish() }
                        submitted = true; crop = null
                    } catch (_: Exception) { report("OCR: falha ao preparar imagem") }
                    finally {
                        crop?.recycle(); full?.recycle(); hardware?.recycle(); buffer.close()
                        if (!submitted) finish()
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
            if (Build.VERSION.SDK_INT >= 34) service.takeScreenshotOfWindow(selected.id, service.mainExecutor, callback)
            else service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, callback)
        } catch (_: Exception) { report("OCR: captura indisponível"); finish() }
    }
    private fun android.graphics.Rect.region() = OcrRegion(left, top, right, bottom)

    private fun finish() { busy = false; if (closed) recognizer.close() }
    fun close() {
        closed = true
        retry?.let { handler.removeCallbacks(it) }; retry = null
        if (!busy) recognizer.close()
    }
}
