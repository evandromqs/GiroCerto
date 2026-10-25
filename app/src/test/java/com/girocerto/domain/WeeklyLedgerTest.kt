package com.girocerto.domain

import com.girocerto.domain.calculator.WeeklyLedger
import com.girocerto.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WeeklyLedgerTest {
    private val monday = LocalDate.of(2026, 10, 19)
    @Test fun weeklyContractIsChargedOnceRegardlessOfWorkingDays() {
        for (count in listOf(3, 5, 7)) {
            val days = (0 until count).map { i -> WorkDay(monday.plusDays(i.toLong()),
                listOf(PlatformWork(WorkPlatform.UBER,100.0,0.0,0.0)),10.0,2.0,40.0,0.5,100.0,200.0) }
            val result = WeeklyLedger.calculate(monday,days,999.0)
            assertEquals(200.0,result.contract,0.0)
            assertEquals(count * 95.0 - 200,result.net,0.0)
            assertFalse(result.usedCurrentContract)
        }
    }
    @Test fun neighbouringWeeksAreExcludedAndOldRecordsAreExplicitlyEstimated() {
        val day = WorkDay(monday.minusDays(1),emptyList(),0.0,0.0,40.0,0.5,100.0,250.0)
        val result = WeeklyLedger.calculate(monday,listOf(day),200.0)
        assertEquals(0,result.registeredDays)
        assertEquals(-200.0,result.net,0.0)
        assertTrue(result.usedCurrentContract)
    }
}
