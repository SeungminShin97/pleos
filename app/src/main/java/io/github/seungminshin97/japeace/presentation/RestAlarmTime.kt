package io.github.seungminshin97.japeace.presentation

import java.time.Duration
import java.time.LocalTime
import java.time.Instant
import java.time.ZonedDateTime

internal fun restAlarmDelayMillis(input: String, now: ZonedDateTime = ZonedDateTime.now()): Long? =
    restAlarmClockTarget(input, now)?.let { Duration.between(now.toInstant(), it).toMillis().coerceAtLeast(1) }

internal fun restAlarmClockTarget(input: String, now: ZonedDateTime = ZonedDateTime.now()): Instant? {
    if (!Regex("[0-9]{2}:[0-9]{2}").matches(input)) return null
    val hour = input.substring(0, 2).toInt()
    val minute = input.substring(3).toInt()
    if (hour !in 0..23 || minute !in 0..59) return null
    val time = LocalTime.of(hour, minute)
    val today = now.toLocalDate().atTime(time).atZone(now.zone)
    val target = if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
    return target.toInstant()
}
