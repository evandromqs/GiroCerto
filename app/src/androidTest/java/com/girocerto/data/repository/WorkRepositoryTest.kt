package com.girocerto.data.repository

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.girocerto.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WorkRepositoryTest {
    @Test fun corruptDayIsPreservedAndCannotBeOverwritten() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("work_ledger_v1", 0)
        val date = LocalDate.of(1899, 12, 31)
        val key = "day:$date"
        val previous = prefs.getString(key, null)
        try {
            prefs.edit().putString(key, "broken-json").commit()
            val repo = WorkRepository(context)
            assertNotNull(repo.loadError.value)
            try { repo.saveDay(WorkDay(date, emptyList(),0.0,0.0,0.0,0.0,0.0)); fail("Protected record") }
            catch (_: IllegalStateException) { }
            assertEquals("broken-json", prefs.getString(key,null))
            assertTrue(repo.backup().contains("broken-json"))
        } finally { prefs.edit().apply { if(previous == null) remove(key) else putString(key,previous) }.commit() }
    }
    @Test fun fullBackupRoundTripPreservesAppDetailsAndContract() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = WorkRepository(context)
        val date = LocalDate.of(1899, 12, 30)
        val day = WorkDay(date,listOf(PlatformWork(WorkPlatform.KEETA,45.75,12.3,2.5)),15.0,3.0,40.0,.6,100.0,200.0)
        try {
            repo.saveDay(day)
            val backup = repo.backup()
            repo.deleteDay(date)
            repo.restoreBackup(backup)
            assertEquals(day, repo.days.value.first { it.date == date })
            try { repo.restoreBackup(backup.replace("girocerto-ledger", "other-format")); fail("Invalid format") }
            catch (_: IllegalArgumentException) { }
            assertEquals(day, repo.days.value.first { it.date == date })
        } finally { repo.deleteDay(date) }
    }
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
