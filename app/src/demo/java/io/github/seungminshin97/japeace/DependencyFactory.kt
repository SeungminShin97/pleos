package io.github.seungminshin97.japeace

import android.content.Context

import io.github.seungminshin97.japeace.data.vehicle.FakeVehicleGateway
import io.github.seungminshin97.japeace.data.vehicle.FakeSettingsReader
import io.github.seungminshin97.japeace.data.rest.RestDatabase
import io.github.seungminshin97.japeace.data.rest.RoomRestRepository
import io.github.seungminshin97.japeace.domain.rest.DemoRestCoordinator

private var dependencies: AppDependencies? = null

@Synchronized
fun createDependencies(context: Context): AppDependencies = dependencies ?: run {
    val gateway = FakeVehicleGateway()
    val store = RoomRestRepository(RestDatabase.get(context))
    val settings = FakeSettingsReader(gateway)
    AppDependencies(vehicleGateway = gateway, demoControls = settings, restRepository = store,
        settingsReader = settings, demoRestCoordinator = DemoRestCoordinator(store, gateway, settings))
        .also { dependencies = it }
}
