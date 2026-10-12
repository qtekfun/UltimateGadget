package com.qtekfun.ultimategadget.mapsdl

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * One Amazfit/Zepp device returned by the account, with the Bluetooth auth key that
 * UltimateGadget (Gadgetbridge) needs to pair it offline.
 */
data class HuamiDevice(
    val mac: String,
    val active: Boolean,
    val authKey: String,
) {
    /** Paste-ready form for Gadgetbridge's Auth Key field (0x-prefixed). */
    val pasteKey: String
        get() = if (authKey.startsWith("0x")) authKey else "0x$authKey"
}

/**
 * The account's home region decides which host holds its device list. The login/token exchange is
 * done against the global (US) auth servers, which huami-token uses for every account; only the
 * device-listing host changes. Suffixes confirmed in the wild: global `api-mifit`, Europe
 * `api-mifit-de`, and the US shards `api-mifit-us2` / `api-mifit-us3` (the latter is what the
 * official Gadgetbridge pairing script uses).
 */
enum class HuamiRegion(val label: String, val deviceHost: String) {
    GLOBAL("Global", "api-mifit.zepp.com"),
    EUROPE("Europa", "api-mifit-de.zepp.com"),
    US2("US", "api-mifit-us2.zepp.com"),
    US3("US (alt)", "api-mifit-us3.zepp.com"),
}

/**
 * Retrieves Amazfit / Zepp Bluetooth auth keys straight from Huami's servers, so a watch can be
 * paired with UltimateGadget without the Zepp app. This is a faithful Kotlin reimplementation of
 * the `amazfit` method of the huami-token tool (https://github.com/argrento/huami-token, MIT),
 * which the official Gadgetbridge pairing guide points users to.
 *
 * It lives in the map-downloader companion because that is the only part of UltimateGadget allowed
 * to reach the internet. The account email/password are used once for the login handshake, sent
 * only to Huami/Zepp over HTTPS, and never stored anywhere.
 *
 * Endpoints mirror huami-token's current (US) servers; accounts registered in other regions may
 * fail at the token step.
 */
object HuamiToken {

    // Auth hosts this flow talks to (global/US, as huami-token does for every account). The device
    // host is region-dependent and comes from HuamiRegion.
    private const val URL_TOKENS = "https://api-user-us2.zepp.com/v2/registrations/tokens"
    private const val URL_LOGIN = "https://api-mifit-us2.zepp.com/v2/client/login"
    // A-GNSS (AGPS/EPO) file listing, per file type (mirrors huami-token's GPS download).
    private const val URL_GPS = "https://api-mifit-us2.zepp.com/apps/com.xiaomi.hm.health/fileTypes/%s/files"

    private const val CHANNEL = "a100900101016"
    private const val CV = "151689_9.12.5"
    private const val VN = "9.12.5"
    private const val VB = "202509151347"
    private const val USER_AGENT = "Zepp/9.12.5 (Pixel 4; Android 12; Density/2.75)"

    // AES-128-CBC parameters used to wrap the credentials payload (from huami-token).
    private val ENC_KEY = "xeNtBVqzDc6tuNTh".toByteArray(Charsets.US_ASCII)
    private val ENC_IV = "MAAAYAAAAAAAAABg".toByteArray(Charsets.US_ASCII)

    class HuamiException(message: String) : IOException(message)

    /**
     * Logs in with [email] / [password] and returns the account's devices with their auth keys.
     * Runs blocking network I/O, so call it off the main thread.
     */
    fun fetchAuthKeys(
        email: String,
        password: String,
        region: HuamiRegion = HuamiRegion.GLOBAL,
    ): List<HuamiDevice> {
        val accessToken = getAccessToken(email, password)
        val login = login(accessToken)
        return getDevices(login.userId, login.appToken, region)
    }

    /**
     * Lists the account's devices from an already-established web-login session, skipping the
     * email/password handshake. [userId] and [appToken] come from the Zepp web login (the cookie
     * `hm-user-login-info` → `token_info`, or the `userid`/`apptoken` cookies), captured after the
     * user signs in — including any OTP or captcha — on Huami's own login page. Blocking I/O.
     */
    fun fetchAuthKeysWithToken(
        userId: String,
        appToken: String,
        region: HuamiRegion = HuamiRegion.US3,
    ): List<HuamiDevice> = getDevices(userId.trim(), appToken.trim(), region)

