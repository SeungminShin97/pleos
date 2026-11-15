package io.github.seungminshin97.japeace.presentation

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.seungminshin97.japeace.domain.policy.ParkingDecision
import io.github.seungminshin97.japeace.integration.vehicle.readRestClimatePower
import io.github.seungminshin97.japeace.integration.vehicle.supportsPleosRestControl
import kotlinx.coroutines.launch

@Composable
internal fun RestClimatePanel(viewModel: HomeViewModel) {
    val context = LocalContext.current.applicationContext
    val state by viewModel.vehicleState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    fun request(target: Boolean? = null) {
        reading = true
        status = null
        scope.launch {
            try {
                status = readRestClimatePower(context, target) {
                    viewModel.parkingPolicy.evaluate(viewModel.vehicleState.value) == ParkingDecision.Allowed
                }
            } finally {
                reading = false
            }
        }
    }
    OutlinedButton(enabled = !reading, onClick = { request() }) {
        Text(if (reading) "공조 확인 중" else "공조 상태 확인")
    }
    if (supportsPleosRestControl(context)) {
        val enabled = !reading && viewModel.parkingPolicy.evaluate(state) == ParkingDecision.Allowed
        OutlinedButton(enabled = enabled, onClick = { request(true) }) { Text("공조 켜기") }
        OutlinedButton(enabled = enabled, onClick = { request(false) }) { Text("공조 끄기") }
    }
    status?.let { Text(it) }
}
