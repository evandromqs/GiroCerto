package com.girocerto.monitoring

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class OcrRecognitionTest {
    @Test fun localRecognizerReadsUberFareFromBitmapTwice() {
        // Exercise the actual bundled recognizer, not only manually supplied strings.
        val bitmap = Bitmap.createBitmap(800, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
        listOf("Envios Moto", "R$ 16,73", "29 min (10.7 km)", "Aceitar").forEachIndexed { i, text ->
            canvas.drawText(text, 60f, 180f + i * 150f, paint)
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            var first: OfferReaderResult? = null
            repeat(2) {
                val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS)
                val lines = result.textBlocks.flatMap { it.lines }
                    .filter { OfferOcrContext.acceptsLine(it.text, it.boundingBox?.top, bitmap.height) }
                    .map { it.text }
                assertTrue("Offer context missing: $lines", OfferOcrContext.permits(lines))
                val parsed = AccessibilityOfferReader.parse(lines, "com.ubercab.driver")
                assertTrue("Incomplete offer: $parsed", parsed.isComplete)
                assertEquals(16.73, parsed.fare!!, .001)
                assertEquals(10.7, parsed.distanceKm!!, .001)
                assertEquals(29, parsed.durationMinutes)
                first?.let { assertEquals(it, parsed) }
                first = parsed
            }
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }
}
