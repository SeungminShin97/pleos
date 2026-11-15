package io.github.seungminshin97.japeace.presentation

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import io.github.seungminshin97.japeace.domain.vehicle.VehicleState
import io.github.seungminshin97.japeace.integration.vehicle.adjustPassengerSeat
import io.github.seungminshin97.japeace.integration.vehicle.supportsPleosRestControl
import kotlinx.coroutines.launch

@Composable
internal fun PassengerSeatPanel(state: VehicleState) {
    val context = LocalContext.current
    if (!supportsPleosRestControl(context)) return

    val scope = rememberCoroutineScope()
    val latestState by rememberUpdatedState(state)
    var selected by remember { mutableStateOf(PassengerSeat.FRONT) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    PassengerSeatControls(selected, onSelect = {
        if (!busy) {
            selected = it
            status = ""
        }
    }, enabled = state.connected && state.readingsValid && !busy, status = status) { forward ->
        if (!busy) {
            busy = true
            val target = selected
            val direction = if (forward) "FORWARD" else "BACKWARD"
            Log.d("PassengerSeat", "Request seat=$target direction=$direction gear=${state.gear} speedMps=${state.speedMetersPerSecond}")
            status = "${target.label} 위치 확인 중"
            scope.launch {
                try {
                    status = adjustPassengerSeat(context, target, forward) {
                        latestState.connected && latestState.readingsValid
                    }
                    Log.d("PassengerSeat", "Result seat=$target direction=$direction: $status")
                } finally {
                    busy = false
                }
            }
        }
    }
}
