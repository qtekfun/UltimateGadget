package com.qtekfun.ultimategadget.mapsdl

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.TreeMap

/**
 * One wearable returned by the Mi Fitness account, with the Bluetooth auth key that UltimateGadget
 * (Gadgetbridge) needs to pair it offline. Xiaomi keys are 32 hex chars (16 bytes); Gadgetbridge
 * wants them 0x-prefixed.
 */
data class XiaomiDevice(
    val name: String,
    val mac: String,
    val authKey: String,
) {
    /** Paste-ready form for Gadgetbridge's Auth Key field (0x-prefixed). */
    val pasteKey: String
        get() = if (authKey.startsWith("0x")) authKey else "0x$authKey"
}

/**
 * Retrieves Xiaomi (Mi Fitness / Xiaomi Wear) Bluetooth auth keys straight from Xiaomi's health
 * servers, so a newer Mi Band (8/9) or Xiaomi watch can be paired with UltimateGadget without the
 * Mi Fitness app. This is a faithful Kotlin reimplementation of the `xiaomi` method of the
 * huami-token tool (https://codeberg.org/argrento/huami-token, MIT) — the same tool the official
 * Gadgetbridge pairing guide points users to for the Huami/Zepp accounts ([HuamiToken]).
 *
 * The flow has two parts:
 *  1. Mi Account login (account.xiaomi.com `serviceLogin` → `serviceLoginAuth2` → follow the STS
 *     `location` to get a `serviceToken`), service id `miothealth`.
 *  2. An encrypted call to the Mi Fitness health backend (`hlth.io.mi.com`) listing the account's
 *     bound devices; each wearable carries its `auth_key`. The request/response are RC4-drop[1024]
 *     encrypted and SHA1-signed exactly as the Mi Fitness app does ([XiaomiCrypto]).
 *
 * Like [HuamiToken], this lives in the map-downloader companion because that is the only part of
 * UltimateGadget allowed to reach the internet. The account email/password are used once for the
 * login handshake, sent only to Xiaomi over HTTPS, and never stored anywhere.
 *
 * Two-factor and captcha challenges are not handled: if the account requires them the login fails
 * with the server's message. Not yet validated on a device — pending a test with a real Xiaomi
 * wearable and account.
 */
object XiaomiToken {

    private const val URL_SERVICE_LOGIN = "https://account.xiaomi.com/pass/serviceLogin"
    private const val URL_SERVICE_LOGIN_AUTH2 = "https://account.xiaomi.com/pass/serviceLoginAuth2"
    // Device list lives on the base domain, not a regional subdomain (per huami-token).
    private const val URL_SOURCE_LIST = "https://hlth.io.mi.com/app/v1/source/get_source_list"
    private const val SOURCE_LIST_PATH = "/app/v1/source/get_source_list"

    private const val SID = "miothealth"
    private const val UA_WEB =
        "Mozilla/5.0 (Linux; Android 12; Pixel 4 Build/SP1A.210812.016.C1; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.200 Mobile Safari/537.36"
    private const val UA_API = "Android-12-9.8.348i-google-Pixel 4"
    // region_tag hint; the source list is served from the base domain regardless.
    private const val REGION_TAG = "de"

    private const val JSON_PREFIX = "&&&START&&&"

    class XiaomiException(message: String) : IOException(message)

    private data class Session(
        val ssecurity: String,
        val serviceToken: String,
        val userId: String,
        val cUserId: String,
    )

    /**
     * Logs in with [email] / [password] against the Mi Fitness account and returns the account's
     * wearables with their auth keys. Runs blocking network I/O, so call it off the main thread.
     */
    fun fetchAuthKeys(email: String, password: String): List<XiaomiDevice> {
        val session = login(email.trim(), password)
        return getSourceList(session)
    }

    /**
     * Lists the account's wearables from a browser-login session, so the OTP and captcha are
     * handled on Xiaomi's own login page. [userId] is the numeric account id and [passToken] the
     * long-lived cookie set after a successful web sign-in on account.xiaomi.com; both are captured
     * from the WebView cookies. No password is sent. Blocking I/O.
     */
    fun fetchAuthKeysWithPassToken(userId: String, passToken: String): List<XiaomiDevice> {
        val session = loginWithPassToken(userId.trim(), passToken.trim())
        return getSourceList(session)
    }

