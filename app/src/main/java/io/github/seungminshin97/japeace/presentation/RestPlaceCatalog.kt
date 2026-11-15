package io.github.seungminshin97.japeace.presentation

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

internal class RestPlaceCatalog(private val context: Context) {
    private val cache = AtomicFile(File(context.filesDir, "rest-places.json"))

    fun needsRefresh(now: Long = System.currentTimeMillis()): Boolean {
        val savedAt = cache.baseFile.lastModified()
        return savedAt <= 0 || now - savedAt !in 0 until TimeUnit.DAYS.toMillis(7)
    }

    fun load(): List<RestPlace> {
        return runCatching { parse(JSONArray(cache.readFully().decodeToString())) }
            .getOrElse {
                val bundled = context.assets.open("rest-places.json").bufferedReader().use { it.readText() }
                parse(JSONArray(bundled))
            }
    }

    fun refresh(): List<RestPlace> {
        val normalized = JSONArray()
        for ((id, kind, nameField) in listOf(
            Triple(15028203, "졸음쉼터", "SHLTR_NM"),
            Triple(15025446, "휴게소", "ENTRPS_NM"),
        )) {
            val header = JSONObject(fetch("/download/columList.json?pk=$id&ext=JSON", id))
            val table = header.getJSONObject("tableVO")
            val tableName = table.getString("svcTableNm")
            require(tableName.matches(Regex("[a-z_]+")))
            val fields = listOf(nameField, "ROAD_ROUTE_NM", "ROAD_ROUTE_DRC", "LATITUDE", "LONGITUDE",
                "TOILET_YN", "PRKPLCE_CO", "REFERENCE_DATE") + if (id == 15025446) listOf("ELCTY_YN") else emptyList()
            val count = header.getInt("totalCount")
            require(count in 1..5000)
            val columns = fields.joinToString("&") { "colNmList=$it" }
            val rows = JSONArray(fetch(
                "/download/standard.json?publicDataPk=$id&totalCount=$count&svcTableNm=$tableName&perPage=5000&page=1&$columns",
                id,
            ))
            check(rows.length() == count) { "Incomplete catalog" }
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                normalized.put(JSONObject()
                    .put("name", row.getString(nameField))
                    .put("kind", kind)
                    .put("route", row.optString("ROAD_ROUTE_NM"))
                    .put("direction", row.optString("ROAD_ROUTE_DRC"))
                    .put("lat", row.optString("LATITUDE"))
                    .put("lon", row.optString("LONGITUDE"))
                    .put("toilet", row.optString("TOILET_YN"))
                    .put("parking", row.optString("PRKPLCE_CO"))
                    .put("ev", row.optString("ELCTY_YN"))
                    .put("referenceDate", row.optString("REFERENCE_DATE")))
            }
        }
        val result = parse(normalized)
        val stream = cache.startWrite()
        try {
            stream.write(normalized.toString().encodeToByteArray())
            cache.finishWrite(stream)
        } catch (error: IOException) {
            cache.failWrite(stream)
            throw error
        }
        return result
    }

    private fun parse(rows: JSONArray): List<RestPlace> =
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.getJSONObject(index)
            val lat = row.optDouble("lat")
            val lon = row.optDouble("lon")
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            RestPlace(row.getString("name"), row.getString("kind"), row.optString("route"),
                row.optString("direction"), lat, lon, row.optString("toilet"), row.optString("parking"), row.optString("ev"))
        }.also { places ->
            check(places.any { it.kind == "졸음쉼터" } && places.any { it.kind == "휴게소" })
        }

    private fun fetch(path: String, id: Int): String {
        val origin = "https://www.data.go.kr"
        val connection = URL(origin + path).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
        connection.setRequestProperty("Referer", "$origin/data/$id/standard.do")
        return try {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
