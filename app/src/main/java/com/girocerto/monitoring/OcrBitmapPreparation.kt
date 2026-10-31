package com.girocerto.monitoring

import android.graphics.Bitmap

internal object OcrBitmapPreparation {
    fun mutableCopy(source: Bitmap): Bitmap =
        checkNotNull(source.copy(Bitmap.Config.ARGB_8888, true)) {
            "Não foi possível converter a captura para OCR"
        }
}
