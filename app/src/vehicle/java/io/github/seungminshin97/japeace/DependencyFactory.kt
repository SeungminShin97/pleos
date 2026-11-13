package io.github.seungminshin97.japeace

import android.car.Car
import android.content.Context

import io.github.seungminshin97.japeace.integration.vehicle.PleosVehicleGateway
import io.github.seungminshin97.japeace.integration.vehicle.PleosSettingsReader
import io.github.seungminshin97.japeace.data.rest.RestDatabase
import io.github.seungminshin97.japeace.data.rest.RoomRestRepository

fun createDependencies(context: Context): AppDependencies = AppDependencies(
    vehicleGateway = PleosVehicleGateway(context.applicationContext),
    restRepository = RoomRestRepository(RestDatabase.get(context)),
    settingsReader = PleosSettingsReader(context.applicationContext),
    vehiclePermissions = listOf(Car.PERMISSION_SPEED),
)
