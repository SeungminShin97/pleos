package io.github.seungminshin97.japeace.integration.vehicle

import io.github.seungminshin97.japeace.domain.vehicle.VehicleGateway
import io.github.seungminshin97.japeace.domain.vehicle.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fail-closed placeholder for the real AAOS/Pleos adapter.
 * Vehicle SDK is a vehicle-flavor dependency, but service connection, permissions,
 * standard speed/gear reads, and capability checks have not been implemented yet.
 */
class PleosVehicleGateway : VehicleGateway {
    override val state = MutableStateFlow(VehicleState()).asStateFlow()
}
