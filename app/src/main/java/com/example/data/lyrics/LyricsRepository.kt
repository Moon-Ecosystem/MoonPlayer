package com.example.data.lyrics

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.example.data.db.LyricsDao
import com.example.data.db.LyricsEntity
import com.example.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Where a song's lyrics came from, shown under the lyrics. */
enum class LyricsSource(val label: String) { LRC_FILE("Local .lrc file"), LRCLIB("LRCLIB") }

sealed interface LyricsLookup {
    /**
     * [kind] is the version shown (null for a local .lrc or plain text); [available] lists the
     * kinds this song has, for the Original / Romaji / Translation switch.
     */
    data class Found(
        val lyrics: Lyrics,
        val source: LyricsSource,
        val kind: LyricsKind? = null,
        val available: Set<LyricsKind> = emptySet()
    ) : LyricsLookup
    data object NotFound : LyricsLookup
    /**
     * LRCLIB couldn't answer: [busy] when its servers refused (overloaded), otherwise no
     * connection. Nothing is cached, so the next open tries again.
     */
    data class Unavailable(val busy: Boolean) : LyricsLookup
}

/** LRCLIB answered "busy" (503/429) even after a retry. */
private class LrclibBusyException : IOException("LRCLIB is busy")

/**
 * Finds lyrics for a track: a same-named .lrc beside the file first, then a saved earlier
 * result, then (only when [online]) LRCLIB. Online results, including "none", are saved so
 * each song is looked up once.
 */
class LyricsRepository(private val context: Context, private val dao: LyricsDao) {

    /** [preferRomaji]: show romaji over the original script when the song has both. */
    suspend fun find(track: Track, online: Boolean, preferRomaji: Boolean): LyricsLookup = withContext(Dispatchers.IO) {
        readSiblingLrc(track)?.let { text ->
            toLyrics(text)?.let { return@withContext LyricsLookup.Found(it, LyricsSource.LRC_FILE) }
        }
        val saved = dao.get(track.id) ?: run {
            if (!online) return@withContext LyricsLookup.NotFound
            val fetched = try {
                fetchFromLrclib(track)
            } catch (e: IOException) {
                Log.w(TAG, "LRCLIB lookup failed: ${e.message}")
                return@withContext LyricsLookup.Unavailable(busy = e is LrclibBusyException)
            }
            LyricsEntity(
                trackId = track.id,
                originalLrc = fetched.versions[LyricsKind.ORIGINAL],
                romajiLrc = fetched.versions[LyricsKind.ROMAJI],
                translationLrc = fetched.versions[LyricsKind.TRANSLATION],
                plain = fetched.plain,
                chosenKind = null,
                source = LyricsSource.LRCLIB.name,
                fetchedAt = System.currentTimeMillis()
            ).also { dao.put(it) }
        }
        fromSaved(saved, preferRomaji)
    }

    /** Remembers which version to show for this song. */
    suspend fun choose(trackId: String, kind: LyricsKind) = withContext(Dispatchers.IO) {
        dao.setChosenKind(trackId, kind.name)
    }

    /** Forgets a saved result so the next [find] looks the song up again. */
    suspend fun forget(trackId: String) = withContext(Dispatchers.IO) { dao.delete(trackId) }

    private fun fromSaved(saved: LyricsEntity, preferRomaji: Boolean): LyricsLookup {
        val versions = mapOf(
            LyricsKind.ORIGINAL to saved.originalLrc,
            LyricsKind.ROMAJI to saved.romajiLrc,
            LyricsKind.TRANSLATION to saved.translationLrc
        ).mapNotNull { (kind, lrc) -> lrc?.let(::parseLrc)?.let { kind to Lyrics.Synced(it) } }.toMap()
        val chosen = saved.chosenKind?.let { runCatching { LyricsKind.valueOf(it) }.getOrNull() }
        val kind = chooseLyricsKind(versions.keys, chosen, preferRomaji)
        if (kind != null) return LyricsLookup.Found(versions.getValue(kind), LyricsSource.LRCLIB, kind, versions.keys)
        return saved.plain?.let(::toLyrics)?.let { LyricsLookup.Found(it, LyricsSource.LRCLIB) } ?: LyricsLookup.NotFound
    }

    /** Synced when the text has timestamps, else plain; null for blank text. */
    private fun toLyrics(text: String): Lyrics? =
        parseLrc(text)?.let { Lyrics.Synced(it) } ?: text.takeIf { it.isNotBlank() }?.let { Lyrics.Plain(it.trim()) }

