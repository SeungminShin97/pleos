package io.github.seungminshin97.japeace.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

internal enum class PassengerSeat(val label: String) {
    FRONT("조수석"), REAR_LEFT("뒷좌석 왼쪽"), REAR_RIGHT("뒷좌석 오른쪽"),
}

@Composable
internal fun PassengerSeatControls(
    selected: PassengerSeat,
    onSelect: (PassengerSeat) -> Unit,
    enabled: Boolean,
    status: String,
    onMove: (Boolean) -> Unit,
) {
    Text("동승석 전후 조정")
    Row {
        PassengerSeat.entries.forEach { seat ->
            OutlinedButton(onClick = { onSelect(seat) }, enabled = seat != selected) {
                Text(seat.label)
            }
        }
    }
    Row {
        OutlinedButton(onClick = { onMove(true) }, enabled = enabled) { Text("앞으로") }
        OutlinedButton(onClick = { onMove(false) }, enabled = enabled) { Text("뒤로") }
    }
    if (status.isNotEmpty()) Text(status)
}
