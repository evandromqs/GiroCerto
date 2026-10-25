package com.girocerto.data.repository

import com.girocerto.domain.calculator.RideCalculator
import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.TrafficLightStatus
import com.girocerto.domain.model.VehicleSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class StoredDecimalTest {
    @Test fun legacyFloatTargetDoesNotDowngradeAnExactMatch() {
        val target = StoredDecimal.decode(2.2f, 2.0)
        assertEquals(2.2, target, 0.0)
        val analysis = RideCalculator.calculate(
            RideOffer(22.0, 10.0, 30), VehicleSettings(minTargetPerKm = target)
        )
        assertEquals(TrafficLightStatus.GREEN, analysis.trafficLight)
    }

    @Test fun decimalStringsPreservePrecisionAndBadValuesUseDefault() {
        assertEquals(2.123456789, StoredDecimal.decode("2.123456789", 2.0), 0.0)
        listOf(null, "bad", "NaN", "Infinity").forEach {
            assertEquals(2.0, StoredDecimal.decode(it, 2.0), 0.0)
        }
    }
}
