package io.github.seungminshin97.japeace.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.seungminshin97.japeace.R

@Composable
internal fun ExternalRestPlaceNavigation(place: RestPlace, route: RestPlaceRoute) {
    val context = LocalContext.current
    val start = route.currentLocation
    val selected = RestRoutePoint(place.latitude, place.longitude)
    val tmapAppKey = stringResource(R.string.tmap_app_key).trim()
    var unavailable by remember(place) { mutableStateOf<String?>(null) }
    OutlinedButton(onClick = {
        val destination = Uri.Builder()
            .scheme("nmap")
            .authority("navigation")
            .appendQueryParameter("dlat", place.latitude.toString())
            .appendQueryParameter("dlng", place.longitude.toString())
            .appendQueryParameter("dname", place.name)
            .appendQueryParameter("appname", context.packageName)
            .build()
        unavailable = context.openMap(destination, "com.nhn.android.nmap", "네이버 지도")
    }) { Text("네이버 지도에서 길찾기") }
    OutlinedButton(onClick = {
        val destination = kakaoMapUri(place, start)
        unavailable = context.openMap(destination, "net.daum.android.map", "카카오맵")
    }) { Text(if (start == null) "카카오맵에서 위치 보기" else "카카오맵에서 길찾기") }
    OutlinedButton(onClick = {
        unavailable = context.openMap(googleMapsRouteUri(start, selected), "com.google.android.apps.maps", "Google 지도")
    }) { Text("Google 지도에서 길찾기") }
    if (route.points.size in 2..6) {
        OutlinedButton(onClick = {
            val destination = kakaoMapRouteUri(
                route.points.first(),
                route.points.last(),
                route.points.drop(1).dropLast(1) + RestRoutePoint(place.latitude, place.longitude),
            )
            unavailable = context.openMap(destination, "net.daum.android.map", "카카오맵")
        }) { Text("카카오맵에서 경유지로 추가") }
    }
    if (route.points.size in 2..4) {
        OutlinedButton(onClick = {
            val destination = googleMapsRouteUri(
                route.points.first(),
                route.points.last(),
                route.points.drop(1).dropLast(1) + selected,
            )
            unavailable = context.openMap(destination, "com.google.android.apps.maps", "Google 지도")
        }) { Text("Google 지도에서 경유지로 추가") }
    }
    if (tmapAppKey.isNotEmpty()) {
        OutlinedButton(onClick = {
            unavailable = context.openMap(tmapRouteUri(place, tmapAppKey), null, "티맵 길찾기")
        }) { Text("티맵에서 길찾기") }
    }
    unavailable?.let { Text(it) }
}

internal fun kakaoMapUri(place: RestPlace, start: RestRoutePoint?): Uri {
    val end = RestRoutePoint(place.latitude, place.longitude)
    return if (start == null) {
        Uri.Builder().scheme("kakaomap").authority("look").appendQueryParameter("p", end.coordinates()).build()
    } else kakaoMapRouteUri(start, end, emptyList())
}

internal fun kakaoMapRouteUri(start: RestRoutePoint, end: RestRoutePoint, stops: List<RestRoutePoint>): Uri {
    val uri = Uri.Builder().scheme("kakaomap").authority("route")
        .appendQueryParameter("sp", start.coordinates())
        .appendQueryParameter("ep", end.coordinates())
        .appendQueryParameter("by", "car")
    stops.forEachIndexed { index, stop ->
        uri.appendQueryParameter(if (index == 0) "vp" else "vp${index + 1}", stop.coordinates())
    }
    return uri.build()
}

private fun RestRoutePoint.coordinates(): String = "$latitude,$longitude"

private fun googleMapsRouteUri(
    start: RestRoutePoint?,
    end: RestRoutePoint,
    stops: List<RestRoutePoint> = emptyList(),
): Uri {
    val uri = Uri.Builder().scheme("https").authority("www.google.com").path("/maps/dir/")
        .appendQueryParameter("api", "1")
        .appendQueryParameter("destination", end.coordinates())
        .appendQueryParameter("travelmode", "driving")
    start?.let { uri.appendQueryParameter("origin", it.coordinates()) }
    if (stops.isNotEmpty()) uri.appendQueryParameter("waypoints", stops.joinToString("|") { it.coordinates() })
    return uri.build()
}

@Composable
internal fun NearbyLodgingSearch(location: RestRoutePoint?) {
    val context = LocalContext.current
    var message by remember(location) { mutableStateOf<String?>(null) }
    OutlinedButton(onClick = {
        message = if (location == null) {
            "현재 위치를 확인할 수 없습니다."
        } else {
            val search = Uri.Builder()
                .scheme("kakaomap")
                .authority("search")
                .appendQueryParameter("q", "숙소")
                .appendQueryParameter("p", location.coordinates())
                .build()
            context.openMap(search, "net.daum.android.map", "카카오맵")
        }
    }) { Text("주변 숙소 검색") }
    message?.let { Text(it) }
}

private fun tmapRouteUri(place: RestPlace, appKey: String): Uri = Uri.Builder()
    .scheme("https")
    .authority("apis.openapi.sk.com")
    .path("/tmap/app/routes")
    .appendQueryParameter("appKey", appKey)
    .appendQueryParameter("goalname", place.name)
    .appendQueryParameter("goalx", place.longitude.toString())
    .appendQueryParameter("goaly", place.latitude.toString())
    .build()

private fun Context.openMap(destination: Uri, packageName: String?, label: String): String? {
    val message = "이 기기에서 ${label}을 열 수 없습니다."
    return try {
        startActivity(Intent(Intent.ACTION_VIEW, destination).setPackage(packageName))
        null
    } catch (_: ActivityNotFoundException) {
        message
    } catch (_: SecurityException) {
        message
    }
}
