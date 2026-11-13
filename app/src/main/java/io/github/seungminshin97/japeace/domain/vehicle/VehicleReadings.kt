package io.github.seungminshin97.japeace.domain.vehicle

/** Observations for one service connection, using VHAL monotonic timestamps. */
class VehicleReadings {
    private var gear: Gear? = null
    private var speed: Float? = null
    private var gearTimestamp = -1L
    private var speedTimestamp = -1L

    fun gear(value: Gear?, timestamp: Long) {
        if (timestamp < gearTimestamp) return
        gearTimestamp = timestamp
        gear = value
    }

    fun speed(value: Float?, timestamp: Long) {
        if (timestamp < speedTimestamp) return
        speedTimestamp = timestamp
        speed = value?.takeIf { it.isFinite() }
    }

    fun invalidateGear() { gear = null }
    fun invalidateSpeed() { speed = null }

    fun snapshot(): VehicleState {
        val valid = gear != null && speed != null
        return VehicleState(true, valid, gear, speed, if (valid) null else VehicleIssue.DATA_UNAVAILABLE)
    }
}