    // Step 1: encrypted credential exchange -> 303 redirect carrying the access token.
    private fun getAccessToken(email: String, password: String): String {
        val form = buildString {
            appendForm("emailOrPhone", email)
            append('&'); appendForm("state", "REDIRECTION")
            append('&'); appendForm("client_id", "HuaMi")
            append('&'); appendForm("password", password)
            append('&'); appendForm("redirect_uri", "https://s3-us-west-2.amazonaws.com/hm-registration/successsignin.html")
            append('&'); appendForm("region", "us-west-2")
            append('&'); appendForm("token", "access")
            append('&'); appendForm("token", "refresh")
            append('&'); appendForm("country_code", "US")
        }
        val encrypted = aesEncrypt(form.toByteArray(Charsets.UTF_8))

        val conn = (URL(URL_TOKENS).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("app_name", "com.huami.midong")
            setRequestProperty("appname", "com.huami.midong")
            setRequestProperty("cv", CV)
            setRequestProperty("v", "2.0")
            setRequestProperty("appplatform", "android_phone")
            setRequestProperty("vb", VB)
            setRequestProperty("vn", VN)
            setRequestProperty("user-agent", USER_AGENT)
            setRequestProperty("x-hm-ekv", "1")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        }
        try {
            conn.outputStream.use { it.write(encrypted) }
            val code = conn.responseCode
            if (code != 303 && code != 302) {
                throw HuamiException("Login rejected (HTTP $code). Check the email and password.")
            }
            val location = conn.getHeaderField("Location")
                ?: throw HuamiException("No redirect from the server; cannot read the token.")
            val access = queryParam(location, "access")
            if (access.isNullOrBlank()) {
                val err = queryParam(location, "error")
                throw HuamiException(
                    if (!err.isNullOrBlank()) "Login failed: $err" else "No access token in the server response."
                )
            }
            return access
        } finally {
            conn.disconnect()
        }
    }

    private data class Login(val userId: String, val appToken: String)

    // Step 2: exchange the access token for an app token + user id.
    private fun login(accessToken: String): Login {
        val body = buildString {
            appendForm("code", accessToken)
            append('&'); appendForm("device_id", UUID.randomUUID().toString())
            append('&'); appendForm("device_model", "android_phone")
            append('&'); appendForm("app_version", VN)
            append('&'); appendForm("dn", "api-mifit.zepp.com,api-user.zepp.com,api-mifit.zepp.com,api-watch.zepp.com,app-analytics.zepp.com,auth.zepp.com,api-analytics.zepp.com")
            append('&'); appendForm("third_name", "huami")
            append('&'); appendForm("source", "com.huami.watch.hmwatchmanager:9.12.5:151689")
            append('&'); appendForm("app_name", "com.huami.midong")
            append('&'); appendForm("country_code", "US")
            append('&'); appendForm("grant_type", "access_token")
            append('&'); appendForm("allow_registration", "false")
            append('&'); appendForm("lang", "en")
            append('&'); appendForm("countryState", "US-NY")
        }
        val conn = (URL(URL_LOGIN).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("app_name", "com.huami.webapp")
            setRequestProperty("appname", "com.huami.webapp")
            setRequestProperty("origin", "https://user.zepp.com")
            setRequestProperty("referer", "https://user.zepp.com/")
            setRequestProperty("user-agent", "Mozilla/5.0 (X11; Linux x86_64; rv:133.0) Gecko/20100101 Firefox/133.0")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("accept", "application/json, text/plain, */*")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val json = JSONObject(readBody(conn, "login"))
            val tokenInfo = json.optJSONObject("token_info")
                ?: throw HuamiException("Login response had no token_info.")
            val appToken = tokenInfo.optString("app_token")
            val userId = tokenInfo.optString("user_id")
            if (appToken.isNullOrBlank() || userId.isNullOrBlank()) {
                throw HuamiException("Login response missing app_token/user_id.")
            }
            return Login(userId, appToken)
        } finally {
            conn.disconnect()
        }
    }

