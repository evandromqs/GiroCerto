package com.girocerto.monitoring

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrBitmapPreparationTest {
    @Test fun hardwareCaptureBecomesMutableWithoutLosingPixels() {
        val original = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.WHITE)
        original.setPixel(16, 16, Color.RED)
        val hardware = checkNotNull(original.copy(Bitmap.Config.HARDWARE, false))
        try {
            val prepared = OcrBitmapPreparation.mutableCopy(hardware)
            try {
                assertEquals(Bitmap.Config.ARGB_8888, prepared.config)
                assertTrue(prepared.isMutable)
                assertEquals(Color.RED, prepared.getPixel(16, 16))
                assertEquals(Color.WHITE, prepared.getPixel(0, 0))
                Canvas(prepared).drawColor(Color.BLACK)
                assertEquals(Color.BLACK, prepared.getPixel(16, 16))
            } finally { prepared.recycle() }
        } finally { hardware.recycle(); original.recycle() }
    }
}