    // Re-login without a password: serviceLogin carrying userId + passToken cookies returns the
    // session (code 0 with ssecurity/nonce/location) directly, no credential or captcha step.
    private fun loginWithPassToken(userId: String, passToken: String): Session {
        val url = "$URL_SERVICE_LOGIN?_json=true&sid=$SID&_locale=en_US"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", UA_WEB)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "en-US,en;q=0.5")
            setRequestProperty("Cookie", "userId=$userId; passToken=$passToken")
        }
        val auth = try {
            val code = conn.responseCode
            if (code != 200) throw XiaomiException("serviceLogin (passToken) falló (HTTP $code).")
            val json = parseXiaomiJson(readBody(conn))
            if (json.optInt("code", -1) != 0) {
                val desc = json.optString("description").ifBlank { "código ${json.optInt("code", -1)}" }
                throw XiaomiException("Sesión Xiaomi no válida: $desc. Vuelve a iniciar sesión.")
            }
            val ssecurity = json.optString("ssecurity")
            val location = json.optString("location")
            if (ssecurity.isBlank() || location.isBlank()) {
                throw XiaomiException("La sesión Xiaomi no devolvió ssecurity/location. Vuelve a iniciar sesión.")
            }
            Auth(
                ssecurity = ssecurity,
                nonce = json.opt("nonce")?.toString().orEmpty(),
                userId = json.opt("userId")?.toString().orEmpty().ifBlank { userId },
                cUserId = json.optString("cUserId"),
                location = location,
            )
        } finally {
            conn.disconnect()
        }
        val serviceToken = getServiceToken(auth)
        return Session(auth.ssecurity, serviceToken, auth.userId, auth.cUserId)
    }

    // ---- Mi Account login (sid=miothealth) ----

    private fun login(email: String, password: String): Session {
        val deviceId = "an_" + md5Hex(email, upper = false)
        val page = serviceLogin(email, deviceId)
        val auth = serviceLoginAuth2(email, password, page, deviceId)
        val serviceToken = getServiceToken(auth)
        return Session(auth.ssecurity, serviceToken, auth.userId, auth.cUserId)
    }

    private data class LoginPage(val sign: String, val qs: String, val callback: String)

    // Step 1: GET serviceLogin -> _sign, qs, callback.
    private fun serviceLogin(email: String, deviceId: String): LoginPage {
        val url = "$URL_SERVICE_LOGIN?_json=true&sid=$SID&_locale=en_US"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", UA_WEB)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "en-US,en;q=0.5")
            setRequestProperty("Cookie", "userId=$email; deviceId=$deviceId")
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw XiaomiException("serviceLogin falló (HTTP $code).")
            val json = parseXiaomiJson(readBody(conn))
            val sign = json.optString("_sign")
            val qs = json.optString("qs")
            val callback = json.optString("callback")
            if (sign.isBlank() || qs.isBlank() || callback.isBlank()) {
                throw XiaomiException("Respuesta de serviceLogin sin _sign/qs/callback.")
            }
            return LoginPage(sign, qs, callback)
        } finally {
            conn.disconnect()
        }
    }

    private data class Auth(
        val ssecurity: String,
        val nonce: String,
        val userId: String,
        val cUserId: String,
        val location: String,
    )

    // Step 2: POST serviceLoginAuth2 with the credentials -> ssecurity, nonce, location.
    private fun serviceLoginAuth2(email: String, password: String, page: LoginPage, deviceId: String): Auth {
        val body = buildString {
            appendForm("qs", page.qs)
            append('&'); appendForm("callback", page.callback)
            append('&'); appendForm("_json", "true")
            append('&'); appendForm("_sign", page.sign)
            append('&'); appendForm("user", email)
            append('&'); appendForm("hash", md5Hex(password, upper = true))
            append('&'); appendForm("sid", SID)
            append('&'); appendForm("_locale", "en_US")
        }
        val conn = (URL(URL_SERVICE_LOGIN_AUTH2).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("User-Agent", UA_WEB)
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "en-US,en;q=0.5")
            setRequestProperty("Cookie", "deviceId=$deviceId")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val json = parseXiaomiJson(readBody(conn))
            if (json.optInt("code", -1) != 0) {
                val desc = json.optString("description").ifBlank { "código ${json.optInt("code", -1)}" }
                throw XiaomiException("Login Xiaomi rechazado: $desc. Revisa email/contraseña (el 2FA/captcha no está soportado).")
            }
            val ssecurity = json.optString("ssecurity")
            val location = json.optString("location")
            if (ssecurity.isBlank() || location.isBlank()) {
                throw XiaomiException("Login Xiaomi sin ssecurity/location (¿la cuenta pide verificación extra?).")
            }
            return Auth(
                ssecurity = ssecurity,
                nonce = json.opt("nonce")?.toString().orEmpty(),
                userId = json.opt("userId")?.toString().orEmpty(),
                cUserId = json.optString("cUserId"),
                location = location,
            )
        } finally {
            conn.disconnect()
        }
    }

    // Step 3: follow the STS location (with clientSign) to collect the serviceToken cookie.
    private fun getServiceToken(auth: Auth): String {
        val clientSign = XiaomiCrypto.base64Encode(
            XiaomiCrypto.sha1("nonce=${auth.nonce}&${auth.ssecurity}".toByteArray(Charsets.UTF_8)),
        )
        val startUrl = auth.location + "&clientSign=" + URLEncoder.encode(clientSign, "UTF-8")
        val jar = getFollowingRedirects(startUrl)
        return jar["serviceToken"]
            ?: throw XiaomiException("No se obtuvo serviceToken de Xiaomi (login incompleto).")
    }

    /** GET [startUrl] following redirects manually, carrying cookies forward, returning the jar. */
    private fun getFollowingRedirects(startUrl: String): Map<String, String> {
        val jar = LinkedHashMap<String, String>()
        var url = startUrl
        var hops = 0
        while (hops++ < 8) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", UA_WEB)
                if (jar.isNotEmpty()) {
                    setRequestProperty("Cookie", jar.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
            }
            try {
                val code = conn.responseCode
                for ((key, values) in conn.headerFields) {
                    if (key != null && key.equals("Set-Cookie", ignoreCase = true)) {
                        for (raw in values) {
                            val pair = raw.substringBefore(';')
                            val name = pair.substringBefore('=').trim()
                            val value = pair.substringAfter('=', "").trim()
                            if (name.isNotEmpty()) jar[name] = value
                        }
                    }
                }
                if (jar.containsKey("serviceToken")) return jar
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return jar
                    url = URL(URL(url), loc).toString()
                } else {
                    return jar
                }
            } finally {
                conn.disconnect()
            }
        }
        return jar
    }

    // ---- Device listing (encrypted Mi Fitness health API) ----

    private fun getSourceList(session: Session): List<XiaomiDevice> {
        // status=1 -> bound wearables, carrying auth_key in their detail.
        val data = "{\"page_size\":50,\"status\":1}"
        val nonce = XiaomiCrypto.generateNonce()
        val encrypted = XiaomiCrypto.encryptParams("POST", SOURCE_LIST_PATH, mapOf("data" to data), nonce, session.ssecurity)
        val form = encrypted.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }

        val conn = (URL(URL_SOURCE_LIST).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("User-Agent", UA_API)
            setRequestProperty("region_tag", REGION_TAG)
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Cookie", "cUserId=${session.cUserId}; serviceToken=${session.serviceToken}; locale=en_us")
        }
        val body: String
        try {
            conn.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            body = readBody(conn)
            if (code !in 200..299) throw XiaomiException("Listado de dispositivos Xiaomi falló (HTTP $code).")
        } finally {
            conn.disconnect()
        }

        // The body is RC4-encrypted base64; fall back to raw JSON for server error messages.
        val json = runCatching { JSONObject(XiaomiCrypto.decryptResponse(body, nonce, session.ssecurity)) }
            .recoverCatching { JSONObject(body) }
            .getOrElse { throw XiaomiException("Respuesta ilegible del servidor Xiaomi.") }

        val list = json.optJSONObject("result")?.optJSONArray("list") ?: JSONArray()
        val out = ArrayList<XiaomiDevice>(list.length())
        for (i in 0 until list.length()) {
            val src = list.optJSONObject(i) ?: continue
            val detailRaw = src.opt("detail")
            val detail: JSONObject? = when {
                detailRaw is JSONObject -> detailRaw
                detailRaw is String -> runCatching { JSONObject(detailRaw) }.getOrNull()
                else -> null
            }
            val authKey = detail?.optString("auth_key").orEmpty()
            if (authKey.isBlank()) continue
            val mac = detail?.optString("mac").takeUnless { it.isNullOrBlank() }
                ?: src.optString("mac", "??:??:??:??:??:??")
            val name = src.optString("name").trim().ifBlank { "Xiaomi" }
            out += XiaomiDevice(name = name, mac = mac, authKey = authKey)
        }
        if (out.isEmpty()) {
            throw XiaomiException("No se encontraron dispositivos con auth key en esta cuenta Xiaomi.")
        }
        return out
    }

    // ---- small helpers ----

    private fun parseXiaomiJson(text: String): JSONObject {
        val trimmed = if (text.startsWith(JSON_PREFIX)) text.substring(JSON_PREFIX.length) else text
        return JSONObject(trimmed)
    }

    private fun md5Hex(s: String, upper: Boolean): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return if (upper) hex.uppercase() else hex
    }

    private fun readBody(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun StringBuilder.appendForm(name: String, value: String) {
        append(URLEncoder.encode(name, "UTF-8"))
        append('=')
        append(URLEncoder.encode(value, "UTF-8"))
    }
}