    // Step 3: list the account's devices and read each one's auth key.
    private fun getDevices(userId: String, appToken: String, region: HuamiRegion): List<HuamiDevice> {
        val r1 = UUID.randomUUID().toString()
        val r2 = UUID.randomUUID().toString()
        val appId = (Math.random() * Long.MAX_VALUE).toLong().toString()
        val query = buildString {
            appendForm("r", r1)
            append('&'); appendForm("r", r2)
            append('&'); appendForm("enableMultiDeviceOnMultiType", "true")
            append('&'); appendForm("enableMultiDeviceOnMultiType", "true")
            append('&'); appendForm("userid", userId)
            append('&'); appendForm("appid", appId)
            append('&'); appendForm("channel", CHANNEL)
            append('&'); appendForm("country", "US")
            append('&'); appendForm("cv", CV)
            append('&'); appendForm("device", "android_32")
            append('&'); appendForm("device_type", "android_phone")
            append('&'); appendForm("enableMultiDevice", "true")
            append('&'); appendForm("lang", "en_US")
            append('&'); appendForm("timezone", "Europe/London")
            append('&'); appendForm("v", "2.0")
        }
        val devicesUrl = "https://${region.deviceHost}/users/$userId/devices?$query"
        val conn = (URL(devicesUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("country", "US")
            setRequestProperty("appplatform", "android_phone")
            setRequestProperty("x-request-id", UUID.randomUUID().toString())
            setRequestProperty("channel", CHANNEL)
            setRequestProperty("vb", VB)
            setRequestProperty("cv", CV)
            setRequestProperty("appname", "com.huami.midong")
            setRequestProperty("v", "2.0")
            setRequestProperty("vn", VN)
            setRequestProperty("apptoken", appToken)
            setRequestProperty("lang", "en_US")
            setRequestProperty("user-agent", USER_AGENT)
        }
        try {
            val json = JSONObject(readBody(conn, "devices"))
            val items: JSONArray = json.optJSONArray("items") ?: JSONArray()
            val out = ArrayList<HuamiDevice>(items.length())
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val mac = item.optString("macAddress", "??:??:??:??:??:??")
                val active = item.optInt("activeStatus", 0) != 0
                val additionalInfoStr = item.optString("additionalInfo", "{}")
                val authKey = runCatching { JSONObject(additionalInfoStr).optString("auth_key", "") }
                    .getOrDefault("")
                if (authKey.isNotBlank()) {
                    out += HuamiDevice(mac = mac, active = active, authKey = authKey)
                }
            }
            if (out.isEmpty()) {
                throw HuamiException("No devices with an auth key were found on this account.")
            }
            return out
        } finally {
            conn.disconnect()
        }
    }

    // ---- A-GNSS (AGPS) for Amazfit / Zepp OS ----

    /**
     * Log in to the Zepp/Huami account and build the Zepp OS A-GNSS package (UIHH container) the
     * watch accepts, from the account's current AGPS/EPO files. The email/password are used only
     * for the login handshake and never stored. Returns the UIHH bytes to save and install in
     * UltimateGadget (its existing Zepp OS AGPS installer validates and sends it).
     */
    fun fetchAgpsUihh(email: String, password: String): ByteArray {
        val accessToken = getAccessToken(email.trim(), password)
        val login = login(accessToken)
        val files = downloadGpsFiles(login.userId, login.appToken)
        return buildGpsUihh(files)
    }

    private fun downloadGpsFiles(userId: String, appToken: String): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        val appId = (Math.random() * Long.MAX_VALUE).toLong().toString()
        val r1 = UUID.randomUUID().toString()
        val r2 = UUID.randomUUID().toString()
        for (fileType in listOf("AGPS_ALM", "AGPSZIP", "LLE", "AGPS", "EPO", "LTO")) {
            val query = buildString {
                appendForm("r", r1); append('&'); appendForm("r", r2)
                append('&'); appendForm("userid", userId)
                append('&'); appendForm("appid", appId)
                append('&'); appendForm("country", "US")
                append('&'); appendForm("appplatform", "android_phone")
                append('&'); appendForm("hm-privacy-diagnostics", "false")
                append('&'); appendForm("hm-privacy-ceip", "false")
                append('&'); appendForm("x-request-id", UUID.randomUUID().toString())
            }
            val listUrl = URL_GPS.format(fileType) + "?" + query
            val body = runCatching {
                val conn = (URL(listUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; connectTimeout = 20_000; readTimeout = 30_000
                    setRequestProperty("apptoken", appToken)
                    setRequestProperty("appname", "com.huami.midong")
                    setRequestProperty("appplatform", "android_phone")
                    setRequestProperty("country", "US")
                    setRequestProperty("x-request-id", UUID.randomUUID().toString())
                    setRequestProperty("user-agent", USER_AGENT)
                }
                try { readBody(conn, "gps $fileType") } finally { conn.disconnect() }
            }.getOrNull() ?: continue
            val arr = runCatching { JSONArray(body) }.getOrNull() ?: continue
            val fileUrl = (0 until arr.length()).firstNotNullOfOrNull {
                arr.optJSONObject(it)?.optString("fileUrl")?.takeIf { u -> u.isNotBlank() }
            } ?: continue
            val name = fileUrl.substringAfterLast('/').substringBefore('?')
            val bytes = runCatching { downloadRaw(fileUrl, appToken) }.getOrNull() ?: continue
            out[name] = bytes
        }
        return out
    }

