package io.github.seungminshin97.japeace.presentation

import kotlin.math.cos
import kotlin.math.hypot

internal data class RestRoutePoint(val latitude: Double, val longitude: Double) {
    fun isUsable(): Boolean = latitude in 31.0..45.0 && longitude in 122.0..133.0
}

internal data class RestPlaceRoute(
    val currentLocation: RestRoutePoint? = null,
    val points: List<RestRoutePoint> = emptyList(),
)

internal fun RestPlace.directionalName() = "$name ($route $direction)"

internal fun catalogWaypointPoint(name: String, catalog: List<RestPlace>): RestRoutePoint? =
    catalog.asSequence()
        .filter { it.directionalName() == name }
        .map { RestRoutePoint(it.latitude, it.longitude) }
        .filter(RestRoutePoint::isUsable)
        .distinct()
        .take(2)
        .toList()
        .singleOrNull()

internal fun rankRestPlaces(places: List<RestPlace>, route: List<RestRoutePoint>): List<RestPlace> {
    val segments = route.zipWithNext().filter { (start, end) -> start != end }
    if (segments.isEmpty()) return places
    return places.sortedBy { place ->
        segments.minOf { (start, end) ->
            val scale = cos(Math.toRadians(start.latitude))
            val dx = (end.longitude - start.longitude) * scale
            val dy = end.latitude - start.latitude
            val x = (place.longitude - start.longitude) * scale
            val y = place.latitude - start.latitude
            val fraction = ((x * dx + y * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
            hypot(x - fraction * dx, y - fraction * dy)
        }
    }
}
