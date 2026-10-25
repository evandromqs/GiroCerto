package com.girocerto.domain

import com.girocerto.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class WorkPlatformSelectionTest {
    @Test fun newDayContainsOnlySelectedApps() {
        val zeros = WorkPlatform.entries.map { PlatformWork(it, 0.0, 0.0, 0.0) }
        assertEquals(setOf(WorkPlatform.IFOOD, WorkPlatform.KEETA), platformsForDay(setOf(WorkPlatform.IFOOD, WorkPlatform.KEETA), zeros))
    }
    @Test fun deselectionPreservesRevenueAndIndependentDistanceAndHours() {
        val entries = listOf(PlatformWork(WorkPlatform.UBER, 50.0, 0.0, 0.0),
            PlatformWork(WorkPlatform.UBER_EATS, 0.0, 12.0, 0.0),
            PlatformWork(WorkPlatform.FOOD_99, 0.0, 0.0, 2.0))
        assertEquals(setOf(WorkPlatform.IFOOD, WorkPlatform.UBER, WorkPlatform.UBER_EATS, WorkPlatform.FOOD_99),
            platformsForDay(setOf(WorkPlatform.IFOOD), entries))
    }
    @Test fun invalidDraftIsNotSilentlyHiddenWhenDeselecting() {
        assertTrue(WorkPlatform.UBER in platformsForDay(setOf(WorkPlatform.KEETA), listOf(PlatformWork(WorkPlatform.UBER, -1.0, 0.0, 0.0))))
    }
    @Test fun clearingDeselectedAmountsRemovesPlatformWithoutChangingSelectedOnes() {
        assertEquals(setOf(WorkPlatform.KEETA), platformsForDay(setOf(WorkPlatform.KEETA), listOf(PlatformWork(WorkPlatform.UBER, 0.0, 0.0, 0.0))))
    }
}
