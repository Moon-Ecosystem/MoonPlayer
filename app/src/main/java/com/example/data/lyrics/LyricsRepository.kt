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
enum class LyricsSource(val label: String) { LRC_FILE("Local .lrc file"), EMBEDDED("In the file"), LRCLIB("LRCLIB") }

sealed interface LyricsLookup {
    /**
     * [shown] is the version on screen (null for a local .lrc); [available] lists every version
     * offered, the file's first within a kind, for the switch. [fileToggle] is set when this song
     * can show (or hide again) file versions that LRCLIB's synced ones hide.
     */
    data class Found(
        val lyrics: Lyrics,
        val source: LyricsSource,
        val shown: LyricsVersion? = null,
        val available: List<LyricsVersion> = emptyList(),
        val fileToggle: FileLyricsToggle? = null
    ) : LyricsLookup
    data object NotFound : LyricsLookup
    /**
     * LRCLIB couldn't answer: [busy] when its servers refused (overloaded), otherwise no
     * connection. Nothing is cached, so the next open tries again.
     */
    data class Unavailable(val busy: Boolean) : LyricsLookup
}

/** What the per-song pill at the end of the lyrics switch does. */
enum class FileLyricsToggle { SHOW, HIDE }

/** LRCLIB answered "busy" (503/429) even after a retry. */
private class LrclibBusyException : IOException("LRCLIB is busy")

/**
 * Finds lyrics for a track: a same-named .lrc beside the file wins outright. Otherwise the
 * versions in the file's own tags and LRCLIB's (a saved earlier result, or a lookup when
 * [online] and the file has no synced lyrics) are offered together, the file's first. Online
 * results, including "none", are saved so each song is looked up once.
 */
class LyricsRepository(private val context: Context, private val dao: LyricsDao) {

    /**
     * [preferRomaji]: show romaji over the original script when the song has both.
     * [keepFileLyrics] (for every song) or [keepFileLyricsForSong]: offer the file's plain
     * versions even where LRCLIB has them synced.
     */
    suspend fun find(
        track: Track,
        online: Boolean,
        preferRomaji: Boolean,
        keepFileLyrics: Boolean = false,
        keepFileLyricsForSong: Boolean = false
    ): LyricsLookup = withContext(Dispatchers.IO) {
        readSiblingLrc(track)?.let { text ->
            lyricsFromText(text)?.let { return@withContext LyricsLookup.Found(it, LyricsSource.LRC_FILE) }
        }
        val versions = LinkedHashMap<LyricsVersion, Lyrics>()
        embeddedVersions(track).forEach { (kind, lyrics) -> versions[LyricsVersion(kind, LyricsSource.EMBEDDED)] = lyrics }

        val row = dao.get(track.id)
        var unavailable: LyricsLookup.Unavailable? = null
        // A row that only holds a version choice isn't an LRCLIB answer. Synced lyrics in the file
        // already follow the song, so LRCLIB is only asked when the file has none.
        val fileSynced = versions.values.any { it is Lyrics.Synced }
        val lrclib = row?.takeIf { it.source == LyricsSource.LRCLIB.name } ?: if (online && !fileSynced) {
            try {
                val fetched = fetchFromLrclib(track)
                LyricsEntity(
                    trackId = track.id,
                    originalLrc = fetched.versions[LyricsKind.ORIGINAL],
                    romajiLrc = fetched.versions[LyricsKind.ROMAJI],
                    translationLrc = fetched.versions[LyricsKind.TRANSLATION],
                    plain = fetched.plain,
                    chosenKind = row?.chosenKind,
                    source = LyricsSource.LRCLIB.name,
                    fetchedAt = System.currentTimeMillis()
                ).also { dao.put(it) }
            } catch (e: IOException) {
                // With lyrics from the file there's still something to show; nothing is saved, so it's tried again next time.
                Log.w(TAG, "LRCLIB lookup failed: ${e.message}")
                unavailable = LyricsLookup.Unavailable(busy = e is LrclibBusyException)
                null
            }
        } else null
        lrclib?.let { saved -> lrclibVersions(saved).forEach { (kind, lyrics) -> versions[LyricsVersion(kind, LyricsSource.LRCLIB)] = lyrics } }

        val offered = withoutShadowedPlain(versions, keepAll = keepFileLyrics || keepFileLyricsForSong)
        // Only the per-song choice can be undone from the lyrics; the setting is changed in Settings.
        val fileToggle = when {
            keepFileLyrics -> null
            keepFileLyricsForSong -> FileLyricsToggle.HIDE.takeIf { withoutShadowedPlain(versions).size < versions.size }
            else -> FileLyricsToggle.SHOW.takeIf { offered.size < versions.size }
        }
        val shown = chooseVersion(offered.keys.toList(), parseChoice(row?.chosenKind), preferRomaji)
            ?: return@withContext unavailable ?: LyricsLookup.NotFound
        LyricsLookup.Found(offered.getValue(shown), shown.source, shown, offered.keys.toList(), fileToggle)
    }

