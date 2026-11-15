package io.github.seungminshin97.japeace.presentation

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import io.github.seungminshin97.japeace.MainActivity
import io.github.seungminshin97.japeace.domain.policy.ParkingDecision
import io.github.seungminshin97.japeace.domain.policy.ParkingPolicy
import io.github.seungminshin97.japeace.domain.vehicle.VehicleState
import kotlinx.coroutines.flow.MutableSharedFlow

internal val restAlarmDeliveries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

internal enum class VehicleAlarmResult {
    SCHEDULED, INVALID_TIME, PARK_REQUIRED, EXACT_ALARM_PERMISSION_REQUIRED,
    NOTIFICATION_PERMISSION_REQUIRED, NOTIFICATIONS_DISABLED, FAILED,
}

internal class VehicleRestAlarm(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val state = context.getSharedPreferences("vehicle-rest-alarm", Context.MODE_PRIVATE)
    private val intent = Intent(context, RestAlarmReceiver::class.java)
    private val flags = PendingIntent.FLAG_IMMUTABLE

    private fun canSchedule() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    fun requestScheduleAfter(delayMillis: Long, vehicleState: VehicleState): VehicleAlarmResult {
        if (delayMillis <= 0) return VehicleAlarmResult.INVALID_TIME
        if (ParkingPolicy().evaluate(vehicleState) != ParkingDecision.Allowed) return VehicleAlarmResult.PARK_REQUIRED
        if (!canSchedule()) return VehicleAlarmResult.EXACT_ALARM_PERMISSION_REQUIRED
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return VehicleAlarmResult.NOTIFICATION_PERMISSION_REQUIRED
        if (!notifications.areNotificationsEnabled()) return VehicleAlarmResult.NOTIFICATIONS_DISABLED
        return try {
            scheduleAfter(delayMillis)
            VehicleAlarmResult.SCHEDULED
        } catch (_: SecurityException) {
            VehicleAlarmResult.FAILED
        }
    }

    fun soundStatus(): String? {
        ensureChannel()
        val channel = notifications.getNotificationChannel(channelId)
        val audio = context.getSystemService(AudioManager::class.java)
        return when {
            !notifications.areNotificationsEnabled() -> "이 기기의 알림이 꺼져 있습니다."
            channel.importance == NotificationManager.IMPORTANCE_NONE -> "휴식 알람 채널이 꺼져 있습니다."
            channel.sound == null ||
                (channel.sound == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) &&
                    RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM) == null) ->
                "휴식 알람 소리가 무음으로 설정되어 있습니다."
            audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0 -> "알람 음량이 0입니다."
            else -> null
        }
    }

    fun openSoundSettings() {
        ensureChannel()
        context.startActivity(Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channelId))
    }

    fun previewRingtone(): Ringtone? {
        if (soundStatus() != null) return null
        val channel = notifications.getNotificationChannel(channelId)
        val sound = channel.sound ?: return null
        return RingtoneManager.getRingtone(context, sound)?.apply {
            audioAttributes = channel.audioAttributes
            isLooping = false
        }
    }

    fun scheduledTimeMillis(): Long? {
        val remaining = state.getLong("deadline", 0) - SystemClock.elapsedRealtime()
        if (remaining <= 0 || !canSchedule() ||
            PendingIntent.getBroadcast(context, 0, intent, flags or PendingIntent.FLAG_NO_CREATE) == null
        ) return null
        return System.currentTimeMillis() + remaining
    }

    private fun scheduleAfter(delayMillis: Long) {
        require(delayMillis > 0)
        val deadline = SystemClock.elapsedRealtime() + delayMillis
        val request = Intent(intent).putExtra("deadline", deadline)
        val pending = PendingIntent.getBroadcast(context, 0, request, flags or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, pending)
            state.edit().putLong("deadline", deadline).apply()
            notifications.cancel(notificationId)
        } catch (error: SecurityException) {
            pending.cancel()
            state.edit().remove("deadline").apply()
            throw error
        }
    }

    fun cancel() {
        context.stopService(Intent(context, RestAlarmPlaybackService::class.java))
        state.edit().remove("deadline").apply()
        PendingIntent.getBroadcast(context, 0, intent, flags or PendingIntent.FLAG_NO_CREATE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
        notifications.cancel(notificationId)
    }

    fun deliver(deadline: Long) {
        if (deadline <= 0 || deadline != state.getLong("deadline", 0) || SystemClock.elapsedRealtime() < deadline) return
        restAlarmDeliveries.tryEmit(Unit)
        cancel()
        ensureChannel()
        if (!notifications.areNotificationsEnabled() ||
            notifications.getNotificationChannel(channelId).importance == NotificationManager.IMPORTANCE_NONE
        ) return
        if (soundStatus() == null &&
            notifications.currentInterruptionFilter in setOf(
                NotificationManager.INTERRUPTION_FILTER_ALL, NotificationManager.INTERRUPTION_FILTER_ALARMS,
            )
        ) {
            try {
                context.startForegroundService(Intent(context, RestAlarmPlaybackService::class.java))
                return
            } catch (error: IllegalStateException) {
                android.util.Log.w("RestAlarm", "Playback service could not start", error)
            } catch (error: SecurityException) {
                android.util.Log.w("RestAlarm", "Playback service permission denied", error)
            }
        }
        notifications.notify(notificationId, alarmNotification(channelId))
    }

    fun alarmNotification(channel: String, dismissible: Boolean = false): Notification {
        val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)
        val builder = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("휴식 시간이 끝났습니다")
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(openApp)
            .setAutoCancel(true)
        if (dismissible) {
            val stop = PendingIntent.getBroadcast(
                context, 0, Intent(intent).setAction(stopPlaybackAction), flags,
            )
            builder.setOngoing(true).setDeleteIntent(stop)
                .addAction(Notification.Action.Builder(null, "알람 끄기", stop).build())
        }
        return builder.build()
    }

    private fun ensureChannel() {
        if (notifications.getNotificationChannel(channelId) != null) return
        val channel = NotificationChannel(channelId, "휴식 알람", NotificationManager.IMPORTANCE_HIGH)
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        notifications.createNotificationChannel(channel)
    }

    companion object {
        private const val channelId = "vehicle_rest_alarm"
        const val notificationId = 701
        const val stopPlaybackAction = "io.github.seungminshin97.japeace.STOP_REST_ALARM"
    }
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == VehicleRestAlarm.stopPlaybackAction) {
            context.stopService(Intent(context, RestAlarmPlaybackService::class.java))
        } else {
            VehicleRestAlarm(context).deliver(intent.getLongExtra("deadline", 0))
        }
    }
}

class RestAlarmPlaybackService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var notification: Notification? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel("vehicle_rest_alarm_playback", "울리는 휴식 알람",
            NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null) }
        manager.createNotificationChannel(channel)
        val alarm = VehicleRestAlarm(this)
        notification = null
        try {
            startForeground(VehicleRestAlarm.notificationId, alarm.alarmNotification(channel.id, dismissible = true))
        } catch (error: RuntimeException) {
            android.util.Log.w("RestAlarm", "Playback service could not enter foreground", error)
            manager.notify(VehicleRestAlarm.notificationId, alarm.alarmNotification(channel.id))
            stopSelf(startId)
            return START_NOT_STICKY
        }
        ringtone?.stop()
        ringtone = runCatching {
            alarm.previewRingtone()?.apply {
                isLooping = true
                play()
            }
        }.getOrNull()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            notification = alarm.alarmNotification(channel.id)
            stopSelf(startId)
        }, if (ringtone == null) 0 else 30_000)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ringtone?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notification?.let {
            getSystemService(NotificationManager::class.java).notify(VehicleRestAlarm.notificationId, it)
        }
        super.onDestroy()
    }
}
