package io.github.seungminshin97.japeace.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class RestPlaceSearchTest {
    @Test fun explicitDirectionMatchesTerminusAndSignedServiceArea() {
        val forward = place("소초", "인천기점 + 강릉종점")
        val reverse = place("소초", "강릉기점 + 인천종점")
        val service = place("횡성(강릉)", "하행")
        val places = listOf(reverse, forward, service)
        assertEquals(listOf(forward, service), searchRestPlaces(places, "영동선 강릉 방면", ""))
        assertEquals(listOf(forward), searchRestPlaces(places, "영동선 강릉방향", "졸음쉼터"))
        assertEquals(places, searchRestPlaces(places, "영동선", ""))
    }

    private fun place(name: String, direction: String) =
        RestPlace(name, if ('(' in name) "휴게소" else "졸음쉼터", "영동선", direction, 37.0, 127.0, "", "", "")
}
