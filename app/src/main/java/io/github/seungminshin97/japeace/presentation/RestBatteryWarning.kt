package io.github.seungminshin97.japeace.presentation

import android.content.Context
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.seungminshin97.japeace.MainActivity

private const val batteryWarningChannel = "vehicle_rest_battery"
private const val batteryWarningId = 702

internal fun isRestBatteryLow(percent: Int?, threshold: String): Boolean {
    val limit = threshold.toIntOrNull()?.takeIf { RestBatteryThreshold.isValid(it) } ?: return false
    return percent != null && percent in 0..limit
}

internal fun Context.updateRestBatteryNotification(low: Boolean) {
    val notifications = getSystemService(NotificationManager::class.java)
    if (!low) {
        notifications.cancel(batteryWarningId)
        return
    }
    if (!notifications.areNotificationsEnabled()) return
    notifications.createNotificationChannel(
        NotificationChannel(batteryWarningChannel, "휴식 배터리 경고", NotificationManager.IMPORTANCE_DEFAULT),
    )
    val openApp = PendingIntent.getActivity(
        this, batteryWarningId, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )
    notifications.notify(batteryWarningId, Notification.Builder(this, batteryWarningChannel)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("휴식 중 배터리 잔량 낮음")
        .setContentText("배터리 잔량이 설정한 기준 이하입니다.")
        .setContentIntent(openApp)
        .setOnlyAlertOnce(true)
        .setAutoCancel(true)
        .build())
}

@Composable
internal fun RestBatteryNotificationEffect(percent: Int?, parked: Boolean) {
    val context = LocalContext.current
    val settings = remember(context) { RestBatteryThreshold(context) }
    val monitoring = restBatteryMonitorRunning()
    var threshold by remember(settings) { mutableStateOf(settings.read()) }
    DisposableEffect(settings) {
        val observation = settings.observe { threshold = it }
        onDispose { observation.close() }
    }
    val low = parked && isRestBatteryLow(percent, threshold.toString())
    LaunchedEffect(low, monitoring) {
        if (!monitoring) context.updateRestBatteryNotification(low)
    }
}

@Composable
internal fun RestBatteryWarning(percent: Int?, parked: Boolean) {
    val context = LocalContext.current
    val settings = remember(context) { RestBatteryThreshold(context) }
    RestBatteryMonitorControls(parked)
    var savedThreshold by remember(settings) { mutableStateOf(settings.read()) }
    DisposableEffect(settings) {
        val observation = settings.observe { savedThreshold = it }
        onDispose { observation.close() }
    }
    var threshold by rememberSaveable { mutableStateOf(savedThreshold.toString()) }
    val limit = threshold.toIntOrNull()?.takeIf { RestBatteryThreshold.isValid(it) }
    val low = isRestBatteryLow(percent, savedThreshold.toString())
    OutlinedTextField(
        value = threshold,
        onValueChange = { value ->
            if (value.length <= 2 && value.all { it in '0'..'9' }) threshold = value
        },
        label = { Text("배터리 경고 기준 (%)") },
        singleLine = true,
        isError = limit == null,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
        ),
    )
    if (limit == null) Text("1~99 사이의 값을 입력하세요.")
    OutlinedButton(enabled = limit != null && limit != savedThreshold, onClick = {
        if (limit != null && settings.save(limit)) {
            threshold = limit.toString()
        }
    }) { Text("배터리 경고 기준 저장") }
    if (low) Text("배터리 잔량이 설정한 기준 이하입니다.")
}
