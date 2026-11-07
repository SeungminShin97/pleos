package io.github.seungminshin97.japeace.domain.vehicle

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE }

/** Unknown is the default. Real adapters must confirm valid readings before allowing controls. */
data class VehicleState(
    val connected: Boolean = false,
    val readingsValid: Boolean = false,
    val gear: Gear? = null,
    val speedMetersPerSecond: Float? = null,
)
