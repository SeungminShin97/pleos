package io.github.seungminshin97.japeace.domain.vehicle

import kotlinx.coroutines.flow.StateFlow

/** Read-only state and subscription lifecycle. Vehicle writes require separate capability validation. */
interface VehicleGateway {
    val state: StateFlow<VehicleState>
    fun start() {}
    fun stop() {}
    fun close() { stop() }
}
