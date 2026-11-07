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
import io.github.seungminshin97.japeace.presentation.theme.JapeaceTheme

@Composable
fun HomeRoute(viewModel: HomeViewModel) {
    val vehicleState by viewModel.vehicleState.collectAsStateWithLifecycle()
    HomeScreen(
        state = vehicleState,
        decision = viewModel.parkingPolicy.evaluate(vehicleState),
        isDemo = viewModel.isDemo,
        onSimulateParked = viewModel::simulateParked,
    )
}

@Composable
fun HomeScreen(
    state: VehicleState,
    decision: ParkingDecision,
    isDemo: Boolean,
    onSimulateParked: (Boolean) -> Unit,
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
                            if (isDemo) "개발용 데모 · 모의 차량 상태" else "차량 연동 준비 · 연결되지 않음",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (isDemo) "실제 차량과 연결되지 않으며 차량을 조작하지 않습니다."
                            else "Pleos SDK 의존성이 등록된 초기 프로젝트입니다. 차량 상태 조회와 조작은 아직 구현하지 않았습니다.",
                        )
                    }
                }
                Card {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("주차 상태", style = MaterialTheme.typography.titleLarge)
                        Text("기어: ${state.gear?.displayName() ?: "미확인"} · 속도: ${state.speedMetersPerSecond?.let { "${(it * 3.6f).toInt()} km/h" } ?: "미확인"}")
                        Text(decision.displayMessage(isDemo), color = MaterialTheme.colorScheme.primary)
                        if (isDemo) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { onSimulateParked(true) }) { Text("P · 정지") }
                                OutlinedButton(onClick = { onSimulateParked(false) }) { Text("D · 주행") }
                            }
                        }
                    }
                }
                Text("휴식 준비", style = MaterialTheme.typography.headlineSmall)
                Text("처음에 편한 자세를 저장하고, 다음 휴식에 다시 불러오는 흐름을 준비하고 있어요.")
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("휴식 프리셋 저장 · 개발 예정")
                }
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("휴식 시작 · 개발 예정")
                }
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("이전 설정으로 복귀 · 개발 예정")
                }
                Text(
                    "다음 단계: 좌석 위치 조회·이동·중단 동작 검증. 음성·알람은 후속 구현이며 장소 검색과 내비 연동은 보류합니다.",
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
    ParkingDecision.Allowed -> if (isDemo) "모의 데이터에서 P·정지 조건 충족" else "P·정지 조건 충족"
    is ParkingDecision.Blocked -> when (reason) {
        ParkingBlockReason.NOT_CONNECTED -> "차량 연결 전에는 조작할 수 없습니다."
        ParkingBlockReason.DATA_UNAVAILABLE -> "정상적인 기어·속도 값을 확인할 수 없습니다."
        ParkingBlockReason.NOT_IN_PARK -> "P 상태에서만 휴식 설정을 적용할 수 있습니다."
        ParkingBlockReason.VEHICLE_MOVING -> "차량 정지를 확인해야 합니다."
    }
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