/**
 * Mi Fitness health-API request crypto: RC4-drop[1024] with a SHA256-derived key and SHA1 request
 * signatures, as the Mi Fitness app (and huami-token) implement it. Base64 is standard RFC 4648;
 * a self-contained codec is used so this works on API 23 (java.util.Base64 is API 26) and stays
 * unit-testable on the plain JVM. Kept internal so the module's tests can exercise it directly.
 */
internal object XiaomiCrypto {

    private const val B64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val B64_INVERSE = IntArray(128) { -1 }.also {
        for (i in B64_ALPHABET.indices) it[B64_ALPHABET[i].code] = i
    }

    fun base64Encode(data: ByteArray): String {
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i + 3 <= data.size) {
            val n = ((data[i].toInt() and 0xFF) shl 16) or
                ((data[i + 1].toInt() and 0xFF) shl 8) or
                (data[i + 2].toInt() and 0xFF)
            sb.append(B64_ALPHABET[(n shr 18) and 63])
            sb.append(B64_ALPHABET[(n shr 12) and 63])
            sb.append(B64_ALPHABET[(n shr 6) and 63])
            sb.append(B64_ALPHABET[n and 63])
            i += 3
        }
        when (data.size - i) {
            1 -> {
                val n = (data[i].toInt() and 0xFF) shl 16
                sb.append(B64_ALPHABET[(n shr 18) and 63])
                sb.append(B64_ALPHABET[(n shr 12) and 63])
                sb.append("==")
            }
            2 -> {
                val n = ((data[i].toInt() and 0xFF) shl 16) or ((data[i + 1].toInt() and 0xFF) shl 8)
                sb.append(B64_ALPHABET[(n shr 18) and 63])
                sb.append(B64_ALPHABET[(n shr 12) and 63])
                sb.append(B64_ALPHABET[(n shr 6) and 63])
                sb.append('=')
            }
        }
        return sb.toString()
    }

    fun base64Decode(src: String): ByteArray {
        val out = ByteArrayOutputStream(src.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in src) {
            val v = if (c.code < 128) B64_INVERSE[c.code] else -1
            if (v < 0) continue // skips padding '=' and whitespace
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    fun sha1(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(data)
    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    /** base64(random8 || int32_be((now_ms) / 60000)). */
    fun generateNonce(): String {
        val random = ByteArray(8)
        SecureRandom().nextBytes(random)
        val minutes = (System.currentTimeMillis() / 60000L).toInt()
        val time = byteArrayOf(
            (minutes shr 24).toByte(), (minutes shr 16).toByte(),
            (minutes shr 8).toByte(), minutes.toByte(),
        )
        return base64Encode(random + time)
    }

    /** base64(SHA256(b64decode(ssecurity) || b64decode(nonce))). */
    fun deriveRc4Key(ssecurityB64: String, nonceB64: String): String =
        base64Encode(sha256(base64Decode(ssecurityB64) + base64Decode(nonceB64)))

    /** base64(SHA1("METHOD&path&k1=v1&...&rc4Key")), params in sorted key order. */
    fun sha1Sign(method: String, path: String, params: Map<String, String>, rc4KeyB64: String): String {
        val parts = ArrayList<String>()
        if (method.isNotEmpty()) parts.add(method.uppercase())
        if (path.isNotEmpty()) parts.add(path)
        for (k in params.keys.sorted()) parts.add("$k=${params[k]}")
        parts.add(rc4KeyB64)
        return base64Encode(sha1(parts.joinToString("&").toByteArray(Charsets.UTF_8)))
    }

    /**
     * Encrypt request params (i42.c mode): add rc4_hash__, RC4-encrypt every value with one
     * continuous stream in sorted key order, then append the signature and the nonce.
     */
    fun encryptParams(
        method: String,
        signingPath: String,
        params: Map<String, String>,
        nonceB64: String,
        ssecurityB64: String,
    ): LinkedHashMap<String, String> {
        val rc4Key = deriveRc4Key(ssecurityB64, nonceB64)
        val plain = TreeMap(params) // sorted
        plain["rc4_hash__"] = sha1Sign(method, signingPath, plain, rc4Key)

        val rc4 = Rc4(base64Decode(rc4Key)).apply { drop(1024) }
        val encrypted = TreeMap<String, String>()
        for ((k, v) in plain) {
            encrypted[k] = base64Encode(rc4.crypt(v.toByteArray(Charsets.UTF_8)))
        }
        val signature = sha1Sign(method, signingPath, encrypted, rc4Key)

        val out = LinkedHashMap<String, String>(encrypted)
        out["signature"] = signature
        out["_nonce"] = nonceB64
        return out
    }

    /** Decrypt a base64, RC4-drop[1024] encrypted response body to UTF-8 text. */
    fun decryptResponse(bodyB64: String, nonceB64: String, ssecurityB64: String): String {
        val rc4 = Rc4(base64Decode(deriveRc4Key(ssecurityB64, nonceB64))).apply { drop(1024) }
        return String(rc4.crypt(base64Decode(bodyB64)), Charsets.UTF_8)
    }

    /** Plain RC4 stream cipher. */
    class Rc4(key: ByteArray) {
        private val s = IntArray(256) { it }
        private var i = 0
        private var j = 0

        init {
            require(key.isNotEmpty()) { "RC4 key must not be empty" }
            var k = 0
            for (idx in 0..255) {
                k = (k + s[idx] + (key[idx % key.size].toInt() and 0xFF)) and 0xFF
                val t = s[idx]; s[idx] = s[k]; s[k] = t
            }
        }

        fun crypt(data: ByteArray): ByteArray {
            val out = ByteArray(data.size)
            for (idx in data.indices) {
                i = (i + 1) and 0xFF
                j = (j + s[i]) and 0xFF
                val t = s[i]; s[i] = s[j]; s[j] = t
                out[idx] = (data[idx].toInt() xor s[(s[i] + s[j]) and 0xFF]).toByte()
            }
            return out
        }

        /** Discard [n] bytes of keystream. */
        fun drop(n: Int) {
            crypt(ByteArray(n))
        }
    }
}
