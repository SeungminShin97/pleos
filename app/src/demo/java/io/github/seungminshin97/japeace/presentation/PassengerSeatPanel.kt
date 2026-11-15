package io.github.seungminshin97.japeace.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.seungminshin97.japeace.domain.vehicle.VehicleState

@Composable
internal fun PassengerSeatPanel(state: VehicleState) {
    var front by rememberSaveable { mutableIntStateOf(50) }
    var rearLeft by rememberSaveable { mutableIntStateOf(50) }
    var rearRight by rememberSaveable { mutableIntStateOf(50) }
    var selected by rememberSaveable { mutableStateOf(PassengerSeat.FRONT) }
    val position = when (selected) {
        PassengerSeat.FRONT -> front
        PassengerSeat.REAR_LEFT -> rearLeft
        PassengerSeat.REAR_RIGHT -> rearRight
    }
    PassengerSeatControls(
        selected, { selected = it }, state.connected && state.readingsValid,
        "모의 위치 $position",
    ) { forward ->
        val updated = (position + if (forward) 5 else -5).coerceIn(0, 100)
        when (selected) {
            PassengerSeat.FRONT -> front = updated
            PassengerSeat.REAR_LEFT -> rearLeft = updated
            PassengerSeat.REAR_RIGHT -> rearRight = updated
        }
    }
}
