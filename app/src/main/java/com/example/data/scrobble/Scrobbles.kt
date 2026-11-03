package com.example.data.scrobble

import android.content.Context
import com.example.BuildConfig
import com.example.data.stats.StatsRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Where Pano Scrobbler (or any scrobbler) sends plays. Both speak the Last.fm 2.0 API, so one
 * client reads either; Moon Player only reads public profile data and never scrobbles.
 */
enum class ScrobbleService(val label: String, val apiUrl: String, val webUrl: String) {
    LASTFM("Last.fm", "https://ws.audioscrobbler.com/2.0/", "https://www.last.fm"),
    LIBREFM("Libre.fm", "https://libre.fm/2.0/", "https://libre.fm");

    // Libre.fm doesn't check keys; it only wants 32 characters.
    val apiKey: String get() = if (this == LASTFM) BuildConfig.LASTFM_API_KEY else "moonplayer".padEnd(32, '0')

    fun profileUrl(user: String) = "$webUrl/user/${URLEncoder.encode(user, "UTF-8")}"
}

/** An artist, album or track in a top list; [url] is its page on the service. */
data class TopItem(val name: String, val detail: String, val plays: Int, val url: String)

/** One scrobble; [at] (ms) is null for the track being scrobbled right now. */
data class Scrobble(val title: String, val artist: String, val at: Long?, val imageUrl: String?, val url: String)

data class ScrobbleStats(
    /** Scrobbles in the chosen range. */
    val scrobbles: Int,
    /** Different artists in the chosen range. */
    val artists: Int,
    val totalScrobbles: Int,
    /** When the account was made (ms), if the service says. */
    val since: Long?,
    val topArtists: List<TopItem>,
    val topAlbums: List<TopItem>,
    val topTracks: List<TopItem>,
    val recent: List<Scrobble>,
    val fetchedAt: Long,
    /** Loaded from the saved copy because the service couldn't be reached. */
    val offline: Boolean = false
)

class ScrobbleException(val reason: Reason, message: String? = null) : Exception(message) {
    enum class Reason { NO_USER, PRIVATE, OFFLINE, NO_KEY, FAILED }
}

/** Reads a user's scrobble stats and keeps the last copy per range, for offline and instant opens. */
class ScrobbleRepository(context: Context) {
    private val cacheDir = File(context.filesDir, "scrobbles")

    /** The saved copy for this account and range, or null. */
    suspend fun cached(service: ScrobbleService, user: String, range: StatsRange): ScrobbleStats? =
        withContext(Dispatchers.IO) {
            val file = cacheFile(service, user, range)
            if (!file.exists()) return@withContext null
            runCatching { parseScrobbleStats(JSONObject(file.readText()), file.lastModified()) }.getOrNull()
        }

    /** Fetches fresh stats; throws [ScrobbleException]. Falls back to nothing: callers use [cached]. */
    suspend fun fetch(service: ScrobbleService, user: String, range: StatsRange): ScrobbleStats =
        withContext(Dispatchers.IO) {
            if (service.apiKey.isBlank()) throw ScrobbleException(ScrobbleException.Reason.NO_KEY)
            val period = when (range) {
                StatsRange.WEEK -> "7day"
                StatsRange.MONTH -> "1month"
                StatsRange.YEAR -> "12month"
                StatsRange.ALL -> "overall"
            }
            val calls = buildMap {
                put("info", listOf("method" to "user.getinfo"))
                put("artists", listOf("method" to "user.gettopartists", "period" to period, "limit" to "50"))
                put("albums", listOf("method" to "user.gettopalbums", "period" to period, "limit" to "50"))
                put("tracks", listOf("method" to "user.gettoptracks", "period" to period, "limit" to "50"))
                put("recent", listOf("method" to "user.getrecenttracks", "limit" to "50"))
                // The top lists don't say how many scrobbles a range holds; recent tracks since then does.
                if (range.days != null) {
                    val from = range.since(System.currentTimeMillis()) / 1000
                    put("count", listOf("method" to "user.getrecenttracks", "from" to "$from", "limit" to "1"))
                }
            }
            val combined = JSONObject()
            coroutineScope {
                calls.map { (name, params) ->
                    async { name to get(service, params + ("user" to user)) }
                }.awaitAll()
            }.forEach { (name, json) -> combined.put(name, json) }

            val file = cacheFile(service, user, range)
            file.parentFile?.mkdirs()
            file.writeText(combined.toString())
            parseScrobbleStats(combined, System.currentTimeMillis())
        }

    /** Forgets every saved copy (on disconnect). */
    suspend fun clear() = withContext(Dispatchers.IO) { cacheDir.deleteRecursively() }

