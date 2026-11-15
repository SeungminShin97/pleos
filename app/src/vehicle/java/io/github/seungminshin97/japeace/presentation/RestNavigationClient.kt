package io.github.seungminshin97.japeace.presentation

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

internal data class RestNavigationState(
    val ready: Boolean = false,
    val pending: Boolean = false,
    val status: String = "내비 연결 중",
    val destination: DestinationInfo? = null,
    val waypoints: List<WaypointInfo>? = null,
    val currentLocation: RestRoutePoint? = null,
    val routeRevision: Long = 0,
) {
    fun contains(place: RestPlace, uniqueName: Boolean): Boolean =
        destination?.let { place.matches(it.name, it.latitude, it.longitude) } == true ||
            waypoints?.any {
                place.forWaypoint(uniqueName).matchesWaypoint(it, allowNameOnly = true) ||
                    place.matchesWaypoint(it, allowNameOnly = uniqueName)
            } == true

    val waypointIndex: WaypointIndex?
        get() = when (waypoints?.size) {
            0 -> WaypointIndex.FIRST
            1 -> WaypointIndex.SECOND
            else -> null
        }

    fun canAddWaypoint(place: RestPlace, uniqueName: Boolean) =
        ready && !pending && destination?.name?.isNotBlank() == true &&
            waypointIndex != null && !contains(place, uniqueName)
}

/** One navigation connection. Call commands on the main thread and close the client after use. */
@MainThread
internal class RestNavigationClient(context: Context) : AutoCloseable {
    private val navi = NaviHelper(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(RestNavigationState())
    val state = mutableState.asStateFlow()
    private var expected: PendingRouteRequest? = null
    private var confirmation: Job? = null

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            scope.launch {
                val point = RestRoutePoint(currentLocationInfo.latitude, currentLocationInfo.longitude)
                mutableState.value = state.value.copy(currentLocation = point.takeIf(RestRoutePoint::isUsable))
            }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            scope.launch {
                mutableState.value = state.value.copy(destination = destinationInfo)
                val request = expected ?: return@launch
                if (request.waypointPosition == null &&
                    request.place.matches(destinationInfo.name, destinationInfo.latitude, destinationInfo.longitude)
                ) finishRequest("목적지 설정 확인")
            }
        }

        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
            scope.launch {
                mutableState.value = state.value.copy(waypoints = waypointInfoList)
                val request = expected ?: return@launch
                val waypoint = request.waypointPosition?.let(waypointInfoList::getOrNull) ?: return@launch
                if (request.place.matchesWaypoint(waypoint, allowNameOnly = true)) finishRequest("경유지 추가 확인")
            }
        }

        override fun onDestinationChanged() {
            scope.launch {
                invalidateRoute()
                requestRouteInfo()
            }
        }

        override fun onRoutingCompleted() {
            scope.launch {
                navi.getCurrentLocationInfo()
                requestRouteInfo()
            }
        }

        override fun onRouteCancelled() {
            scope.launch { invalidateRoute() }
        }

        override fun onRouteEnded() {
            scope.launch { invalidateRoute() }
        }

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            scope.launch {
                invalidateRoute()
                requestRouteInfo()
            }
        }

        override fun onError(errorCode: NaviErrorCode) {
            scope.launch { finishRequest("내비 응답: $errorCode") }
        }
    }

    init {
        navi.initialize({
            scope.launch {
                navi.addListener(listener)
                mutableState.value = state.value.copy(ready = true, status = "내비 연결됨")
                navi.getCurrentLocationInfo()
            }
        }, {
            scope.launch { mutableState.value = state.value.copy(status = "내비에 연결할 수 없습니다.") }
        })
    }

    fun refreshRoute() {
        if (!state.value.ready || state.value.pending) return
        mutableState.value = state.value.copy(currentLocation = null, destination = null, waypoints = null)
        navi.getCurrentLocationInfo()
        requestRouteInfo()
    }

    fun setDestination(place: RestPlace) {
        if (!state.value.ready || state.value.pending) return
        beginRequest(PendingRouteRequest(place, null), "목적지 변경 요청 중")
        navi.requestRoute(RouteInfo(latitude = place.latitude, longitude = place.longitude,
            poiName = place.name, poiId = "", address = "", poiSubId = ""))
    }

    fun addWaypoint(place: RestPlace, uniqueName: Boolean) {
        val route = state.value
        if (!route.canAddWaypoint(place, uniqueName)) return
        val index = route.waypointIndex ?: return
        val position = route.waypoints?.size ?: return
        val waypoint = place.forWaypoint(uniqueName)
        beginRequest(PendingRouteRequest(waypoint, position), "경유지 추가 요청 중")
        navi.addWaypoint(RequestWaypointInfo(latitude = waypoint.latitude, longitude = waypoint.longitude,
            poiName = waypoint.name, waypointIndex = index, poiId = "", address = "", poiSubId = ""))
    }

    private fun beginRequest(request: PendingRouteRequest, status: String) {
        expected = request
        mutableState.value = state.value.copy(pending = true, status = status)
        confirmation = scope.launch {
            repeat(10) {
                delay(2_000)
                if (request.waypointPosition == null) navi.getDestinationInfo() else navi.getWaypointInfo()
            }
            delay(2_000)
            finishRequest("경로 요청 결과를 확인하지 못했습니다.")
        }
    }

    private fun finishRequest(status: String) {
        confirmation?.cancel()
        confirmation = null
        expected = null
        mutableState.value = state.value.copy(pending = false, status = status)
    }

    private fun requestRouteInfo() {
        navi.getDestinationInfo()
        navi.getWaypointInfo()
    }

    private fun invalidateRoute() {
        // Rerouting can end the old guide before the new route arrives.
        mutableState.value = state.value.copy(
            destination = null, waypoints = null, routeRevision = state.value.routeRevision + 1,
        )
    }

    override fun close() {
        scope.cancel()
        mutableState.value = RestNavigationState()
        navi.release()
    }
}

private data class PendingRouteRequest(val place: RestPlace, val waypointPosition: Int?)

private fun RestPlace.forWaypoint(uniqueName: Boolean): RestPlace =
    if (uniqueName) this else copy(name = directionalName())

private fun RestPlace.matchesWaypoint(waypoint: WaypointInfo, allowNameOnly: Boolean): Boolean {
    if (waypoint.latitude == 0.0 && waypoint.longitude == 0.0) {
        val lat = waypoint.displayLatitude
        val lng = waypoint.displayLongitude
        return if (lat != null && lng != null) matches(waypoint.name, lat, lng)
        else allowNameOnly && name == waypoint.name
    }
    return matches(waypoint.name, waypoint.latitude, waypoint.longitude)
}

private fun RestPlace.matches(name: String, latitude: Double, longitude: Double): Boolean =
    this.name == name && abs(this.latitude - latitude) <= 0.0005 && abs(this.longitude - longitude) <= 0.0005
