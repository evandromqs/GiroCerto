package com.girocerto.overlay

import org.junit.Assert.*
import org.junit.Test

class BubbleDropZoneTest {
    @Test fun releaseInsideTrashRadiusRemovesBubble() {
        assertTrue(BubbleDropZone.contains(200f, 900f, 200f, 900f, 50f))
        assertTrue(BubbleDropZone.contains(230f, 940f, 200f, 900f, 50f))
    }
    @Test fun passingNearFooterOutsideTrashDoesNotRemoveBubble() {
        assertFalse(BubbleDropZone.contains(251f, 900f, 200f, 900f, 50f))
        assertFalse(BubbleDropZone.contains(240f, 940f, 200f, 900f, 50f))
    }
    @Test fun missingTargetAndInvalidCoordinatesCannotRemoveBubble() {
        assertFalse(BubbleDropZone.contains(0f, 0f, 0f, 0f, 0f))
        assertFalse(BubbleDropZone.contains(Float.NaN, 0f, 0f, 0f, 50f))
    }
}