    private fun cacheFile(service: ScrobbleService, user: String, range: StatsRange) =
        File(cacheDir, "${service.name}-${user.lowercase().replace(Regex("[^a-z0-9_-]"), "_")}-${range.name}.json")

    private fun get(service: ScrobbleService, params: List<Pair<String, String>>): JSONObject {
        val query = (params + ("api_key" to service.apiKey) + ("format" to "json"))
            .joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val connection = try {
            URL("${service.apiUrl}?$query").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw ScrobbleException(ScrobbleException.Reason.OFFLINE, e.message)
        }
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "MoonPlayer (https://github.com/Moon-Ecosystem/MoonPlayer)")
            val code = try {
                connection.responseCode
            } catch (e: IOException) {
                throw ScrobbleException(ScrobbleException.Reason.OFFLINE, e.message)
            }
            val body = (if (code < 400) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: throw ScrobbleException(ScrobbleException.Reason.FAILED, "${service.label} answered HTTP $code")
            if (json.has("error")) {
                val reason = when (json.optInt("error")) {
                    6 -> ScrobbleException.Reason.NO_USER
                    17 -> ScrobbleException.Reason.PRIVATE
                    10, 26 -> ScrobbleException.Reason.NO_KEY
                    else -> ScrobbleException.Reason.FAILED
                }
                throw ScrobbleException(reason, json.optString("message"))
            }
            return json
        } finally {
            connection.disconnect()
        }
    }
}

/** Turns the saved {info, artists, albums, tracks, recent, count?} answers into stats. */
internal fun parseScrobbleStats(json: JSONObject, fetchedAt: Long): ScrobbleStats {
    val user = json.optJSONObject("info")?.optJSONObject("user")
    val total = user?.optString("playcount")?.toIntOrNull() ?: 0
    val registered = user?.optJSONObject("registered")
    val since = (registered?.optString("unixtime")?.toLongOrNull() ?: registered?.optString("#text")?.toLongOrNull())
        ?.takeIf { it > 0 }?.times(1000)

    val artists = json.optJSONObject("artists")?.optJSONObject("topartists")
    val albums = json.optJSONObject("albums")?.optJSONObject("topalbums")
    val tracks = json.optJSONObject("tracks")?.optJSONObject("toptracks")
    val recent = json.optJSONObject("recent")?.optJSONObject("recenttracks")
    // No "count" means the whole history (ALL range).
    val inRange = json.optJSONObject("count")?.optJSONObject("recenttracks")?.let(::totalOf) ?: total

    fun top(list: JSONObject?, key: String, detail: (JSONObject) -> String) =
        objects(list, key).map { o ->
            TopItem(o.optString("name"), detail(o), o.optString("playcount").toIntOrNull() ?: 0, o.optString("url"))
        }

    return ScrobbleStats(
        scrobbles = inRange,
        artists = artists?.let(::totalOf) ?: 0,
        totalScrobbles = total,
        since = since,
        topArtists = top(artists, "artist") { "" },
        topAlbums = top(albums, "album") { nameOf(it.opt("artist")) },
        topTracks = top(tracks, "track") { nameOf(it.opt("artist")) },
        recent = objects(recent, "track").map { o ->
            val nowPlaying = o.optJSONObject("@attr")?.optString("nowplaying") == "true"
            Scrobble(
                title = o.optString("name"),
                artist = nameOf(o.opt("artist")),
                at = if (nowPlaying) null else o.optJSONObject("date")?.optString("uts")?.toLongOrNull()?.times(1000),
                imageUrl = imageOf(o),
                url = o.optString("url")
            )
        },
        fetchedAt = fetchedAt
    )
}

private fun totalOf(list: JSONObject): Int? = list.optJSONObject("@attr")?.optString("total")?.toIntOrNull()

// The API sends one item as an object, several as an array.
private fun objects(list: JSONObject?, key: String): List<JSONObject> = when (val v = list?.opt(key)) {
    is JSONArray -> (0 until v.length()).mapNotNull { v.optJSONObject(it) }
    is JSONObject -> listOf(v)
    else -> emptyList()
}

// An artist is {"name": ...} in top lists and {"#text": ...} in recent tracks.
private fun nameOf(artist: Any?): String = when (artist) {
    is JSONObject -> artist.optString("name").ifBlank { artist.optString("#text") }
    is String -> artist
    else -> ""
}

/** The largest cover the service gave, or null. */
private fun imageOf(o: JSONObject): String? {
    val images = o.optJSONArray("image") ?: return null
    return (images.length() - 1 downTo 0).firstNotNullOfOrNull { i ->
        images.optJSONObject(i)?.optString("#text")?.takeIf { it.isNotBlank() }
    }
}
