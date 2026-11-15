package io.github.seungminshin97.japeace.domain.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryReadingTest {
    @Test
    fun usesNominalCapacityWhenCurrentCapacityCannotBeRead() {
        assertEquals(BatteryReading(19, true), batteryReading(15_000f, null) { 77_400f })
        assertEquals(BatteryReading(19, true), batteryReading(15_000f, Float.NaN) { 77_400f })
    }

    @Test
    fun prefersCurrentCapacityWithoutReadingNominalCapacity() {
        val reading = batteryReading(15_000f, 60_000f) { error("Unexpected nominal capacity lookup") }
        assertEquals(BatteryReading(25, false), reading)
    }

    @Test
    fun reportsUnavailableForUnusableEnergyOrCapacity() {
        assertNull(batteryReading(null, 60_000f) { 77_400f })
        assertNull(batteryReading(15_000f, 0f) { Float.POSITIVE_INFINITY })
        assertNull(batteryReading(70_000f, 60_000f) { 77_400f })
    }
}
