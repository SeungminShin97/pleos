package io.github.seungminshin97.japeace.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.seungminshin97.japeace.domain.policy.ParkingBlockReason
import io.github.seungminshin97.japeace.domain.policy.ParkingDecision
import io.github.seungminshin97.japeace.domain.vehicle.Gear
import io.github.seungminshin97.japeace.domain.vehicle.ChargingStatus
import io.github.seungminshin97.japeace.domain.vehicle.VehicleState
import io.github.seungminshin97.japeace.presentation.theme.JapeaceTheme

enum class HomePage(val label: String) {
    SETTINGS("휴식 설정"),
    SEATS("동승석"),
    PLACES("쉴 곳"),
    ALARM("알람 및 음성"),
    MUSIC("음악"),
    ELECTRIC("전기차 휴식"),
}

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    requestedRestMinutes: Int? = null,
    restPlaceQuery: String? = null,
    requestedRestSettings: Boolean = false,
    onSettingsRequestHandled: () -> Unit = {},
    onPlacesRequestHandled: () -> Unit = {},
    onRestRequestHandled: () -> Unit = {},
) {
    val vehicleState by viewModel.vehicleState.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var page by rememberSaveable { mutableStateOf(HomePage.SETTINGS) }
    LaunchedEffect(requestedRestMinutes, restPlaceQuery, requestedRestSettings) {
        if (restPlaceQuery != null) page = HomePage.PLACES
        else if (requestedRestMinutes != null) page = HomePage.ALARM
        else if (requestedRestSettings) {
            page = HomePage.SETTINGS
            onSettingsRequestHandled()
        }
    }
    if (!viewModel.isDemo) {
        val parked = viewModel.parkingPolicy.evaluate(vehicleState) == ParkingDecision.Allowed
        RestBatteryNotificationEffect(vehicleState.batteryPercent, parked)
    }
    HomeScreen(
        state = vehicleState,
        decision = viewModel.parkingPolicy.evaluate(vehicleState),
        isDemo = viewModel.isDemo,
        onSimulateParked = viewModel::simulateParked,
        onSimulateSeatChange = viewModel::simulateSeatChange,
        storage = storage,
        onSavePreset = viewModel::savePreset,
        canExecute = viewModel.canExecute,
        onApply = viewModel::applyPreset,
        onRestore = viewModel::restoreSession,
        onAbandon = viewModel::abandonSession,
        selectedId = selectedId,
        onSelect = { selectedId = it },
        page = page,
        onPageChange = { page = it },
        restTools = { current ->
            when (current) {
                HomePage.SETTINGS -> Unit
                HomePage.SEATS -> PassengerSeatPanel(vehicleState)
                HomePage.PLACES -> RestPlacesPanel(restPlaceQuery, onPlacesRequestHandled)
                HomePage.ALARM -> RestToolsPanel(viewModel, viewModel.parkingPolicy.evaluate(vehicleState) == ParkingDecision.Allowed,
                    selectedId, storage.pendingSession?.id, requestedRestMinutes, onRestRequestHandled)
                HomePage.MUSIC -> Unit
                HomePage.ELECTRIC -> {
                    val battery = vehicleState.batteryPercent
                    val cable = vehicleState.chargePortConnected
                    Text(when {
                        battery == null -> "배터리 정보 없음"
                        vehicleState.batteryUsesNominalCapacity -> "배터리 약 $battery% (정격 용량 기준)"
                        else -> "배터리 약 $battery% (현재 용량 기준)"
                    })
                    RestBatteryWarning(battery, viewModel.parkingPolicy.evaluate(vehicleState) == ParkingDecision.Allowed)
                    RestClimatePanel(viewModel)
                    Text(when (cable) {
                        true -> "충전 케이블 연결됨"
                        false -> "충전 케이블 연결 안 됨"
                        null -> "충전 케이블 정보 없음"
                    })
                    Text(when (vehicleState.chargingStatus) {
                        ChargingStatus.CHARGING -> "충전 중"
                        ChargingStatus.TARGET_REACHED -> "목표 충전량 도달"
                        ChargingStatus.NOT_CHARGING -> "충전 중 아님"
                        ChargingStatus.ERROR -> "충전 오류"
                        null -> "충전 상태 정보 없음"
                    })
                }
            }
        },
    )
}

@Composable
fun HomeScreen(
    state: VehicleState,
    decision: ParkingDecision,
    isDemo: Boolean,
    onSimulateParked: (Boolean) -> Unit,
    onSimulateSeatChange: () -> Unit = {},
    storage: PresetStorageState = PresetStorageState(),
    onSavePreset: (String) -> Unit = {},
    canExecute: Boolean = false,
    onApply: (String) -> Unit = {},
    onRestore: (String) -> Unit = {},
    onAbandon: (String) -> Unit = {},
    selectedId: String? = null,
    onSelect: (String) -> Unit = {},
    page: HomePage = HomePage.SETTINGS,
    onPageChange: (HomePage) -> Unit = {},
    restTools: @Composable (HomePage) -> Unit = {},
) {
    val pageState = rememberSaveableStateHolder()
    Scaffold { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(if (isDemo) "자.peace 데모" else "자.peace", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    HomePage.entries.filter { !isDemo || it != HomePage.ELECTRIC }.forEach { target ->
                        OutlinedButton(enabled = target != page, onClick = { onPageChange(target) }) {
                            Text(target.label)
                        }
                    }
                }
                pageState.SaveableStateProvider(page.name) {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (isDemo && page == HomePage.SETTINGS) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("주차 상태", style = MaterialTheme.typography.titleLarge)
                                Text("기어: ${state.gear?.displayName() ?: "미확인"}, 속도: ${state.speedMetersPerSecond?.takeIf { it.isFinite() }?.let { "${(it * 3.6f).toInt()} km/h" } ?: "미확인"}")
                                Text(decision.displayMessage(), color = MaterialTheme.colorScheme.primary)
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedButton(onClick = { onSimulateParked(true) }) { Text("P, 정지") }
                                        OutlinedButton(onClick = { onSimulateParked(false) }) { Text("D, 주행") }
                                    }
                                    OutlinedButton(
                                        onClick = onSimulateSeatChange,
                                        enabled = decision == ParkingDecision.Allowed && storage.ready &&
                                            !storage.saving && !storage.executing && storage.pendingSession == null,
                                    ) { Text("모의 좌석 위치 변경") }
                                }
                        }
                        if (page == HomePage.SETTINGS) {
                            PresetStoragePanel(storage, decision == ParkingDecision.Allowed, canExecute,
                                onSavePreset, onApply, onRestore, onAbandon, selectedId, onSelect)
                        }
                        restTools(page)
                    }
                }
                RestMusicPanel(visible = page == HomePage.MUSIC)
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

private fun ParkingDecision.displayMessage(): String = when (this) {
    ParkingDecision.Allowed -> "모의 데이터에서 P와 정지 조건 충족"
    is ParkingDecision.Blocked -> when (reason) {
        ParkingBlockReason.NOT_CONNECTED -> "차량 연결 전에는 조작할 수 없습니다."
        ParkingBlockReason.DATA_UNAVAILABLE -> "정상적인 기어와 속도 값을 확인할 수 없습니다."
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
