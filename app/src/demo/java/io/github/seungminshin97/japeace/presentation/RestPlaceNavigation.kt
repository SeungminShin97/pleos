package io.github.seungminshin97.japeace.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
internal fun RestPlaceNavigation(place: RestPlace, uniqueName: Boolean) {
    Text("데모 좌표 ${place.latitude}, ${place.longitude}")
}
