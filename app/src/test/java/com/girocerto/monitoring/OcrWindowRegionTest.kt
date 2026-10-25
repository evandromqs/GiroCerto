package com.girocerto.monitoring

import org.junit.Assert.*
import org.junit.Test

class OcrWindowRegionTest {
    private val screen = OcrRegion(0, 0, 610, 1378)

    @Test fun batteryBannerAndBubbleDoNotBlockVisibleUberCard() {
        val covers = listOf(OcrRegion(0, 0, 610, 215), OcrRegion(15, 150, 110, 245))
        val fare = OcrRegion(50, 770, 330, 835)
        val parts = OcrWindowRegion.visibleParts(screen, covers)
        assertTrue(parts.any { it.intersection(fare) == fare })
        assertTrue(OcrWindowRegion.masks(screen, covers, false, 610, 1378).none { it.intersection(fare) != null })
        assertTrue(parts.none { it.intersection(covers.first()) != null })
    }
    @Test fun completelyCoveredWindowIsNotReadable() {
        assertTrue(OcrWindowRegion.visibleParts(screen, listOf(screen)).isEmpty())
    }
    @Test fun severalWindowsCanCoverTheEntireTarget() {
        assertTrue(OcrWindowRegion.visibleParts(screen, listOf(OcrRegion(0, 0, 610, 700), OcrRegion(0, 700, 610, 1378))).isEmpty())
    }
    @Test fun unrelatedWindowDoesNotChangeVisibleArea() {
        assertEquals(listOf(screen), OcrWindowRegion.visibleParts(screen, listOf(OcrRegion(700, 0, 800, 100))))
    }
    @Test fun windowScreenshotMasksUseLocalOriginAndScale() {
        val popup = OcrRegion(20, 680, 590, 1330)
        assertEquals(listOf(OcrRegion(0, 0, 140, 140)), OcrWindowRegion.masks(popup,
            listOf(OcrRegion(0, 650, 90, 750)), true, 1140, 1300))
    }
    @Test fun displayScreenshotMasksAreClippedWithoutChangingOrigin() {
        val popup = OcrRegion(20, 680, 590, 1330)
        assertEquals(listOf(OcrRegion(20, 680, 90, 750)), OcrWindowRegion.masks(popup,
            listOf(OcrRegion(0, 650, 90, 750)), false, 610, 1378))
    }
    @Test fun masksNeverIncludeOtherAppsOutsideTarget() {
        assertTrue(OcrWindowRegion.masks(OcrRegion(20, 680, 590, 1330),
            listOf(OcrRegion(0, 0, 610, 215)), true, 570, 650).isEmpty())
    }
}
