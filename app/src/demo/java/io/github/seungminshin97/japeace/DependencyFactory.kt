package io.github.seungminshin97.japeace

import io.github.seungminshin97.japeace.data.vehicle.FakeVehicleGateway

fun createDependencies(): AppDependencies {
    val gateway = FakeVehicleGateway()
    return AppDependencies(vehicleGateway = gateway, demoControls = gateway)
}
