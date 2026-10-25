package com.girocerto.overlay

internal object BubbleDropZone {
    fun contains(x: Float, y: Float, centerX: Float, centerY: Float, radius: Float): Boolean =
        radius > 0 && (x - centerX) * (x - centerX) + (y - centerY) * (y - centerY) <= radius * radius
}
