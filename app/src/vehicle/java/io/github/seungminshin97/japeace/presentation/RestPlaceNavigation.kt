package io.github.seungminshin97.japeace.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun RestPlaceNavigation(place: RestPlace, uniqueName: Boolean) {
    val navigation = LocalRestNavigation.current

    val state by navigation.state.collectAsStateWithLifecycle()
    var confirmDestination by remember(place) { mutableStateOf(false) }


    OutlinedButton(enabled = state.ready && !state.pending, onClick = navigation::refreshRoute) {
        Text("현재 경로 확인")
    }
    state.destination?.name?.takeIf(String::isNotBlank)?.let { Text("현재 목적지 $it") }
    OutlinedButton(enabled = state.ready && !state.pending, onClick = { confirmDestination = true }) {
        Text("이 장소를 목적지로")
    }
    if (confirmDestination) {
        AlertDialog(
            onDismissRequest = { confirmDestination = false },
            title = { Text("목적지 설정") },
            text = { Text("${place.name}을(를) 목적지로 설정할까요? 안내 중인 경로가 있으면 변경됩니다.") },
            confirmButton = {
                TextButton(enabled = state.ready && !state.pending, onClick = {
                    confirmDestination = false
                    navigation.setDestination(place)
                }) { Text("목적지 설정 확인") }
            },
            dismissButton = { TextButton(onClick = { confirmDestination = false }) { Text("취소") } },
        )
    }
    OutlinedButton(enabled = state.canAddWaypoint(place, uniqueName), onClick = {
        navigation.addWaypoint(place, uniqueName)
    }) { Text("이 장소를 경유지로 추가") }
    if (state.contains(place, uniqueName)) Text("이미 경로에 있는 장소입니다.")
    Text(state.status)
}
