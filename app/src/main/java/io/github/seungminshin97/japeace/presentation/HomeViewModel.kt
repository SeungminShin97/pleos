package io.github.seungminshin97.japeace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import io.github.seungminshin97.japeace.domain.policy.ParkingDecision
import io.github.seungminshin97.japeace.domain.rest.RestPreset
import io.github.seungminshin97.japeace.domain.rest.StoredSession
import io.github.seungminshin97.japeace.domain.rest.SessionState
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import io.github.seungminshin97.japeace.AppDependencies
import io.github.seungminshin97.japeace.domain.policy.ParkingPolicy

data class PresetStorageState(
    val loading: Boolean = true,
    val ready: Boolean = false,
    val saving: Boolean = false,
    val executing: Boolean = false,
    val presets: List<RestPreset> = emptyList(),
    val pendingSession: StoredSession? = null,
    val lastResult: StoredSession? = null,
    val message: String? = null,
    val error: String? = null,
)

class HomeViewModel(private val dependencies: AppDependencies) : ViewModel() {
    val vehicleState = dependencies.vehicleGateway.state
    val isDemo = dependencies.demoControls != null
    val canExecute = dependencies.demoRestCoordinator != null
    val parkingPolicy = ParkingPolicy()
    val vehiclePermissions = dependencies.vehiclePermissions
    private val mutableStorage = MutableStateFlow(PresetStorageState())
    val storage = mutableStorage.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                dependencies.demoRestCoordinator?.recover()
                    ?: dependencies.restRepository.recoverInterruptedSession()
                combine(dependencies.restRepository.observePresets(), dependencies.restRepository.observeActiveSession()) { presets, session -> presets to session }
                    .collect { (presets, session) -> mutableStorage.update { it.copy(loading = false, ready = true, presets = presets, pendingSession = session) } }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w("RestStorage", "Cannot open saved records", error)
                mutableStorage.update { it.copy(loading = false, ready = false, error = "저장 기록을 열지 못했습니다. 기존 데이터는 삭제하지 않았습니다.") }
            }
        }
    }

    fun savePreset(name: String) {
        if (!mutableStorage.value.ready || mutableStorage.value.saving || mutableStorage.value.executing) return
        if (parkingPolicy.evaluate(vehicleState.value) != ParkingDecision.Allowed) {
            mutableStorage.update { it.copy(error = "P와 정지 상태에서만 저장할 수 있습니다.") }
            return
        }
        if (name.trim().length !in 1..40) {
            mutableStorage.update { it.copy(error = "설정 이름은 1~40자로 입력해 주세요.") }
            return
        }
        mutableStorage.update { it.copy(saving = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                val capture = dependencies.settingsReader.captureDriverSeat()
                check(parkingPolicy.evaluate(vehicleState.value) == ParkingDecision.Allowed)
                val preset = dependencies.restRepository.savePreset(name, capture)
                mutableStorage.update { it.copy(message = "설정 ${preset.capture.settings.size}개를 저장했습니다.") }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                Log.w("RestStorage", "Cannot capture or save preset", error)
                mutableStorage.update { it.copy(error = "설정을 읽거나 저장하지 못했습니다. 권한과 P 정지 상태를 확인해 주세요.") }
            } finally {
                mutableStorage.update { it.copy(saving = false) }
            }
        }
    }

    fun applyPreset(presetId: String) {
        val coordinator = dependencies.demoRestCoordinator ?: return
        if (mutableStorage.value.pendingSession != null) return
        if (mutableStorage.value.presets.none { it.id == presetId }) return
        runExecution { coordinator.apply(UUID.randomUUID().toString(), presetId) }
    }

    fun restoreSession(sessionId: String) {
        val coordinator = dependencies.demoRestCoordinator ?: return
        if (mutableStorage.value.pendingSession?.id != sessionId) return
        runExecution { coordinator.restore(sessionId) }
    }

    fun abandonSession(sessionId: String) {
        val coordinator = dependencies.demoRestCoordinator ?: return
        if (mutableStorage.value.pendingSession?.id != sessionId) return
        runExecution(requiresParking = false) {
            coordinator.abandon(sessionId)
            null
        }
    }

    private fun runExecution(requiresParking: Boolean = true, action: suspend () -> StoredSession?) {
        val state = mutableStorage.value
        if (!state.ready || state.saving || state.executing) return
        if (requiresParking && parkingPolicy.evaluate(vehicleState.value) != ParkingDecision.Allowed) return
        mutableStorage.update { it.copy(executing = true, error = null, message = null, lastResult = null) }
        viewModelScope.launch {
            try {
                val result = action()
                mutableStorage.update {
                    it.copy(lastResult = result, message = when (result?.state) {
                        SessionState.APPLIED -> "프리셋 적용 완료"
                        SessionState.COMPLETED -> "이전 설정으로 복귀 완료"
                        SessionState.RECOVERY_REQUIRED -> "작업 중단. 항목별 결과를 확인해 주세요."
                        null -> "세션을 종료했습니다."
                        else -> null
                    })
                }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                Log.w("RestExecution", "Cannot finish session action", error)
                mutableStorage.update { it.copy(error = "작업을 완료하지 못했습니다. 주차 상태와 작업 기록을 확인해 주세요.") }
            } finally {
                mutableStorage.update { it.copy(executing = false) }
            }
        }
    }

    fun start() = dependencies.vehicleGateway.start()
    fun stop() = dependencies.vehicleGateway.stop()

    override fun onCleared() {
        dependencies.vehicleGateway.close()
        super.onCleared()
    }

    fun simulateParked(parked: Boolean) {
        dependencies.demoControls?.setParked(parked)
    }

    fun simulateSeatChange() {
        val state = mutableStorage.value
        if (!state.ready || state.saving || state.executing || state.pendingSession != null) return
        val position = dependencies.demoControls?.changeSeatPosition() ?: return
        mutableStorage.update { it.copy(message = "모의 좌석 앞뒤 위치를 ${position}(으)로 변경했습니다.", error = null, lastResult = null) }
    }

    class Factory(private val dependencies: () -> AppDependencies) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(dependencies()) as T
        }
    }
}
