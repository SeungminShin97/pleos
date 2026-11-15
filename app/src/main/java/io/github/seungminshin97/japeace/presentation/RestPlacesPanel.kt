package io.github.seungminshin97.japeace.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class RestPlace(
    val name: String,
    val kind: String,
    val route: String,
    val direction: String,
    val latitude: Double,
    val longitude: Double,
    val toilet: String,
    val parking: String,
    val ev: String,
)

internal fun searchRestPlaces(places: List<RestPlace>, query: String, kind: String): List<RestPlace> {
    val explicitDirection = Regex("([가-힣0-9]+)\\s*(?:방면|방향)(?=\\s|$)").find(query)
    val heading = explicitDirection?.groupValues?.get(1)
    val terms = (explicitDirection?.let { query.removeRange(it.range) } ?: query)
        .trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return places.filter { place ->
        (kind.isEmpty() || place.kind == kind) &&
            (heading == null || place.matchesHeading(heading)) &&
            terms.all { term -> listOf(place.name, place.route, place.direction).any { it.contains(term, ignoreCase = true) } }
    }
}

private fun RestPlace.matchesHeading(heading: String): Boolean {
    val terminus = direction.substringAfterLast('+').trim().removeSuffix("종점").trim()
    val nameDirection = name.substringAfterLast('(', "").substringBefore(')').trim()
    return heading == terminus || heading == nameDirection
}

@Composable
internal fun RestPlacesPanel(requestedQuery: String?, onOpenHandled: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val source = remember(context) { RestPlaceCatalog(context) }
    val scope = rememberCoroutineScope()
    var places by remember { mutableStateOf<List<RestPlace>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("") }
    var shown by rememberSaveable { mutableStateOf(false) }
    var limit by rememberSaveable { mutableStateOf(10) }
    var selected by remember { mutableStateOf<RestPlace?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(requestedQuery) {
        if (requestedQuery != null) {
            shown = true
            query = requestedQuery
            kind = ""
            selected = null
            limit = 10
            onOpenHandled()
        }
    }
    OutlinedButton(onClick = {
        shown = !shown
    }) { Text(if (shown) "쉴 곳 검색 닫기" else "쉴 곳 검색") }
    if (!shown) return
    ProvideRestNavigation {
        val navigation = restPlaceRoute(places.orEmpty())

        fun refresh() {
            if (refreshing) return
            refreshing = true
            refreshMessage = null
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { runCatching { source.refresh() } }
                    result.onSuccess {
                        places = it
                        selected = selected?.takeIf(it::contains)
                        refreshMessage = "공공데이터 목록을 갱신했습니다."
                    }.onFailure {
                        refreshMessage = if (places != null) "갱신에 실패했습니다. 저장된 목록을 표시합니다."
                        else "목록을 내려받지 못했습니다."
                    }
                } finally {
                    refreshing = false
                }
            }
        }
        LaunchedEffect(source) {
            if (places == null) {
                val result = withContext(Dispatchers.IO) { runCatching { source.load() } }
                places = result.getOrNull()
                failed = result.isFailure
            }
            if (withContext(Dispatchers.IO) { source.needsRefresh() }) refresh()
        }
        OutlinedButton(enabled = !refreshing && (places != null || failed), onClick = ::refresh) {
            Text(if (refreshing) "갱신 중" else "공공데이터 갱신")
        }
        refreshMessage?.let { Text(it) }
        OutlinedTextField(query, { query = it; limit = 10 }, label = { Text("이름, 노선, 방향 검색") }, singleLine = true)
        Row {
            listOf("" to "전체", "졸음쉼터" to "졸음쉼터", "휴게소" to "휴게소").forEach { (value, label) ->
                OutlinedButton(onClick = { kind = value; limit = 10 }, enabled = kind != value) { Text(label) }
            }
        }
        val catalog = places
        if (catalog == null) {
            Text(if (failed) "공공데이터 목록을 읽을 수 없습니다." else "목록을 불러오는 중")
            return@ProvideRestNavigation
        }
        val route = navigation.points
        val matches = remember(catalog, query, kind, route) {
            rankRestPlaces(searchRestPlaces(catalog, query, kind), route)
        }
        if (route.zipWithNext().any { (start, end) -> start != end }) {
            Text("현재 위치·경유지·목적지 사이 직선 기준으로 정렬")
        }
        Text("검색 결과 ${matches.size}곳")
        selected?.let { place ->
            Text("선택한 장소 ${place.name}")
            val uniqueName = catalog.none { it.name == place.name &&
                (it.latitude != place.latitude || it.longitude != place.longitude) }
            RestPlaceNavigation(place, uniqueName)
        }
        matches.take(limit).forEach { place ->
            Text("${place.name} (${place.kind})")
            Text(listOf(place.route, place.direction).filter(String::isNotBlank).joinToString(" / "))
            Text(listOfNotNull(
                place.toilet.toFacilityLabel("화장실"),
                place.parking.toIntOrNull()?.let { "주차 ${it}면" },
                place.ev.toFacilityLabel("전기차 충전소"),
            ).ifEmpty { listOf("시설 정보 없음") }.joinToString(" / "))
            OutlinedButton(onClick = { selected = place }) { Text("${place.name} 선택") }
        }
        if (matches.size > limit) OutlinedButton(onClick = { limit += 10 }) { Text("더 보기") }
        Text("출처 공공데이터포털, 한국도로공사")
    }
}

private fun String.toFacilityLabel(name: String): String? = when (this) {
    "Y" -> "$name 있음"
    "N" -> "$name 없음"
    else -> null
}
