package io.github.seungminshin97.japeace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.seungminshin97.japeace.AppDependencies
import io.github.seungminshin97.japeace.domain.policy.ParkingPolicy

class HomeViewModel(private val dependencies: AppDependencies) : ViewModel() {
    val vehicleState = dependencies.vehicleGateway.state
    val isDemo = dependencies.demoControls != null
    val parkingPolicy = ParkingPolicy()

    fun simulateParked(parked: Boolean) {
        dependencies.demoControls?.setParked(parked)
    }

    class Factory(private val dependencies: AppDependencies) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(dependencies) as T
        }
    }
}
