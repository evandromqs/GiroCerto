package com.girocerto.data.repository

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.girocerto.domain.calculator.RideCalculator
import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.VehicleSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import java.io.File

@RunWith(AndroidJUnit4::class)
class RideRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private fun context(): Context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        override fun getFilesDir(): File = folder.root
    }
    private fun offer() = RideCalculator.calculate(RideOffer(30.0, 10.0, 30), VehicleSettings())

    @Test fun corruptHistoryIsPreservedAndCannotBeOverwritten() = runBlocking {
        val context = context()
        val file = File(context.filesDir, "girocerto_rides_history.json")
        val original = "[{broken-history"
        file.writeText(original)
        val repository = RideRepository(context)
        org.junit.Assert.assertNotNull(repository.loadError.value)
        try { repository.saveRide(offer()); org.junit.Assert.fail("Must reject overwrite") }
        catch (_: java.io.IOException) { }
        assertEquals(original, file.readText())
        repository.retryLoad().join()
        org.junit.Assert.assertNotNull(repository.loadError.value)
        file.writeText("[]")
        repository.retryLoad().join()
        org.junit.Assert.assertNull(repository.loadError.value)
        repository.saveRide(offer())
        assertEquals(1, RideRepository(context).rides.value.size)
    }
    @Test fun concurrentSavesSurviveReloadWithUniqueIds() = runBlocking {
        val context = context()
        val repository = RideRepository(context)
        (1..40).map { async(Dispatchers.Default) { repository.saveRide(offer()) } }.awaitAll()
        val reloaded = RideRepository(context)
        assertEquals(40, reloaded.rides.value.size)
        assertEquals(40, reloaded.rides.value.map { it.id }.toSet().size)
        repository.deleteRide(repository.rides.value.first().id)
        assertEquals(39, RideRepository(context).rides.value.size)
        repository.clearHistory()
        assertEquals(0, RideRepository(context).rides.value.size)
    }

    @Test fun dailyCostIsDeductedOnlyOnce() = runBlocking {
        val repository = RideRepository(context())
        val analysis = offer()
        repository.saveRide(analysis)
        repository.saveRide(analysis)
        assertEquals(analysis.estimatedProfit * 2 - 100.0,
            repository.getTodaySummary(100.0).totalEstimatedProfit, 0.00001)
        repository.clearHistory()
        assertEquals(0.0, repository.getTodaySummary(100.0).totalEstimatedProfit, 0.0)
    }
}
