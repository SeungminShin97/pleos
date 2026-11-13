package io.github.seungminshin97.japeace

import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import io.github.seungminshin97.japeace.presentation.HomeRoute
import io.github.seungminshin97.japeace.presentation.HomeViewModel
import io.github.seungminshin97.japeace.presentation.theme.JapeaceTheme

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModel.Factory { createDependencies(applicationContext) }
    }
    private var requestedPermission = false
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) homeViewModel.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedPermission = savedInstanceState?.getBoolean("requestedVehiclePermission") ?: false
        setContent {
            JapeaceTheme {
                HomeRoute(homeViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        homeViewModel.start()
        val missing = homeViewModel.vehiclePermissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty() && !requestedPermission) {
            requestedPermission = true
            permissionRequest.launch(missing.toTypedArray())
        }
    }

    override fun onStop() {
        homeViewModel.stop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("requestedVehiclePermission", requestedPermission)
        super.onSaveInstanceState(outState)
    }
}
