package io.github.seungminshin97.japeace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import io.github.seungminshin97.japeace.domain.policy.ParkingDecision
import io.github.seungminshin97.japeace.domain.rest.RestPreset
import io.github.seungminshin97.japeace.domain.rest.StoredSession
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
    val presets: List<RestPreset> = emptyList(),
    val pendingSession: StoredSession? = null,
    val message: String? = null,
    val error: String? = null,
)

class HomeViewModel(private val dependencies: AppDependencies) : ViewModel() {
    val vehicleState = dependencies.vehicleGateway.state
    val isDemo = dependencies.demoControls != null
    val parkingPolicy = ParkingPolicy()
    val vehiclePermissions = dependencies.vehiclePermissions
    private val mutableStorage = MutableStateFlow(PresetStorageState())
    val storage = mutableStorage.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                dependencies.restRepository.recoverInterruptedSession()
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
        if (!mutableStorage.value.ready || mutableStorage.value.saving) return
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
                mutableStorage.update { it.copy(message = "설정 ${preset.capture.settings.size}개를 저장했습니다. 자동 적용은 아직 지원하지 않습니다.") }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                Log.w("RestStorage", "Cannot capture or save preset", error)
                mutableStorage.update { it.copy(error = "설정을 읽거나 저장하지 못했습니다. 권한과 P 정지 상태를 확인해 주세요.") }
            } finally {
                mutableStorage.update { it.copy(saving = false) }
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

    class Factory(private val dependencies: () -> AppDependencies) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(dependencies()) as T
        }
    }
}
