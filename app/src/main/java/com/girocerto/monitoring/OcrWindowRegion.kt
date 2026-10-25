package com.girocerto.monitoring

/** Screen coordinates, independent of Android so masking can be regression-tested. */
internal data class OcrRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    fun intersection(other: OcrRegion): OcrRegion? = OcrRegion(
        maxOf(left, other.left), maxOf(top, other.top), minOf(right, other.right), minOf(bottom, other.bottom)
    ).takeIf { it.width > 0 && it.height > 0 }

    fun without(mask: OcrRegion): List<OcrRegion> {
        val overlap = intersection(mask) ?: return listOf(this)
        return listOf(OcrRegion(left, top, right, overlap.top),
            OcrRegion(left, overlap.bottom, right, bottom),
            OcrRegion(left, overlap.top, overlap.left, overlap.bottom),
            OcrRegion(overlap.right, overlap.top, right, overlap.bottom))
            .filter { it.width > 0 && it.height > 0 }
    }
}

internal object OcrWindowRegion {
    fun visibleParts(window: OcrRegion, covers: List<OcrRegion>): List<OcrRegion> =
        covers.fold(listOf(window)) { parts, cover -> parts.flatMap { it.without(cover) } }

    // Window screenshots have their own origin; display screenshots use screen coordinates.
    fun masks(window: OcrRegion, covers: List<OcrRegion>, windowCapture: Boolean,
              bitmapWidth: Int, bitmapHeight: Int): List<OcrRegion> = covers.mapNotNull { cover ->
        val clipped = window.intersection(cover) ?: return@mapNotNull null
        if (!windowCapture) clipped else {
            val sx = bitmapWidth.toDouble() / window.width
            val sy = bitmapHeight.toDouble() / window.height
            OcrRegion(kotlin.math.floor((clipped.left - window.left) * sx).toInt(),
                kotlin.math.floor((clipped.top - window.top) * sy).toInt(),
                kotlin.math.ceil((clipped.right - window.left) * sx).toInt(),
                kotlin.math.ceil((clipped.bottom - window.top) * sy).toInt())
        }
    }
}
