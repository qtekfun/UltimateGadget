package com.qtekfun.ultimategadget.mapsdl

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * On-demand client for Huawei Health's golf course catalog, anonymous endpoints.
 *
 * Lets the user browse country -> city -> course and (later) fetch the course map for a course
 * they pick, so a Huawei watch can get golf course maps without Huawei Health. Reverse-engineered
 * from the Health golf H5 mini-app; the "...Anon" endpoints need no login/token.
 *
 * This is per-user, on-demand use (the user picks what to fetch) — not a bulk harvester.
 * Lives in the map-downloader companion because it is the only part with internet access.
 *
 * Endpoints confirmed working anonymously (resultCode 0): country list, city list, course list.
 * Host is region-dependent (GRS); EU default below.
 */
object GolfApi {

    // EU region host. Other regions use e.g. healthoperation-drcn.things.dbankcloud.cn (CN).
    private const val BASE =
        "https://healthoperation-dre.things.dbankcloud.com/operationgeneral/app/v1/golfcourse"

    data class Country(val id: String, val code: String, val name: String)
    data class City(val id: String, val name: String, val province: String)
    data class Course(
        val id: Long,
        val name: String,
        val totalLength: Int,
        val lat: Double,
        val lon: Double,
        val version: String,
        val mapType: Int,
    )

    class GolfException(message: String) : RuntimeException(message)

    /** Common request wrapper that the H5 `h5ProRequest` adds (validated). */
    private fun envelope(language: String, country: String): JSONObject = JSONObject().apply {
        put("source", 1)
        put("ts", System.currentTimeMillis())
        put("appId", "com.huawei.health")
        put("iVersion", 5)
        put("deviceType", "0")
        put("deviceId", UUID.randomUUID().toString())
        put("sysVersion", "15")
        put("language", language)
        put("timeZone", "Europe/Madrid")
        put("countryCode", country)
        put("clientType", 1)
        put("clientVersion", "1501001")
        put("territory", country)
        put("locale", language)
    }

    private fun post(endpoint: String, body: JSONObject): JSONObject {
        val conn = (URL("$BASE/$endpoint").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw GolfException("HTTP $code")
            val json = JSONObject(text)
            val rc = json.optInt("resultCode", -1)
            if (rc != 0) throw GolfException("resultCode $rc: ${json.optString("resultDesc")}")
            return json
        } finally {
            conn.disconnect()
        }
    }

    /** `language` like "es-ES"; `country` is the ISO code used in the envelope, e.g. "ES". */
    fun countries(language: String = "es-ES", country: String = "ES"): List<Country> {
        val body = envelope(language, country).put("language", language)
        val arr: JSONArray = post("getCountryListAnon", body).optJSONArray("countryList") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                Country(it.optString("countryId"), it.optString("countryCode"), it.optString("countryName"))
            }
        }.sortedBy { it.name }
    }

    fun cities(countryId: String, language: String = "es-ES", country: String = "ES"): List<City> {
        val body = envelope(language, country).put("countryId", countryId)
        val arr: JSONArray = post("getCitysAnon", body).optJSONArray("cities") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                City(it.optString("cityId"), it.optString("city"), it.optString("province"))
            }
        }.sortedBy { it.name }
    }

    fun courses(cityId: String, language: String = "es-ES", country: String = "ES"): List<Course> {
        val body = envelope(language, country).apply {
            put("queryType", 1)
            put("from", 0)
            put("size", 50)
            put("keyWords", " ")
            put("categoryId", "golfcourse")
            put("cityId", cityId)
            put("devConStatus", 0)
        }
        val arr: JSONArray = post("getCoursesAnon", body).optJSONArray("courseData") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                val coords = it.optJSONObject("coordinates")
                Course(
                    id = it.optLong("courseId"),
                    name = it.optString("name"),
                    totalLength = it.optInt("totalLength"),
                    lat = coords?.optDouble("lat", 0.0) ?: 0.0,
                    lon = coords?.optDouble("lon", 0.0) ?: 0.0,
                    version = it.optString("version"),
                    mapType = it.optInt("courseMapType"),
                )
            }
        }.sortedBy { it.name }
    }

    data class CourseMap(val courseId: Long, val version: String, val url: String, val size: Long)

    /**
     * Course map metadata for [courseId]. The watch needs the "vector" map; note `type` MUST be
     * "lite_wearable" — the anonymous server rejects "wearable" with "parameter invalid".
     * The returned [CourseMap.url] is a short-lived (~2h) signed CDN ZIP.
     */
    fun courseMap(courseId: Long, language: String = "es-ES", country: String = "ES"): CourseMap? {
        val body = envelope(language, country).apply {
            put("courseIds", JSONArray().put(courseId))
            put("language", language)
            put("type", "lite_wearable")
            put("deviceLevel", "vector")
        }
        val el = post("getCourseMapDataAnon", body).optJSONArray("courseMapData")?.optJSONObject(0)
            ?: return null
        val url = el.optString("url")
        if (url.isBlank()) return null
        return CourseMap(el.optLong("courseId", courseId), el.optString("version"), url, el.optLong("size"))
    }

    /** Download the signed ZIP at [url] and return the inner course `.bin` (name to bytes). */
    fun downloadMapBin(url: String): Pair<String, ByteArray> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
        }
        try {
            if (conn.responseCode !in 200..299) throw GolfException("download HTTP ${conn.responseCode}")
            java.util.zip.ZipInputStream(conn.inputStream.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".bin")) {
                        return entry.name.substringAfterLast('/') to zip.readBytes()
                    }
                    entry = zip.nextEntry
                }
            }
            throw GolfException("no .bin inside the course zip")
        } finally {
            conn.disconnect()
        }
    }
}
