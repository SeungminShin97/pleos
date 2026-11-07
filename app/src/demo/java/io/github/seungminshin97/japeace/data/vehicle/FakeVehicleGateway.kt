package io.github.seungminshin97.japeace.data.vehicle

import io.github.seungminshin97.japeace.DemoControls
import io.github.seungminshin97.japeace.domain.vehicle.Gear
import io.github.seungminshin97.japeace.domain.vehicle.VehicleGateway
import io.github.seungminshin97.japeace.domain.vehicle.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Included in demo builds only. Does not connect to or control a vehicle. */
class FakeVehicleGateway : VehicleGateway, DemoControls {
    private val mutableState = MutableStateFlow(VehicleState())
    override val state = mutableState.asStateFlow()

    init { setParked(true) }

    override fun setParked(parked: Boolean) {
        mutableState.value = VehicleState(
            connected = true,
            readingsValid = true,
            gear = if (parked) Gear.PARK else Gear.DRIVE,
            speedMetersPerSecond = if (parked) 0f else 10f,
        )
    }
}
