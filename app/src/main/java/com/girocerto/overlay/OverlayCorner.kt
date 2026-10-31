package com.girocerto.overlay

/**
 * Standard resting corners for the compact floating modal overlay (Requirement R4).
 * Supports quadrant-based corner gravity: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT.
 */
enum class OverlayCorner(val label: String) {
    TOP_LEFT("Superior Esquerdo"),
    TOP_RIGHT("Superior Direito"),
    BOTTOM_LEFT("Inferior Esquerdo"),
    BOTTOM_RIGHT("Inferior Direito")
}
