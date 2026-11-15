package io.github.seungminshin97.japeace.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.time.Instant

internal enum class PhoneClockResult { OPENED, INVALID_TIME, UNAVAILABLE, DENIED }

internal fun requestPhoneAlarm(context: Context, target: Instant): PhoneClockResult {
    val time = alarmTime(target) ?: return PhoneClockResult.INVALID_TIME
    return openPhoneClock(context, Intent(AlarmClock.ACTION_SET_ALARM)
        .putExtra(AlarmClock.EXTRA_HOUR, time.hour)
        .putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
        .putExtra(AlarmClock.EXTRA_MESSAGE, "자.peace 휴식")
        .putExtra(AlarmClock.EXTRA_SKIP_UI, false))
}

internal fun requestPhoneTimer(context: Context, minutes: Int): PhoneClockResult =
    phoneTimerIntent(minutes)?.let { openPhoneClock(context, it) } ?: PhoneClockResult.INVALID_TIME

private fun openPhoneClock(context: Context, intent: Intent): PhoneClockResult = try {
    context.startActivity(intent)
    PhoneClockResult.OPENED
} catch (_: ActivityNotFoundException) {
    PhoneClockResult.UNAVAILABLE
} catch (_: SecurityException) {
    PhoneClockResult.DENIED
}

internal fun phoneTimerIntent(minutes: Int): Intent? {
    if (minutes !in 1..120) return null
    return Intent(AlarmClock.ACTION_SET_TIMER)
        .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
        .putExtra(AlarmClock.EXTRA_MESSAGE, "자.peace 휴식")
        .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
}
