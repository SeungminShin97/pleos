package io.github.seungminshin97.japeace.domain.policy

import io.github.seungminshin97.japeace.domain.vehicle.Gear
import io.github.seungminshin97.japeace.domain.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Test

class ParkingPolicyTest {
    private val policy = ParkingPolicy()
    private val parked = VehicleState(true, true, Gear.PARK, 0f)

    @Test fun permitsOnlyConfirmedParkAndStop() {
        assertEquals(ParkingDecision.Allowed, policy.evaluate(parked))
    }

    @Test fun disconnectedVehicleIsBlockedEvenWithPreviouslyValidValues() {
        assertBlocked(ParkingBlockReason.NOT_CONNECTED, parked.copy(connected = false))
    }

    @Test fun invalidOrMissingReadingsAreBlocked() {
        listOf(
            parked.copy(readingsValid = false),
            parked.copy(gear = null),
            parked.copy(speedMetersPerSecond = null),
            parked.copy(speedMetersPerSecond = Float.NaN),
            parked.copy(speedMetersPerSecond = Float.POSITIVE_INFINITY),
            parked.copy(speedMetersPerSecond = Float.NEGATIVE_INFINITY),
        ).forEach { assertBlocked(ParkingBlockReason.DATA_UNAVAILABLE, it) }
    }

    @Test fun stationaryDriveReverseAndNeutralAreBlocked() {
        listOf(Gear.DRIVE, Gear.REVERSE, Gear.NEUTRAL).forEach {
            assertBlocked(ParkingBlockReason.NOT_IN_PARK, parked.copy(gear = it))
        }
    }

    @Test fun parkWithAnyNonzeroSpeedIsBlocked() {
        listOf(0.01f, -0.01f, 10f).forEach {
            assertBlocked(ParkingBlockReason.VEHICLE_MOVING, parked.copy(speedMetersPerSecond = it))
        }
    }

    private fun assertBlocked(reason: ParkingBlockReason, state: VehicleState) {
        assertEquals(ParkingDecision.Blocked(reason), policy.evaluate(state))
    }
}
