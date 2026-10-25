package com.girocerto.ui

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.girocerto.app.MainActivity
import com.girocerto.overlay.OverlayController
import com.girocerto.monitoring.CopilotPreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OverlayFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val diagnostics = object : org.junit.rules.TestWatcher() {
        override fun failed(error: Throwable, description: org.junit.runner.Description) {
            val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
            java.io.File(dir).mkdirs()
            java.io.File(dir, "overlay-failure.png").outputStream().use { stream ->
                automation.takeScreenshot()?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            }
        }
    }
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    @Test fun bubbleCanBeDraggedToTrashAndRestored() {
        val pkg = compose.activity.packageName
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("appops set $pkg SYSTEM_ALERT_WINDOW allow")).use { it.readBytes() }
        val info = automation.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        compose.runOnIdle {
            val prefs = CopilotPreferences(compose.activity)
            prefs.write(prefs.read().copy(enabled = true))
            OverlayController.restore(compose.activity)
            OverlayController.startFloatingBubble(compose.activity)
        }
        val bubble = awaitBounds("Giro Certo. Toque para abrir; arraste até a lixeira para ocultar.")
        val down = SystemClock.uptimeMillis()
        fun touch(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            assertTrue("Touch injection failed", automation.injectInputEvent(event, true)); event.recycle()
        }
        touch(MotionEvent.ACTION_DOWN, bubble.exactCenterX(), bubble.exactCenterY())
        touch(MotionEvent.ACTION_MOVE, bubble.exactCenterX() + 50, bubble.exactCenterY() + 50)
        SystemClock.sleep(300)
        val trash = awaitBounds("Solte aqui para ocultar")
        touch(MotionEvent.ACTION_MOVE, trash.exactCenterX(), trash.exactCenterY())
        SystemClock.sleep(200)
        touch(MotionEvent.ACTION_UP, trash.exactCenterX(), trash.exactCenterY())
        compose.waitUntil(5_000) { OverlayController.hidden(compose.activity) }
        compose.runOnIdle {
            OverlayController.restore(compose.activity)
            OverlayController.startFloatingBubble(compose.activity)
        }
        awaitBounds("Giro Certo. Toque para abrir; arraste até a lixeira para ocultar.")
        assertFalse(OverlayController.hidden(compose.activity))
        compose.runOnIdle { OverlayController.stopFloatingBubble(compose.activity) }
    }

    private fun awaitBounds(description: String): Rect {
        var found: Rect? = null
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (found == null && SystemClock.uptimeMillis() < deadline) {
            for (window in automation.windows) {
                val root = window.root ?: continue
                found = find(root, description)
                if (found != null) break
            }
            if (found == null) SystemClock.sleep(100)
        }
        return checkNotNull(found) { "Overlay element unavailable: $description" }
    }
    private fun find(node: AccessibilityNodeInfo, description: String): Rect? {
        if (node.contentDescription?.toString() == description) return Rect().also { node.getBoundsInScreen(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            find(child, description)?.let { return it }
        }
        return null
    }
}
