package io.github.seungminshin97.japeace.domain.vehicle

import kotlin.math.roundToInt

internal data class BatteryReading(val percent: Int, val usesNominalCapacity: Boolean)

internal fun batteryReading(
    remaining: Float?,
    currentCapacity: Float?,
    nominalCapacity: () -> Float?,
): BatteryReading? {
    if (remaining == null || !remaining.isFinite() || remaining < 0) return null
    val current = currentCapacity?.takeIf { it.isFinite() && it > 0 }
    val capacity = current ?: nominalCapacity()?.takeIf { it.isFinite() && it > 0 } ?: return null
    if (remaining > capacity) return null
    return BatteryReading((100 * (remaining / capacity)).roundToInt(), current == null)
}
