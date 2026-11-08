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
        compose.waitUntil(5_000) { boundsFor("Giro Certo. Toque para abrir; arraste até a lixeira para ocultar.") == null }
        compose.runOnIdle {
            OverlayController.restore(compose.activity)
            OverlayController.startFloatingBubble(compose.activity)
        }
        awaitBounds("Giro Certo. Toque para abrir; arraste até a lixeira para ocultar.")
        assertFalse(OverlayController.hidden(compose.activity))
        compose.runOnIdle { OverlayController.stopFloatingBubble(compose.activity) }
    }

    @Test fun offerCardMinimizesWithoutPausingCopilot() {
        val pkg = compose.activity.packageName
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("appops set $pkg SYSTEM_ALERT_WINDOW allow")).use { it.readBytes() }
        val analysis = com.girocerto.domain.calculator.RideCalculator.calculate(
            com.girocerto.domain.model.RideOffer(fare = 16.73, distanceKm = 10.7, durationMinutes = 29, appName = "Uber"),
            com.girocerto.domain.model.VehicleSettings())
        for (theme in listOf(com.girocerto.app.theme.ThemeMode.LIGHT, com.girocerto.app.theme.ThemeMode.DARK)) {
            compose.runOnIdle {
                com.girocerto.app.theme.AppearancePreferences.set(compose.activity, theme)
                com.girocerto.app.theme.AppearancePreferences.setBubbleSize(
                    compose.activity,
                    com.girocerto.app.theme.FloatingIconSize.MEDIUM,
                )
                val prefs = CopilotPreferences(compose.activity); prefs.write(prefs.read().copy(enabled = true))
                OverlayController.stopFloatingBubble(compose.activity)
                OverlayController.restore(compose.activity)
                assertTrue(OverlayController.showOverlay(compose.activity, analysis))
            }
            val minimize = awaitBounds("Recolher painel")
            assertTopMetrics()
            SystemClock.sleep(300)
            val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            if(dir != null) java.io.File(dir,"offer-${theme.name}.png").outputStream().use { automation.takeScreenshot()?.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            val time = SystemClock.uptimeMillis()
            for(action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,minimize.exactCenterX(),minimize.exactCenterY(),0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                assertTrue(automation.injectInputEvent(event,true)); event.recycle()
            }
            val docked = awaitBounds("Giro Certo. Toque para abrir; arraste até a lixeira para ocultar.")
            val density = compose.activity.resources.displayMetrics.density
            val expectedBubblePx = com.girocerto.app.theme.FloatingIconSize.MEDIUM.dp * density
            assertEquals(expectedBubblePx.toDouble(), docked.width().toDouble(), 2.0)
            tap(docked)
            awaitBounds("Recolher painel")
            assertTopMetrics()
            tap(awaitBounds("Recolher painel"))
            val returned = awaitBounds("Giro Certo. Toque para abrir; arraste até a lixeira para ocultar.")
            assertEquals(docked, returned)
            assertTrue(CopilotPreferences(compose.activity).read().enabled)
        }
        compose.runOnIdle {
            OverlayController.stopFloatingBubble(compose.activity)
            com.girocerto.app.theme.AppearancePreferences.set(compose.activity, com.girocerto.app.theme.ThemeMode.LIGHT)
        }
    }

    @Test fun threePlatformsStayInBoundedPanelAndExpire() {
        val pkg = compose.activity.packageName
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("appops set $pkg SYSTEM_ALERT_WINDOW allow")).use { it.readBytes() }
        compose.runOnIdle {
            val prefs = CopilotPreferences(compose.activity)
            prefs.write(prefs.read().copy(enabled = true))
            OverlayController.restore(compose.activity)
            for (name in listOf("Uber", "99", "iFood")) {
                val offer = com.girocerto.domain.model.RideOffer(20.0, 5.0, 15, appName = name)
                OverlayController.showOverlay(compose.activity, com.girocerto.domain.calculator.RideCalculator.calculate(offer, com.girocerto.domain.model.VehicleSettings()))
            }
        }
        awaitBounds("Abrir Uber")
        awaitBounds("Abrir 99")
        awaitBounds("Abrir iFood")
        val panel = awaitBounds("Análise da oferta")
        assertTrue(panel.height() <= compose.activity.resources.displayMetrics.heightPixels * .50 + 2)
        // Unknown package handling must not depend on which driver apps happen to
        // be installed on the physical test device.
        compose.runOnIdle {
            val unknown = com.girocerto.domain.model.RideOffer(
                20.0,
                5.0,
                15,
                appName = "App inexistente",
                sourcePackage = "com.girocerto.test.notinstalled"
            )
            OverlayController.showOverlay(
                compose.activity,
                com.girocerto.domain.calculator.RideCalculator.calculate(
                    unknown,
                    com.girocerto.domain.model.VehicleSettings()
                )
            )
        }
        tap(awaitBounds("Abrir App inexistente"))
        awaitBounds("Recolher painel")
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        if (dir != null) java.io.File(dir, "offer-three-platforms.png").outputStream().use {
            automation.takeScreenshot()?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        val deadline = SystemClock.uptimeMillis() + 32_000
        while (boundsFor("Análise da oferta") != null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(200)
        assertNull(boundsFor("Análise da oferta"))
        tap(awaitBounds("Giro Certo. Toque para abrir; arraste até a lixeira para ocultar."))
        assertNull(boundsFor("Abrir iFood"))
        compose.runOnIdle { OverlayController.stopFloatingBubble(compose.activity) }
    }

    @Test fun openShortcutUsesInstalledLauncherAndCollapsesPanel() {
        val pkg = compose.activity.packageName
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("appops set $pkg SYSTEM_ALERT_WINDOW allow")).use { it.readBytes() }
        compose.runOnIdle {
            val prefs = CopilotPreferences(compose.activity)
            prefs.write(prefs.read().copy(enabled = true))
            OverlayController.restore(compose.activity)
            val offer = com.girocerto.domain.model.RideOffer(20.0, 5.0, 15, appName = "Uber", sourcePackage = pkg)
            OverlayController.showOverlay(compose.activity, com.girocerto.domain.calculator.RideCalculator.calculate(offer, com.girocerto.domain.model.VehicleSettings()))
        }
        tap(awaitBounds("Abrir Uber"))
        awaitBounds("Giro Certo. Toque para abrir; arraste até a lixeira para ocultar.")
        assertNull(boundsFor("Análise da oferta"))
        compose.runOnIdle { OverlayController.stopFloatingBubble(compose.activity) }
    }

    private fun tap(bounds: Rect) {
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(automation.injectInputEvent(event, true)); event.recycle()
        }
    }

    private fun assertTopMetrics() {
        val panel = awaitBounds("Análise da oferta")
        val screen = checkNotNull(automation.takeScreenshot())
        try {
            val density = compose.activity.resources.displayMetrics.density
            assertEquals(screen.width / 2.0, panel.exactCenterX().toDouble(), 16.0 * density)
            assertTrue("Panel must stay at the top", panel.top < screen.height * .15)
            assertTrue("Panel must occupy at most 50% of the screen", panel.height() <= screen.height * .50 + 2)
        } finally { screen.recycle() }
        val km = awaitBounds("Ganho por quilômetro")
        val hour = awaitBounds("Rendimento por hora")
        val profit = awaitBounds("Lucro estimado")
        assertEquals(km.centerY(), hour.centerY())
        assertEquals(hour.centerY(), profit.centerY())
        assertTrue(km.right <= hour.left && hour.right <= profit.left)
        awaitBounds("Aplicativo detectado: Uber")
    }

    private fun awaitBounds(description: String): Rect {
        var found: Rect? = null
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (found == null && SystemClock.uptimeMillis() < deadline) {
            found = boundsFor(description)
            if (found == null) SystemClock.sleep(100)
        }
        return checkNotNull(found) { "Overlay element unavailable: $description" }
    }

    // Inspect the app's actual attached windows. Accessibility may omit non-focused
    // application overlays even though they are visible and accept touch events.
    private fun boundsFor(description: String): Rect? {
        var result: Rect? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            fun find(view: android.view.View): android.view.View? {
                if (view.contentDescription?.toString() == description && view.isShown && view.isAttachedToWindow) return view
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                return null
            }
            for (root in android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                val target = find(root) ?: continue
                val point = IntArray(2)
                target.getLocationOnScreen(point)
                result = Rect(point[0], point[1], point[0] + target.width, point[1] + target.height)
                break
            }
        }
        return result
    }
}
