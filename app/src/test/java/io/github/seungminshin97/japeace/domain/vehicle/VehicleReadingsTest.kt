package io.github.seungminshin97.japeace.domain.vehicle

import io.github.seungminshin97.japeace.domain.policy.ParkingDecision
import io.github.seungminshin97.japeace.domain.policy.ParkingPolicy
import org.junit.Assert.*
import org.junit.Test

class VehicleReadingsTest {
    @Test fun requiresBothObservedProperties() {
        val readings = VehicleReadings()
        readings.gear(Gear.PARK, 10)
        assertFalse(readings.snapshot().readingsValid)
        readings.speed(0f, 10)
        assertEquals(ParkingDecision.Allowed, ParkingPolicy().evaluate(readings.snapshot()))
    }

    @Test fun delayedParkedEventCannotOverwriteNewerDrivingState() {
        val readings = VehicleReadings()
        readings.gear(Gear.DRIVE, 20)
        readings.speed(10f, 20)
        readings.gear(Gear.PARK, 10)
        readings.speed(0f, 10)
        assertEquals(Gear.DRIVE, readings.snapshot().gear)
        assertEquals(10f, readings.snapshot().speedMetersPerSecond)
        assertTrue(ParkingPolicy().evaluate(readings.snapshot()) is ParkingDecision.Blocked)
    }

    @Test fun propertyErrorClearsPreviouslyAllowedStateUntilRequery() {
        val readings = VehicleReadings()
        readings.gear(Gear.PARK, 20)
        readings.speed(0f, 20)
        readings.invalidateGear()
        assertFalse(readings.snapshot().readingsValid)
        readings.gear(Gear.PARK, 10)
        assertNull(readings.snapshot().gear)
        readings.gear(Gear.PARK, 20)
        assertTrue(readings.snapshot().readingsValid)
        readings.invalidateSpeed()
        assertFalse(readings.snapshot().readingsValid)
    }

    @Test fun unknownGearAndNonFiniteSpeedNeverAllowParking() {
        val readings = VehicleReadings()
        readings.gear(Gear.PARK, 10)
        for (speed in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            readings.speed(speed, 10)
            assertFalse(readings.snapshot().readingsValid)
        }
        readings.speed(-2f, 20)
        assertTrue(ParkingPolicy().evaluate(readings.snapshot()) is ParkingDecision.Blocked)
        readings.gear(null, 20)
        assertFalse(readings.snapshot().readingsValid)
    }

    @Test fun newConnectionHasNoReadingsFromPreviousSession() {
        val previous = VehicleReadings().apply { gear(Gear.PARK, 100); speed(0f, 100) }
        assertTrue(previous.snapshot().readingsValid)
        val next = VehicleReadings()
        next.speed(0f, 1)
        assertFalse(next.snapshot().readingsValid)
        next.gear(Gear.DRIVE, 1)
        assertEquals(Gear.DRIVE, next.snapshot().gear)
    }
}