    private fun downloadRaw(url: String, appToken: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 20_000; readTimeout = 60_000
            setRequestProperty("apptoken", appToken)
            setRequestProperty("user-agent", USER_AGENT)
        }
        try {
            if (conn.responseCode !in 200..299) throw HuamiException("Descarga A-GPS falló (HTTP ${conn.responseCode}).")
            return conn.inputStream.readBytes()
        } finally { conn.disconnect() }
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (!e.isDirectory) out[e.name.substringAfterLast('/')] = zin.readBytes()
                e = zin.nextEntry
            }
        }
        return out
    }

    private fun le32(v: Long): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
    )

    private fun crc32(b: ByteArray): Long {
        val c = java.util.zip.CRC32(); c.update(b); return c.value
    }

    /** Assemble the UIHH A-GNSS container from the downloaded cep_7days.zip + lle_1week.zip. */
    private fun buildGpsUihh(files: Map<String, ByteArray>): ByteArray {
        val cepZip = files.entries.firstOrNull { it.key.contains("cep_7days") && it.key.endsWith(".zip") }?.value
            ?: throw HuamiException("La cuenta no devolvió el A-GPS (cep_7days.zip). ¿El reloj tiene GPS y A-GPS activado?")
        val lleZip = files.entries.firstOrNull { it.key.contains("lle_1week") && it.key.endsWith(".zip") }?.value
            ?: throw HuamiException("La cuenta no devolvió el A-GPS (lle_1week.zip).")
        val cep = readZipEntries(cepZip)
        val lle = readZipEntries(lleZip)
        // file name -> UIHH file-type id (same mapping huami-token uses).
        val spec = listOf(
            "gps_alm.bin" to 0x05, "gln_alm.bin" to 0x0F,
            "lle_bds.lle" to 0x86, "lle_gps.lle" to 0x87, "lle_glo.lle" to 0x88,
            "lle_gal.lle" to 0x89, "lle_qzss.lle" to 0x8A,
        )
        val content = java.io.ByteArrayOutputStream()
        for ((fname, type) in spec) {
            val fc = (if (type >= 0x86) lle[fname] else cep[fname])
                ?: throw HuamiException("Falta $fname en el paquete A-GPS")
            content.write(byteArrayOf(0x01, type.toByte()))
            content.write(le32(fc.size.toLong()))
            content.write(le32(crc32(fc)))
            content.write(fc)
        }
        val c = content.toByteArray()
        val out = java.io.ByteArrayOutputStream()
        out.write("UIHH".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0x04, 0, 0, 0, 0, 0, 0, 0x01))
        out.write(le32(crc32(c)))
        out.write(ByteArray(6))
        out.write(le32(c.size.toLong()))
        out.write(ByteArray(6))
        out.write(c)
        return out.toByteArray()
    }

    private fun aesEncrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(ENC_KEY, "AES"), IvParameterSpec(ENC_IV))
        return cipher.doFinal(data)
    }

    private fun StringBuilder.appendForm(name: String, value: String) {
        append(URLEncoder.encode(name, "UTF-8"))
        append('=')
        append(URLEncoder.encode(value, "UTF-8"))
    }

    private fun queryParam(url: String, name: String): String? {
        val q = url.substringAfter('?', "").substringBefore('#')
        for (pair in q.split('&')) {
            val idx = pair.indexOf('=')
            if (idx <= 0) continue
            if (pair.substring(0, idx) == name) {
                return java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            }
        }
        return null
    }

    private fun readBody(conn: HttpURLConnection, what: String): String {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            throw HuamiException("Request '$what' failed (HTTP $code).")
        }
        return text
    }
}
