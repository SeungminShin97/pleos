package io.github.seungminshin97.japeace.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.seungminshin97.japeace.domain.policy.ParkingBlockReason
import io.github.seungminshin97.japeace.domain.policy.ParkingDecision
import io.github.seungminshin97.japeace.domain.vehicle.Gear
import io.github.seungminshin97.japeace.domain.vehicle.VehicleState
import io.github.seungminshin97.japeace.domain.vehicle.VehicleIssue
import io.github.seungminshin97.japeace.presentation.theme.JapeaceTheme

@Composable
fun HomeRoute(viewModel: HomeViewModel) {
    val vehicleState by viewModel.vehicleState.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    HomeScreen(
        state = vehicleState,
        decision = viewModel.parkingPolicy.evaluate(vehicleState),
        isDemo = viewModel.isDemo,
        onSimulateParked = viewModel::simulateParked,
        storage = storage,
        onSavePreset = viewModel::savePreset,
    )
}

@Composable
fun HomeScreen(
    state: VehicleState,
    decision: ParkingDecision,
    isDemo: Boolean,
    onSimulateParked: (Boolean) -> Unit,
    storage: PresetStorageState = PresetStorageState(),
    onSavePreset: (String) -> Unit = {},
) {
    Scaffold { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text("자.peace", style = MaterialTheme.typography.displaySmall)
                Text("내 차에서, 나만의 휴식", style = MaterialTheme.typography.titleLarge)
                Card {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (isDemo) "개발용 데모, 모의 차량 상태" else "AAOS 차량 상태 읽기",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (isDemo) "실제 차량과 연결되지 않으며 차량을 조작하지 않습니다."
                            else state.connectionMessage(),
                        )
                    }
                }
                Card {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("주차 상태", style = MaterialTheme.typography.titleLarge)
                        Text("기어: ${state.gear?.displayName() ?: "미확인"}, 속도: ${state.speedMetersPerSecond?.takeIf { it.isFinite() }?.let { "${(it * 3.6f).toInt()} km/h" } ?: "미확인"}")
                        Text(decision.displayMessage(isDemo), color = MaterialTheme.colorScheme.primary)
                        if (isDemo) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { onSimulateParked(true) }) { Text("P, 정지") }
                                OutlinedButton(onClick = { onSimulateParked(false) }) { Text("D, 주행") }
                            }
                        }
                    }
                }
                Text("휴식 준비", style = MaterialTheme.typography.headlineSmall)
                PresetStoragePanel(storage, decision == ParkingDecision.Allowed, onSavePreset)
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("휴식 시작, 개발 예정")
                }
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("이전 설정으로 복귀, 개발 예정")
                }
                Text(
                    "좌석의 실제 이동과 원위치 재현은 미검증입니다. 음성과 알람은 후속 구현이며 장소 검색과 내비 연동은 보류합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Gear.displayName(): String = when (this) {
    Gear.PARK -> "P"
    Gear.REVERSE -> "R"
    Gear.NEUTRAL -> "N"
    Gear.DRIVE -> "D"
}

private fun ParkingDecision.displayMessage(isDemo: Boolean): String = when (this) {
    ParkingDecision.Allowed -> if (isDemo) "모의 데이터에서 P와 정지 조건 충족" else "조회한 차량 값에서 P와 정지 조건 충족, 차량 조작은 아직 지원하지 않습니다."
    is ParkingDecision.Blocked -> when (reason) {
        ParkingBlockReason.NOT_CONNECTED -> "차량 연결 전에는 조작할 수 없습니다."
        ParkingBlockReason.DATA_UNAVAILABLE -> "정상적인 기어와 속도 값을 확인할 수 없습니다."
        ParkingBlockReason.NOT_IN_PARK -> "P 상태에서만 휴식 설정을 적용할 수 있습니다."
        ParkingBlockReason.VEHICLE_MOVING -> "차량 정지를 확인해야 합니다."
    }
}

private fun VehicleState.connectionMessage(): String = when (issue) {
    VehicleIssue.CONNECTING -> "차량 서비스에 연결하고 있습니다."
    VehicleIssue.PERMISSION_REQUIRED -> "차량 속도 읽기 권한이 필요합니다. 앱 권한 설정에서 허용한 뒤 다시 열어 주세요."
    VehicleIssue.DISCONNECTED -> "차량 서비스 연결이 끊겼습니다. 이전 값으로 주차 상태를 판정하지 않습니다."
    VehicleIssue.DATA_UNAVAILABLE -> "정상적인 기어와 속도 값을 기다리고 있습니다. 차량 조작은 지원하지 않습니다."
    VehicleIssue.UNSUPPORTED -> "이 환경에서 필요한 차량 속성 구독을 사용할 수 없습니다."
    VehicleIssue.READ_FAILED -> "차량 상태 조회에 실패했습니다. 주차 조건은 미확인으로 처리합니다."
    null -> if (connected) "AAOS에서 기어와 속도를 조회합니다. 모의 데이터가 아니며 차량 조작은 아직 지원하지 않습니다."
        else "차량 서비스에 연결되지 않았습니다."
}

@Preview(widthDp = 1000, heightDp = 800)
@Composable
private fun HomePreview() {
    JapeaceTheme {
        HomeScreen(
            state = VehicleState(true, true, Gear.PARK, 0f),
            decision = ParkingDecision.Allowed,
            isDemo = true,
            onSimulateParked = {},
        )
    }
}