    private fun readSiblingLrc(track: Track): String? = try {
        val audio = Uri.parse(track.contentUri)
        val lrcId = siblingLrcDocumentId(DocumentsContract.getDocumentId(audio)) ?: return null
        val tree = DocumentsContract.buildTreeDocumentUri(audio.authority, DocumentsContract.getTreeDocumentId(audio))
        val lrcUri = DocumentsContract.buildDocumentUriUsingTree(tree, lrcId)
        context.contentResolver.openInputStream(lrcUri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: Exception) {
        // No .lrc there, a non-tree URI, or no access: just means "no local lyrics".
        null
    }

    private class Fetched(val versions: Map<LyricsKind, String>, val plain: String?)

    /**
     * Every synced version LRCLIB has for the song (by kind) plus plain text; empty when it has
     * nothing. Throws IOException only when it found nothing and some request failed, so a busy
     * server is never mistaken for (and saved as) "no lyrics".
     */
    private suspend fun fetchFromLrclib(track: Track): Fetched {
        val durationSec = track.durationMs / 1000.0
        val records = mutableListOf<LrclibRecord>()
        var failure: IOException? = null

        suspend fun collect(pathAndQuery: String, single: Boolean) {
            try {
                getJson(pathAndQuery)?.let { records += if (single) listOf(toRecord(JSONObject(it))) else parseArray(it) }
            } catch (e: IOException) {
                failure = e
            } catch (e: org.json.JSONException) {
                failure = IOException("Bad LRCLIB reply", e)
            }
        }
        fun versions() = pickLyricsVersions(records, durationSec, track.title)

        // Search first: it returns every version, which usually includes a synced one.
        collect("/api/search?" + query("track_name" to track.title, "artist_name" to track.artist), single = false)
        // Only ask more of a busy server when that wasn't enough (nothing synced, or a
        // non-Latin song without romaji): the exact match, and an "artist title" search that
        // also catches romaji entries filed under English titles.
        val first = versions()
        val nonLatinSong = (track.title.any { it.isLetter() } && latinShare(track.title) < 0.5) || first.keys.any { it != LyricsKind.ORIGINAL } ||
            first[LyricsKind.ORIGINAL]?.let { latinShare(it) < 0.5 } == true
        if (first.isEmpty() || (nonLatinSong && LyricsKind.ROMAJI !in first)) {
            collect(
                "/api/get?" + query(
                    "track_name" to track.title,
                    "artist_name" to track.artist,
                    "album_name" to track.album,
                    "duration" to Math.round(durationSec).toString()
                ),
                single = true
            )
            collect("/api/search?" + query("q" to "${track.artist} ${track.title}"), single = false)
        }

        val versions = versions()
        val plain = records.firstOrNull { !it.plainLyrics.isNullOrBlank() }?.plainLyrics
        if (versions.isEmpty() && plain == null) failure?.let { throw it }
        return Fetched(versions, plain)
    }

    private fun parseArray(json: String): List<LrclibRecord> {
        val array = JSONArray(json)
        return (0 until array.length()).map { toRecord(array.getJSONObject(it)) }
    }

    private fun toRecord(o: JSONObject) = LrclibRecord(
        syncedLyrics = o.optString("syncedLyrics").takeIf { !o.isNull("syncedLyrics") && it.isNotBlank() },
        plainLyrics = o.optString("plainLyrics").takeIf { !o.isNull("plainLyrics") && it.isNotBlank() },
        durationSec = o.optDouble("duration", 0.0)
    )

    private fun query(vararg params: Pair<String, String>) =
        params.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

    /**
     * Response body, or null for 404 ("no lyrics"). A busy answer (503/429) is retried once
     * after a pause, then throws [LrclibBusyException]; other failures throw IOException.
     */
    private suspend fun getJson(pathAndQuery: String): String? {
        repeat(2) { attempt ->
            val connection = URL("https://lrclib.net$pathAndQuery").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                // LRCLIB asks clients to identify themselves.
                connection.setRequestProperty("User-Agent", "MoonPlayer (https://github.com/Varun-666/MoonPlayer)")
                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    HttpURLConnection.HTTP_NOT_FOUND -> return null
                    HttpURLConnection.HTTP_UNAVAILABLE, 429 -> if (attempt == 1) throw LrclibBusyException()
                    else -> throw IOException("LRCLIB HTTP $code")
                }
            } finally {
                connection.disconnect()
            }
            delay(1_500)
        }
        throw LrclibBusyException()
    }

    private companion object {
        const val TAG = "LyricsRepository"
    }
}
