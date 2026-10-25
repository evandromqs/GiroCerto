package com.girocerto.data.repository

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.girocerto.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WorkRepositoryTest {
    @Test fun selectedPlatformsSurviveReloadAndRejectEmptySelection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = WorkRepository(context)
        val original = repo.selectedPlatforms.value
        try {
            val selected = setOf(WorkPlatform.UBER_EATS, WorkPlatform.KEETA)
            repo.saveSelectedPlatforms(selected)
            assertEquals(selected, WorkRepository(context).selectedPlatforms.value)
            try {
                repo.saveSelectedPlatforms(emptySet())
                fail("Empty selection must be rejected")
            } catch (_: IllegalArgumentException) { }
            assertEquals(selected, WorkRepository(context).selectedPlatforms.value)
        } finally { repo.saveSelectedPlatforms(original) }
    }

    @Test fun dailyEditsReplaceAndSurviveReloadWithOriginalCosts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Instrumented test uses the target app's private preferences; reserve a historical fixture day.
        val date = LocalDate.of(1900, 1, 1)
        val repo = WorkRepository(context)
        check(repo.days.value.none { it.date == date })
        try {
            val day = WorkDay(date, listOf(PlatformWork(WorkPlatform.UBER_EATS, 50.0, 10.0, 2.0)), 12.0, 2.0, 20.0, 0.5, 100.0)
            repo.saveDay(day)
            repo.saveDay(day.copy(totalKm = 20.0))
            val reloaded = WorkRepository(context).days.value.filter { it.date == date }
            assertEquals(1, reloaded.size)
            assertEquals(20.0, reloaded.single().totalKm, 0.0)
            assertEquals(20.0, reloaded.single().fixedCost, 0.0)
            repo.deleteDay(date)
            assertTrue(WorkRepository(context).days.value.none { it.date == date })
        } finally { repo.deleteDay(date) }
    }
}
