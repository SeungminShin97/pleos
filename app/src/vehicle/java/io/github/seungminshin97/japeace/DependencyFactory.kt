package io.github.seungminshin97.japeace

import io.github.seungminshin97.japeace.integration.vehicle.PleosVehicleGateway

fun createDependencies(): AppDependencies = AppDependencies(vehicleGateway = PleosVehicleGateway())
