package io.github.seungminshin97.japeace

import io.github.seungminshin97.japeace.domain.vehicle.VehicleGateway
import io.github.seungminshin97.japeace.domain.rest.RestRepository
import io.github.seungminshin97.japeace.domain.rest.SettingsReader

fun interface DemoControls {
    fun setParked(parked: Boolean)
}

data class AppDependencies(
    val vehicleGateway: VehicleGateway,
    val restRepository: RestRepository,
    val settingsReader: SettingsReader,
    val demoControls: DemoControls? = null,
    val vehiclePermissions: List<String> = emptyList(),
)
