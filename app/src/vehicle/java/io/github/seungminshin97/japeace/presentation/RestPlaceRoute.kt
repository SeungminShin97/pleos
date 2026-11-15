package io.github.seungminshin97.japeace.presentation

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

internal val LocalRestNavigation = staticCompositionLocalOf<RestNavigationClient> {
    error("ProvideRestNavigation is missing")
}

@Composable
internal fun ProvideRestNavigation(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val navigation = remember(context) { RestNavigationClient(context) }
    DisposableEffect(navigation) { onDispose { navigation.close() } }
    CompositionLocalProvider(LocalRestNavigation provides navigation, content = content)
}

@Composable
internal fun restPlaceRoute(catalog: List<RestPlace>): RestPlaceRoute {
    val navigation = LocalRestNavigation.current
    val state by navigation.state.collectAsStateWithLifecycle()
    var requested by remember(navigation) { mutableStateOf(false) }
    var timedOutRevision by remember(navigation) { mutableStateOf<Long?>(null) }
    val start = state.currentLocation
    val end = state.destination?.let { RestRoutePoint(it.latitude, it.longitude) }?.takeIf(RestRoutePoint::isUsable)
    val waypoints = state.waypoints
    val stops = remember(waypoints, catalog) {
        waypoints?.map { waypoint ->
            RestRoutePoint(waypoint.latitude, waypoint.longitude).takeIf(RestRoutePoint::isUsable)
                ?: waypoint.displayLatitude?.let { lat ->
                    waypoint.displayLongitude?.let { lng ->
                        RestRoutePoint(lat, lng).takeIf(RestRoutePoint::isUsable)
                    }
                }
                ?: catalogWaypointPoint(waypoint.name, catalog)
        }
    }
    val missingRoute = start == null || end == null || stops == null
    val timedOut = requested && missingRoute && timedOutRevision == state.routeRevision
    val waiting = requested && missingRoute && !timedOut
    LaunchedEffect(state.routeRevision, state.pending) {
        if (requested && !state.pending && missingRoute) {
            navigation.refreshRoute()
        }
    }
    LaunchedEffect(waiting, state.routeRevision) {
        if (waiting) {
            val revision = state.routeRevision
            delay(8_000)
            timedOutRevision = revision
        }
    }
    OutlinedButton(enabled = state.ready && !state.pending && !waiting, onClick = {
        requested = true
        timedOutRevision = null
        navigation.refreshRoute()
    }) { Text("현재 경로 기준으로 정렬") }
    if (waiting) Text("정렬에 필요한 내비 정보 대기 중")
    if (timedOut) Text("현재 경로 정보를 확인할 수 없습니다. 기본 검색 결과를 표시합니다.")
    val missingStops = stops?.count { it == null } ?: 0
    if (requested && !missingRoute && missingStops > 0) {
        Text("좌표를 확인할 수 없는 경유지 ${missingStops}곳을 정렬에서 제외했습니다.")
    }
    val points = if (requested && start != null && end != null && stops != null) {
        listOf(start) + stops.filterNotNull() + end
    } else {
        emptyList()
    }
    return RestPlaceRoute(currentLocation = start, points = points)
}
