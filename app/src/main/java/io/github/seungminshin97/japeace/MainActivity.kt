package io.github.seungminshin97.japeace

import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import io.github.seungminshin97.japeace.presentation.HomeRoute
import io.github.seungminshin97.japeace.presentation.HomeViewModel
import io.github.seungminshin97.japeace.presentation.theme.JapeaceTheme
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModel.Factory { createDependencies(applicationContext) }
    }
    private var requestedPermission = false
    private val requestedRestMinutes = mutableStateOf<Int?>(null)
    private val restPlaceQuery = mutableStateOf<String?>(null)
    private val requestedRestSettings = mutableStateOf(false)
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) homeViewModel.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedPermission = savedInstanceState?.getBoolean("requestedVehiclePermission") ?: false
        requestedRestMinutes.value = if (savedInstanceState == null) readRestMinutes(intent)
        else savedInstanceState.getInt("requestedRestMinutes").takeIf { it in 1..120 }
        restPlaceQuery.value = if (savedInstanceState == null) readRestPlaceQuery(intent)
        else savedInstanceState.getString("restPlaceQuery")
        requestedRestSettings.value = savedInstanceState?.getBoolean("requestedRestSettings") ?: opensRestSettings(intent)
        setContent {
            JapeaceTheme {
                HomeRoute(
                    homeViewModel, requestedRestMinutes.value, restPlaceQuery.value,
                    requestedRestSettings = requestedRestSettings.value,
                    onSettingsRequestHandled = { requestedRestSettings.value = false },
                    onPlacesRequestHandled = { restPlaceQuery.value = null },
                ) { requestedRestMinutes.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedRestMinutes.value = readRestMinutes(intent)
        restPlaceQuery.value = readRestPlaceQuery(intent)
        requestedRestSettings.value = opensRestSettings(intent)
    }

    private fun opensRestSettings(intent: Intent) =
        intent.action == "$packageName.OPEN_REST_SETTINGS" || intent.action == "OPEN_REST_SETTINGS"

    private fun readRestPlaceQuery(intent: Intent): String? {
        if (intent.action != "$packageName.OPEN_REST_PLACES") return null
        return (readActionParameter(intent, "query") as? String)?.trim()?.take(80).orEmpty()
    }

    private fun readRestMinutes(intent: Intent): Int? {
        if (intent.action != "$packageName.PREPARE_REST_ALARM") return null
        val minutes = readActionParameter(intent, "minutes")?.toString()?.toDoubleOrNull() ?: return null
        return minutes.toInt().takeIf { it in 1..120 && it.toDouble() == minutes }
    }

    private fun readActionParameter(intent: Intent, name: String): Any? =
        runCatching {
            intent.getStringExtra("ACTION_PARAM")?.let { raw ->
                val decoded = org.json.JSONTokener(raw).nextValue()
                val parameters = if (decoded is String) JSONObject(decoded) else decoded as? JSONObject
                parameters?.opt(name)
            } ?: intent.extras?.get(name)
        }.getOrNull()

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
        requestedRestMinutes.value?.let { outState.putInt("requestedRestMinutes", it) }
        restPlaceQuery.value?.let { outState.putString("restPlaceQuery", it) }
        outState.putBoolean("requestedRestSettings", requestedRestSettings.value)
        super.onSaveInstanceState(outState)
    }
}
