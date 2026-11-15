package io.github.seungminshin97.japeace.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.seungminshin97.japeace.integration.vehicle.PleosVehicleGateway
import io.github.seungminshin97.japeace.domain.policy.ParkingDecision
import io.github.seungminshin97.japeace.domain.policy.ParkingPolicy
import io.github.seungminshin97.japeace.domain.policy.ParkingBlockReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class BatteryMonitorStartResult { REQUESTED, ALREADY_RUNNING, PARK_REQUIRED, NOTIFICATIONS_DISABLED, FAILED }

internal class RestBatteryMonitor : Service() {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var gateway: PleosVehicleGateway? = null
    private val threshold by lazy { RestBatteryThreshold(this) }
    private var thresholdObservation: AutoCloseable? = null
    private val parkingPolicy = ParkingPolicy()
    private var previousLow: Boolean? = null

    private fun refreshWarning() {
        val state = gateway?.state?.value ?: return
        val low = parkingPolicy.evaluate(state) == ParkingDecision.Allowed &&
            isRestBatteryLow(state.batteryPercent, threshold.read().toString())
        if (low != previousLow) {
            updateRestBatteryNotification(low)
            previousLow = low
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == stopAction) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (gateway != null) return START_NOT_STICKY
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(channel, "배터리 감시", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 703,
            Intent(this, RestBatteryMonitor::class.java).setAction(stopAction), PendingIntent.FLAG_IMMUTABLE)
        startForeground(703, Notification.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("휴식 배터리 감시 중")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "감시 중지", stop).build())
            .build())
        val source = PleosVehicleGateway(applicationContext)
        gateway = source
        mutableRunning.value = true
        thresholdObservation = threshold.observe { refreshWarning() }
        scope.launch {
            source.state.collect { state ->
                when (val decision = parkingPolicy.evaluate(state)) {
                    ParkingDecision.Allowed -> refreshWarning()
                    is ParkingDecision.Blocked -> {
                        if (decision.reason == ParkingBlockReason.NOT_IN_PARK ||
                            decision.reason == ParkingBlockReason.VEHICLE_MOVING
                        ) stopSelf()
                        else refreshWarning()
                    }
                }
            }
        }
        source.start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        thresholdObservation?.close()
        gateway?.close()
        mutableRunning.value = false
        updateRestBatteryNotification(false)
        super.onDestroy()
    }

    companion object {
        private val mutableRunning = MutableStateFlow(false)
        val running = mutableRunning.asStateFlow()
        private const val channel = "vehicle_rest_battery_monitor"
        private const val stopAction = "stop"

        fun requestStart(context: Context, parked: Boolean): BatteryMonitorStartResult {
            if (running.value) return BatteryMonitorStartResult.ALREADY_RUNNING
            if (!parked) return BatteryMonitorStartResult.PARK_REQUIRED
            if (!context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
                return BatteryMonitorStartResult.NOTIFICATIONS_DISABLED
            }
            return try {
                context.startForegroundService(Intent(context, RestBatteryMonitor::class.java))
                BatteryMonitorStartResult.REQUESTED
            } catch (_: IllegalStateException) {
                BatteryMonitorStartResult.FAILED
            } catch (_: SecurityException) {
                BatteryMonitorStartResult.FAILED
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RestBatteryMonitor::class.java))
        }
    }
}

@Composable
internal fun restBatteryMonitorRunning(): Boolean =
    RestBatteryMonitor.running.collectAsStateWithLifecycle().value

@Composable
internal fun RestBatteryMonitorControls(parked: Boolean) {
    val context = LocalContext.current
    val running = restBatteryMonitorRunning()
    var error by remember { mutableStateOf<String?>(null) }
    OutlinedButton(enabled = running || parked, onClick = {
        if (running) {
            RestBatteryMonitor.stop(context)
            error = null
        } else {
            error = when (RestBatteryMonitor.requestStart(context, parked)) {
                BatteryMonitorStartResult.REQUESTED, BatteryMonitorStartResult.ALREADY_RUNNING -> null
                BatteryMonitorStartResult.PARK_REQUIRED -> "P 정지 상태에서 감시를 시작해 주세요."
                BatteryMonitorStartResult.NOTIFICATIONS_DISABLED -> "배터리 감시를 시작하려면 앱 알림을 허용하세요."
                BatteryMonitorStartResult.FAILED -> "배터리 감시를 시작할 수 없습니다."
            }
        }
    }) { Text(if (running) "배터리 감시 중지" else "배터리 감시 시작") }
    error?.let { Text(it) }
}
