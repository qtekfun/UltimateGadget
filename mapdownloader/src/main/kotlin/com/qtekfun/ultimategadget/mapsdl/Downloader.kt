package com.qtekfun.ultimategadget.mapsdl

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** One downloadable base map (PMTiles render asset of a region in the ultimate-maps-data catalog). */
data class MapRegion(
    val id: String,
    val name: String,
    val parent: String,
    val url: String,
    val size: Long,
    val sha256: String,
    val file: String,
)

/** Default catalog of the ultimate-maps-data releases. The app only ever talks to these hosts. */
const val CATALOG_URL =
    "https://github.com/qtekfun/ultimate-maps-data/releases/latest/download/catalog.json"

object Downloader {

    private const val MAX_HOPS = 5

    /** Fetch and parse the catalog, returning only regions that ship a PMTiles render asset. */
    fun fetchCatalog(): List<MapRegion> {
        val body = get(CATALOG_URL).toString(Charsets.UTF_8)
        val root = JSONObject(body)
        val regions = root.optJSONArray("regions") ?: return emptyList()
        val out = ArrayList<MapRegion>()
        for (i in 0 until regions.length()) {
            val r = regions.optJSONObject(i) ?: continue
            val assets = r.optJSONObject("assets") ?: continue
            val render = assets.optJSONObject("render") ?: continue
            val url = render.optString("url").ifBlank { continue }
            out += MapRegion(
                id = r.optString("id"),
                name = r.optString("name").ifBlank { r.optString("id") },
                parent = r.optString("parent"),
                url = url,
                size = render.optLong("size"),
                sha256 = render.optString("sha256").lowercase(),
                file = render.optString("file").ifBlank { r.optString("id") + ".pmtiles" },
            )
        }
        return out.sortedBy { it.name }
    }

    /**
     * Download [region] into [folder] (a SAF tree the user picked), verifying its SHA-256.
     * Reports bytes downloaded through [onProgress]. Throws on network or checksum failure.
     */
    fun download(
        context: Context,
        region: MapRegion,
        folder: Uri,
        onProgress: (done: Long, total: Long) -> Unit,
    ) {
        val tree = DocumentFile.fromTreeUri(context, folder)
            ?: throw IllegalStateException("Carpeta no accesible")
        tree.findFile(region.file)?.delete()
        val doc = tree.createFile("application/octet-stream", region.file)
            ?: throw IllegalStateException("No se pudo crear el fichero")

        val digest = MessageDigest.getInstance("SHA-256")
        var conn = open(region.url)
        var done = 0L
        try {
            context.contentResolver.openOutputStream(doc.uri).use { out ->
                requireNotNull(out) { "No se pudo escribir" }
                conn.inputStream.use { input ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        onProgress(done, region.size)
                    }
                }
            }
        } catch (e: Exception) {
            doc.delete()
            throw e
        } finally {
            conn.disconnect()
        }

        if (region.sha256.isNotEmpty()) {
            val got = digest.digest().joinToString("") { "%02x".format(it) }
            if (!got.equals(region.sha256, ignoreCase = true)) {
                doc.delete()
                throw IllegalStateException("La verificación SHA-256 falló")
            }
        }
    }

    private fun get(url: String): ByteArray {
        val conn = open(url)
        try {
            return conn.inputStream.readBytes()
        } finally {
            conn.disconnect()
        }
    }

    /** Open [url], following up to [MAX_HOPS] HTTPS redirects (GitHub release-asset hops). */
    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(MAX_HOPS) {
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20000
                readTimeout = 30000
                setRequestProperty("User-Agent", "UltimateGadget-MapsDownloader")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                requireNotNull(loc) { "Redirección sin destino" }
                current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                require(current.startsWith("https://")) { "Redirección no segura" }
                return@repeat
            }
            if (code !in 200..299) {
                c.disconnect()
                throw IllegalStateException("HTTP $code")
            }
            return c
        }
        throw IllegalStateException("Demasiadas redirecciones")
    }
}
