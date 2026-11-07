package io.github.seungminshin97.japeace.domain.policy

import io.github.seungminshin97.japeace.domain.vehicle.Gear
import io.github.seungminshin97.japeace.domain.vehicle.VehicleState

enum class ParkingBlockReason { NOT_CONNECTED, DATA_UNAVAILABLE, NOT_IN_PARK, VEHICLE_MOVING }

sealed interface ParkingDecision {
    data object Allowed : ParkingDecision
    data class Blocked(val reason: ParkingBlockReason) : ParkingDecision
}

/** This is one precondition, not proof of physical safety or permission to move a seat. */
class ParkingPolicy {
    fun evaluate(state: VehicleState): ParkingDecision {
        if (!state.connected) return ParkingDecision.Blocked(ParkingBlockReason.NOT_CONNECTED)
        val speed = state.speedMetersPerSecond
        if (!state.readingsValid || state.gear == null || speed == null || !speed.isFinite()) {
            return ParkingDecision.Blocked(ParkingBlockReason.DATA_UNAVAILABLE)
        }
        if (state.gear != Gear.PARK) return ParkingDecision.Blocked(ParkingBlockReason.NOT_IN_PARK)
        if (speed != 0f) return ParkingDecision.Blocked(ParkingBlockReason.VEHICLE_MOVING)
        return ParkingDecision.Allowed
    }
}
