package io.github.seungminshin97.japeace

import android.content.Context

import io.github.seungminshin97.japeace.data.vehicle.FakeVehicleGateway
import io.github.seungminshin97.japeace.data.vehicle.FakeSettingsReader
import io.github.seungminshin97.japeace.data.rest.RestDatabase
import io.github.seungminshin97.japeace.data.rest.RoomRestRepository

fun createDependencies(context: Context): AppDependencies {
    val gateway = FakeVehicleGateway()
    return AppDependencies(vehicleGateway = gateway, demoControls = gateway,
        restRepository = RoomRestRepository(RestDatabase.get(context)), settingsReader = FakeSettingsReader(gateway))
}
