package io.github.seungminshin97.japeace.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestPlaceRouteRankingTest {
    private val start = RestRoutePoint(37.0, 127.0)
    private val end = RestRoutePoint(37.0, 128.0)

    @Test fun rankingUsesWaypointSegments() {
        val alongDirectPath = place("직선", 37.0, 127.5)
        val alongWaypointPath = place("경유지 부근", 37.5, 127.5)
        val places = listOf(alongDirectPath, alongWaypointPath)
        val waypoint = RestRoutePoint(37.5, 127.5)

        assertEquals(places, rankRestPlaces(places, listOf(start, end)))
        assertEquals(places.reversed(), rankRestPlaces(places, listOf(start, waypoint, end)))
    }

    @Test fun missingOrDuplicatePointsPreserveSearchOrder() {
        val places = listOf(place("첫째", 37.3, 127.1), place("둘째", 37.0, 127.0))
        assertEquals(places, rankRestPlaces(places, emptyList()))
        assertEquals(places, rankRestPlaces(places, listOf(start, start)))
    }

    @Test fun catalogFallbackRequiresUnambiguousDirection() {
        val north = place("전주", 35.86, 127.05).copy(route = "호남선", direction = "북쪽")
        val south = north.copy(latitude = 35.87, direction = "남쪽")
        val catalog = listOf(north, south)

        assertNull(catalogWaypointPoint(north.name, catalog))
        assertEquals(RestRoutePoint(35.87, 127.05), catalogWaypointPoint(south.directionalName(), catalog))
        assertNull(catalogWaypointPoint(south.directionalName(), catalog + south.copy(latitude = 35.88)))
    }

    private fun place(name: String, latitude: Double, longitude: Double) =
        RestPlace(name, "휴게소", "", "", latitude, longitude, "", "", "")
}
