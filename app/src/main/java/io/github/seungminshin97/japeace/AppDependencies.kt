package io.github.seungminshin97.japeace

import io.github.seungminshin97.japeace.domain.vehicle.VehicleGateway

fun interface DemoControls {
    fun setParked(parked: Boolean)
}

data class AppDependencies(
    val vehicleGateway: VehicleGateway,
    val demoControls: DemoControls? = null,
)