    /**
     * Song info rows: what lyrics the song has in each place, synced or not, and which one the
     * lyrics view shows. Never goes online; a song LRCLIB hasn't been asked about says so.
     */
    suspend fun describe(
        track: Track,
        onlineLyrics: Boolean?,
        preferRomaji: Boolean,
        keepFileLyrics: Boolean,
        keepFileLyricsForSong: Boolean
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        fun sync(l: Lyrics) = if (l is Lyrics.Synced) "synced" else "not synced"
        val lookup = find(track, online = false, preferRomaji, keepFileLyrics, keepFileLyricsForSong) as? LyricsLookup.Found
        val offered = lookup?.available.orEmpty()

        val lrc = readSiblingLrc(track)?.let(::lyricsFromText)
        val embedded = embeddedVersions(track)
        val saved = dao.get(track.id)?.takeIf { it.source == LyricsSource.LRCLIB.name }
        listOf(
            ".lrc file" to (lrc?.let { "Yes, ${sync(it)}" } ?: "None"),
            "In the file" to (
                embedded.takeIf { it.isNotEmpty() }?.joinToString(", ") { (kind, l) ->
                    val hidden = lrc == null && LyricsVersion(kind, LyricsSource.EMBEDDED) !in offered
                    "${kind.label} (${sync(l)}${if (hidden) ", hidden by LRCLIB's synced" else ""})"
                } ?: "None"
            ),
            "LRCLIB" to when {
                saved != null -> lrclibVersions(saved).takeIf { it.isNotEmpty() }
                    ?.entries?.joinToString(", ") { (kind, l) -> "${kind.label} (${sync(l)})" } ?: "Has none"
                onlineLyrics != true -> "Not looked up · online lyrics off"
                else -> "Not looked up yet"
            },
            "In use" to when {
                lookup == null -> "None"
                lookup.shown == null -> "${lookup.source.label}, ${sync(lookup.lyrics)}"
                else -> "${lookup.shown.kind.label} from ${if (lookup.source == LyricsSource.EMBEDDED) "the file" else lookup.source.label}, ${sync(lookup.lyrics)}"
            }
        )
    }

    /** Remembers which version to show for this song, even before (or without) an LRCLIB lookup. */
    suspend fun choose(trackId: String, version: LyricsVersion) = withContext(Dispatchers.IO) {
        val choice = encodeChoice(version)
        if (dao.get(trackId) != null) {
            dao.setChosenKind(trackId, choice)
        } else {
            dao.put(LyricsEntity(trackId, null, null, null, null, choice, source = CHOICE_ONLY, fetchedAt = 0))
        }
    }

    /** Forgets a saved result so the next [find] looks the song up again. */
    suspend fun forget(trackId: String) = withContext(Dispatchers.IO) { dao.delete(trackId) }

    /** LRCLIB's synced versions by kind; its plain text stands in as the original when it has none. */
    private fun lrclibVersions(saved: LyricsEntity): Map<LyricsKind, Lyrics> {
        val synced = mapOf(
            LyricsKind.ORIGINAL to saved.originalLrc,
            LyricsKind.ROMAJI to saved.romajiLrc,
            LyricsKind.TRANSLATION to saved.translationLrc
        ).mapNotNull { (kind, lrc) -> lrc?.let(::parseLrc)?.let { kind to Lyrics.Synced(it) } }.toMap()
        if (synced.isNotEmpty()) return synced
        return saved.plain?.let(::lyricsFromText)?.let { mapOf(LyricsKind.ORIGINAL to it) } ?: emptyMap()
    }

    /** The lyrics tags in the audio file, one per kind (synced beats plain), in kind order. */
    private fun embeddedVersions(track: Track): List<Pair<LyricsKind, Lyrics>> {
        val tags = try {
            context.contentResolver.openInputStream(Uri.parse(track.contentUri))?.use { readId3Lyrics(it.buffered()) }.orEmpty()
        } catch (e: Exception) {
            // Unreadable file or lost access: the song simply has no lyrics of its own.
            Log.w(TAG, "Couldn't read lyrics tags (${e.javaClass.simpleName})")
            emptyList()
        }
        return sortEmbedded(tags, track.title)
    }

    private companion object {
        const val TAG = "LyricsRepository"
        /** A lyrics row saved only to remember the chosen version; LRCLIB hasn't been asked. */
        const val CHOICE_ONLY = "CHOICE"
    }

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
}
