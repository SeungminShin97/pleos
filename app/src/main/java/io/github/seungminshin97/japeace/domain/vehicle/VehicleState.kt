package io.github.seungminshin97.japeace.domain.vehicle

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE }
enum class ChargingStatus { CHARGING, TARGET_REACHED, NOT_CHARGING, ERROR }

enum class VehicleIssue { CONNECTING, PERMISSION_REQUIRED, DISCONNECTED, DATA_UNAVAILABLE, UNSUPPORTED, READ_FAILED }

/** Unknown is the default. Real adapters must confirm valid readings before allowing controls. */
data class VehicleState(
    val connected: Boolean = false,
    val readingsValid: Boolean = false,
    val gear: Gear? = null,
    val speedMetersPerSecond: Float? = null,
    val issue: VehicleIssue? = null,
    val batteryPercent: Int? = null,
    val batteryUsesNominalCapacity: Boolean = false,
    val chargePortConnected: Boolean? = null,
    val chargingStatus: ChargingStatus? = null,
)
