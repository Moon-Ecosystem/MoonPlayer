package com.example.data.lyrics

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.inspector.MetadataRetriever
import java.util.concurrent.TimeUnit
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

/** Where a song's lyrics come from: [label] for the source line, [long] for the sheet and Song info. */
enum class LyricsSource(val label: String, val long: String) {
    LRC_FILE(".lrc file", "The .lrc file beside the song"),
    EMBEDDED("In the song file", "Stored in the song file"),
    LRCLIB("LRCLIB", "LRCLIB, online")
}

/** One source that has the shown kind, for the source sheet. */
data class LyricsSourceOption(val source: LyricsSource, val synced: Boolean)

sealed interface LyricsLookup {
    /**
     * [kind] is the version on screen and [kinds] every kind the song has (the pills). [sources]
     * lists every source that has [kind], in the user's order; [source] is the one shown, the
     * automatic pick [autoSource] unless [picked] by hand. [lrclibAsked]: LRCLIB has answered for
     * this song (saved); [lrclibFailed]: a lookup asked for from the source sheet failed.
     */
    data class Found(
        val lyrics: Lyrics,
        val source: LyricsSource,
        val kind: LyricsKind,
        val kinds: List<LyricsKind>,
        val sources: List<LyricsSourceOption>,
        val autoSource: LyricsSource,
        val picked: Boolean,
        val lrclibAsked: Boolean,
        val lrclibFailed: Boolean = false
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
 * Finds a song's lyrics in up to three places: a same-named .lrc beside it, the song file's own
 * tags, and LRCLIB (a saved earlier answer, or a lookup when [online] or the user asks). For each kind one
 * source is shown: the one picked for the song, else synced-first (if preferred) then the user's
 * order. Online results, including "none", are saved so each song is looked up once.
 */
class LyricsRepository(private val context: Context, private val dao: LyricsDao) {

    /**
     * [preferRomaji]: open on romaji when the song has it. [order] and [preferSynced] are the
     * Settings › Lyrics source preferences. [forceLrclib]: ask LRCLIB now although online lyrics
     * is off (the sheet's "Look up on LRCLIB").
     */
    suspend fun find(
        track: Track,
        online: Boolean,
        preferRomaji: Boolean,
        order: List<LyricsSource> = DEFAULT_LYRICS_SOURCE_ORDER,
        preferSynced: Boolean = true,
        forceLrclib: Boolean = false
    ): LyricsLookup = withContext(Dispatchers.IO) {
        // kind -> source -> lyrics
        val byKind = sortedMapOf<LyricsKind, MutableMap<LyricsSource, Lyrics>>()
        fun add(kind: LyricsKind, source: LyricsSource, lyrics: Lyrics) { byKind.getOrPut(kind) { mutableMapOf() }[source] = lyrics }

        readSiblingLrc(track)?.let(::lyricsFromText)?.let { lrc ->
            add(lyricsKinds(listOf(plainText(lrc)), track.title).first(), LyricsSource.LRC_FILE, lrc)
        }
        embeddedVersions(track).forEach { (kind, lyrics) -> add(kind, LyricsSource.EMBEDDED, lyrics) }

        val row = dao.get(track.id)
        var unavailable: LyricsLookup.Unavailable? = null
        // A row that only holds a choice isn't an LRCLIB answer. Every song is asked once (when
        // online): one lookup brings all of LRCLIB's versions, and whether it has a romaji or
        // translation the song lacks can't be known without asking. The answer is saved.
        val wantLrclib = online
        val savedLrclib = row?.takeIf { it.source == LyricsSource.LRCLIB.name }
        val lrclib = savedLrclib ?: if (forceLrclib || wantLrclib) {
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
        lrclib?.let { saved -> lrclibVersions(saved).forEach { (kind, lyrics) -> add(kind, LyricsSource.LRCLIB, lyrics) } }

        val choice = parseChoice(row?.chosenKind)
        val kind = chooseLyricsKind(byKind.keys, choice.kind, preferRomaji)
            ?: return@withContext unavailable ?: LyricsLookup.NotFound
        val candidates = byKind.getValue(kind)
        val auto = autoSource(candidates, order, preferSynced)!!
        val picked = choice.sources[kind]?.takeIf { it in candidates }
        val source = picked ?: auto
        fun rank(s: LyricsSource) = order.indexOf(s).takeIf { it >= 0 } ?: (order.size + s.ordinal)
        LyricsLookup.Found(
            lyrics = candidates.getValue(source),
            source = source,
            kind = kind,
            kinds = byKind.keys.toList(),
            sources = candidates.entries.sortedBy { rank(it.key) }.map { LyricsSourceOption(it.key, it.value is Lyrics.Synced) },
            autoSource = auto,
            picked = picked != null,
            lrclibAsked = lrclib != null,
            lrclibFailed = forceLrclib && unavailable != null
        )
    }

    private fun plainText(l: Lyrics) = when (l) {
        is Lyrics.Synced -> l.lines.joinToString("\n") { it.text }
        is Lyrics.Plain -> l.text
    }

    /**
     * Song info rows: what lyrics the song has in each place, synced or not, and which one the
     * lyrics view shows. Never goes online; a song LRCLIB hasn't been asked about says so.
     */
    suspend fun describe(
        track: Track,
        onlineLyrics: Boolean?,
        lrclibEnabled: Boolean,
        preferRomaji: Boolean,
        order: List<LyricsSource>,
        preferSynced: Boolean
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        fun sync(l: Lyrics) = if (l is Lyrics.Synced) "synced" else "not synced"
        val lookup = find(track, online = false, preferRomaji, order, preferSynced) as? LyricsLookup.Found

        val lrc = readSiblingLrc(track)?.let(::lyricsFromText)
        val embedded = embeddedVersions(track)
        val saved = dao.get(track.id)?.takeIf { it.source == LyricsSource.LRCLIB.name }
        listOf(
            ".lrc file" to (lrc?.let { "Yes, ${sync(it)}" } ?: "None"),
            "In the file" to (embedded.takeIf { it.isNotEmpty() }?.joinToString(", ") { (kind, l) -> "${kind.label} (${sync(l)})" } ?: "None"),
            "LRCLIB" to when {
                saved != null -> lrclibVersions(saved).takeIf { it.isNotEmpty() }
                    ?.entries?.joinToString(", ") { (kind, l) -> "${kind.label} (${sync(l)})" } ?: "Has none"
                !lrclibEnabled -> "Off in Settings"
                onlineLyrics != true -> "Not looked up · automatic lookup off"
                else -> "Not looked up yet"
            },
            "In use" to (lookup?.let {
                "${it.kind.label} from ${it.source.label}, ${sync(it.lyrics)} · ${if (it.picked) "picked for this song" else "automatic"}"
            } ?: "None")
        )
    }

    /** Remembers the kind this song opens on. */
    suspend fun chooseKind(trackId: String, kind: LyricsKind) = updateChoice(trackId) { it.copy(kind = kind) }

    /** Remembers [source] for this song's [kind]; null goes back to the automatic pick. */
    suspend fun chooseSource(trackId: String, kind: LyricsKind, source: LyricsSource?) = updateChoice(trackId) {
        it.copy(sources = if (source == null) it.sources - kind else it.sources + (kind to source))
    }

    /** Saves a changed choice, even before (or without) an LRCLIB lookup. */
    private suspend fun updateChoice(trackId: String, change: (LyricsChoice) -> LyricsChoice) = withContext(Dispatchers.IO) {
        val row = dao.get(trackId)
        val choice = encodeChoice(change(parseChoice(row?.chosenKind)))
        if (row != null) {
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

    /**
     * The lyrics tags in the audio file, one per kind (synced beats plain), in kind order: an ID3
     * tag at the start (MP3, also some AAC), else the tags Media3 reads for M4A, FLAC, Ogg and Opus.
     */
    private fun embeddedVersions(track: Track): List<Pair<LyricsKind, Lyrics>> {
        val uri = Uri.parse(track.contentUri)
        val tags = try {
            context.contentResolver.openInputStream(uri)?.use { readId3Lyrics(it.buffered()) }.orEmpty()
                .ifEmpty { if (track.mimeType == "audio/mpeg") emptyList() else readTagLyrics(uri) }
        } catch (e: Exception) {
            // Unreadable file or lost access: the song simply has no lyrics of its own.
            Log.w(TAG, "Couldn't read lyrics tags (${e.javaClass.simpleName})")
            emptyList()
        }
        return sortEmbedded(tags, track.title)
    }

    // shortcut: WAV isn't covered; Media3 skips its "id3 " chunk. Read that chunk with readId3Lyrics if WAVs with lyrics turn up.
    @OptIn(UnstableApi::class)
    private fun readTagLyrics(uri: Uri): List<EmbeddedLyrics> {
        val groups = MetadataRetriever.Builder(context, MediaItem.fromUri(uri)).build()
            .use { it.retrieveTrackGroups().get(10, TimeUnit.SECONDS) }
        val entries = (0 until groups.length).flatMap { g ->
            val metadata = groups[g].getFormat(0).metadata ?: return@flatMap emptyList()
            (0 until metadata.length()).map { metadata[it] }
        }
        return lyricsFromMetadata(entries)
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
