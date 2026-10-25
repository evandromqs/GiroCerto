package com.girocerto.monitoring

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Display
import android.widget.Toast
import com.girocerto.domain.model.RideOffer
import java.util.concurrent.Executors

/** Explicit opt-in capture of the visible display. No recording or hidden media capture. */
class OfferScreenshot(private val service: AccessibilityService) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val captured = mutableMapOf<RideOffer, Long>()
    private var lastAttempt = -30_000L
    private var lastError = -60_000L
    @Volatile private var closed = false
    private var pending = false

    private fun permitted(packageName: String): Boolean {
        val policy = CopilotPreferences(service).read()
        if (closed || !policy.captureOfferScreenshot || !policy.monitors(packageName)) return false
        val root = service.rootInActiveWindow ?: return false
        return try { root.packageName?.toString() == packageName }
        finally { @Suppress("DEPRECATION") root.recycle() }
    }

    fun capture(offer: RideOffer, packageName: String) {
        if (Build.VERSION.SDK_INT < 30 || pending || !permitted(packageName)) return
        val now = SystemClock.elapsedRealtime()
        captured.entries.removeAll { now - it.value >= 120_000L }
        if (now - lastAttempt < 30_000L || captured.containsKey(offer)) return
        lastAttempt = now
        captured[offer] = now
        pending = true
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        var hardware: Bitmap? = null
                        var copy: Bitmap? = null
                        try {
                            if (!permitted(packageName)) return
                            hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            copy = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                            val bitmap = copy ?: throw java.io.IOException("Bitmap unavailable")
                            worker.execute {
                                try { save(bitmap) }
                                catch (_: Exception) { main.post { reportFailure() } }
                                finally { bitmap.recycle() }
                            }
                            copy = null // worker owns it after successful submission
                        } catch (_: Exception) { reportFailure() }
                        finally {
                            copy?.recycle()
                            hardware?.recycle()
                            buffer.close()
                            pending = false
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        pending = false
                        reportFailure()
                    }
                })
        } catch (_: Exception) {
            pending = false
            reportFailure()
        }
    }

    private fun save(bitmap: Bitmap) {
        if (Build.VERSION.SDK_INT < 30) return
        // Check the opt-in again immediately before publishing media.
        val policy = CopilotPreferences(service).read()
        if (closed || !policy.enabled || !policy.captureOfferScreenshot) return
        val resolver = service.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "GiroCerto_" + System.currentTimeMillis() + ".png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GiroCerto")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw java.io.IOException("Media insert failed")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw java.io.IOException("Encode failed")
            } ?: throw java.io.IOException("Media stream unavailable")
            val currentPolicy = CopilotPreferences(service).read()
            if (closed || !currentPolicy.enabled || !currentPolicy.captureOfferScreenshot) {
                resolver.delete(uri, null, null)
                return
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            main.post { if (!closed) Toast.makeText(service, "Comprovante salvo em Imagens/GiroCerto", Toast.LENGTH_SHORT).show() }
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun reportFailure() {
        val now = SystemClock.elapsedRealtime()
        if (!closed && now - lastError >= 60_000L) {
            lastError = now
            Toast.makeText(service, "Não foi possível salvar o comprovante. Algumas telas bloqueiam capturas.", Toast.LENGTH_LONG).show()
        }
    }
    fun close() { closed = true; worker.shutdown() }
}
