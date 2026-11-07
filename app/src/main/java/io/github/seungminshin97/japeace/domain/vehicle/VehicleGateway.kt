package io.github.seungminshin97.japeace.domain.vehicle

import kotlinx.coroutines.flow.StateFlow

/** Read-only scaffold. Write operations are added after SDK capability and stop behavior validation. */
interface VehicleGateway {
    val state: StateFlow<VehicleState>
}
