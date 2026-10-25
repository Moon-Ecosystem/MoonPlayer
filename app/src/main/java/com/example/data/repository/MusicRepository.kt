package com.example.data.repository

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import java.io.File
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.example.data.db.FavoriteEntity
import com.example.data.db.HistoryEntity
import com.example.data.db.LibraryTrackEntity
import com.example.data.db.MoonPlayerDatabase
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistTrackEntity
import com.example.data.db.QueueEntity
import com.example.data.model.Track
import com.example.ui.theme.MoonWhite
import com.example.ui.theme.WarmIvory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class FolderScanResult(
    val folderName: String,
    val tracks: List<Track>,
    val error: String? = null
)

/**
 * Display name for a SAF tree URI without any I/O, e.g. ".../tree/primary%3AMusic%2FRock" -> "Rock".
 * The storage root itself ("primary:") has no folder segment, so it gets a generic name.
 */
fun folderNameFromTreeUri(treeUri: String): String {
    val docId = java.net.URLDecoder.decode(treeUri.substringAfter("/tree/").substringBefore('/'), "UTF-8")
    return docId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Internal storage" }
}

/**
 * Whether the track at [trackUri] lives inside the folder [treeUri], even when it was scanned
 * through a different (enclosing) folder. Path-style document IDs ("primary:Music/J-pop/a.mp3")
 * are compared as paths; providers with opaque IDs fall back to "scanned through this folder".
 */
fun isTrackInFolder(trackUri: String, treeUri: String): Boolean {
    if (trackUri.startsWith("$treeUri/document/")) return true
    val authority = { uri: String -> uri.substringAfter("://").substringBefore('/') }
    if (authority(trackUri) != authority(treeUri)) return false
    val decode = { s: String -> java.net.URLDecoder.decode(s, "UTF-8") }
    val folderId = decode(treeUri.substringAfter("/tree/").substringBefore('/'))
    val trackId = decode(trackUri.substringAfterLast("/document/"))
    // A storage root ("primary:") already ends in its separator.
    val prefix = if (folderId.endsWith(':')) folderId else "$folderId/"
    return trackId.startsWith(prefix)
}

// Formats ExoPlayer plays without extra decoders, keyed by the canonical MIME type we store.
private val AUDIO_EXTENSIONS = mapOf(
    "mp3" to "audio/mpeg",
    "flac" to "audio/flac",
    "m4a" to "audio/mp4",
    "aac" to "audio/aac",
    "ogg" to "audio/ogg",
    "oga" to "audio/ogg",
    "opus" to "audio/opus",
    "wav" to "audio/wav"
)

// Provider-reported aliases mapped onto the same canonical types.
private val AUDIO_MIME_ALIASES = mapOf(
    "audio/mpeg" to "audio/mpeg", "audio/mp3" to "audio/mpeg", "audio/x-mpeg" to "audio/mpeg",
    "audio/flac" to "audio/flac", "audio/x-flac" to "audio/flac",
    "audio/mp4" to "audio/mp4", "audio/x-m4a" to "audio/mp4", "audio/m4a" to "audio/mp4",
    "audio/aac" to "audio/aac", "audio/aac-adts" to "audio/aac",
    "audio/ogg" to "audio/ogg", "application/ogg" to "audio/ogg", "audio/vorbis" to "audio/ogg",
    "audio/opus" to "audio/opus",
    "audio/wav" to "audio/wav", "audio/x-wav" to "audio/wav", "audio/wave" to "audio/wav"
)

/**
 * Canonical MIME type if [name]/[mimeType] is a supported audio file, else null.
 * The extension wins: Android's own MIME table calls .m4a "audio/mpeg". The provider's MIME
 * covers files without a known extension.
 */
internal fun supportedAudioMime(name: String, mimeType: String): String? =
    AUDIO_EXTENSIONS[name.substringAfterLast('.', "").lowercase()]
        ?: AUDIO_MIME_ALIASES[mimeType.lowercase()]

/**
 * Picks the cover's most prominent colour from Palette swatches given as (rgb, population).
 *
 * Palette's dominantSwatch is just the largest single cluster, so a flat one-shade backdrop
 * beats a subject spread over many shades (e.g. a blue sky vs. reds and pinks). Instead, sum
 * colourful swatches into 60° hue families weighted by saturation, take the strongest family,
 * and return its strongest swatch. Null when the cover is essentially grey: no colourful
 * swatches, or so few that they're a detail (a character's hair on a black-and-white cover).
 */
internal fun accentFromSwatches(swatches: List<Pair<Int, Int>>): Int? {
    data class Scored(val rgb: Int, val family: Int, val weight: Float, val population: Int)

    val colourful = swatches.mapNotNull { (rgb, population) ->
        val r = (rgb shr 16 and 0xFF) / 255f
        val g = (rgb shr 8 and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val lightness = (max + min) / 2
        val chroma = max - min
        val saturation = if (chroma == 0f) 0f else chroma / (1 - kotlin.math.abs(2 * lightness - 1))
        // Skip greys and near-black/near-white swatches: they shouldn't drive the accent.
        if (saturation < 0.25f || lightness < 0.12f || lightness > 0.9f) return@mapNotNull null
        val hue = when (max) {
            r -> 60 * (((g - b) / chroma) % 6)
            g -> 60 * ((b - r) / chroma + 2)
            else -> 60 * ((r - g) / chroma + 4)
        }.let { if (it < 0) it + 360 else it }
        // Families centred on red (0°), yellow, green, cyan, blue, magenta; red wraps around 360°.
        val family = (((hue + 30) / 60).toInt()) % 6
        Scored(rgb, family, population * saturation, population)
    }
    // Under 5% colour reads as a black-and-white cover, not as that colour.
    if (colourful.sumOf { it.population } < 0.05 * swatches.sumOf { it.second }) return null
    val bestFamily = colourful.groupBy { it.family }
        .maxByOrNull { (_, members) -> members.sumOf { it.weight.toDouble() } }
        ?.value ?: return null
    return bestFamily.maxBy { it.weight }.rgb
}

/**
 * The playlists for the quick panel: the one playing, then the most recently played, then the
 * rest in their given order, limited to [limit]. Names no longer in [all] (deleted or renamed
 * playlists still in the play stats) are skipped.
 */
fun pickQuickPlaylists(all: List<String>, recentlyPlayed: List<String>, playing: String?, limit: Int = 4): List<String> =
    (listOfNotNull(playing) + recentlyPlayed + all).filter { it in all }.distinct().take(limit)

/** Short codec label for the player UI, e.g. "audio/flac" -> "FLAC". */
fun audioFormatLabel(mimeType: String): String = when (mimeType) {
    "audio/mpeg" -> "MP3"
    "audio/mp4" -> "M4A"
    else -> mimeType.substringAfter('/').uppercase()
}

/** Every playable format's label, e.g. "MP3 · FLAC · M4A …", for the About screen. */
val SUPPORTED_FORMAT_LABELS: String =
    AUDIO_EXTENSIONS.values.distinct().joinToString(" · ") { audioFormatLabel(it) }

/** (format label, track count) pairs, most common first; tracks without a known format are skipped. */
fun formatBreakdown(tracks: List<Track>): List<Pair<String, Int>> =
    tracks.filter { it.mimeType.isNotBlank() }
        .groupingBy { audioFormatLabel(it.mimeType) }
        .eachCount()
        .toList()
        .sortedByDescending { it.second }

/**
 * The folder's cover image among its image file names, e.g. "Cover.jpg" or "folder.png", for
 * songs without embedded artwork. Names earlier in the list win; other images are ignored.
 */
fun pickCoverFile(names: Collection<String>): String? {
    val covers = names.filter {
        it.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp")
    }
    return listOf("cover", "folder", "front", "album", "albumart").firstNotNullOfOrNull { base ->
        covers.firstOrNull { it.substringBeforeLast('.').equals(base, ignoreCase = true) }
    }
}

class MusicRepository(private val context: Context) {

    private companion object {
        const val TAG = "MusicRepository"
    }

    private val db = MoonPlayerDatabase.getInstance(context)
    private val playlistDao = db.playlistDao()
    private val favoriteDao = db.favoriteDao()
    private val historyDao = db.historyDao()
    private val queueDao = db.queueDao()
    private val trackStatDao = db.trackStatDao()
    private val libraryDao = db.libraryDao()

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())
    val allTracks = _allTracks.asStateFlow()

    private val accentColorCache = mutableMapOf<String, Color>()

    val allPlaylists = playlistDao.getAllPlaylists()
    val playlistSongCounts = playlistDao.getTrackCounts()
    val allPlaylistTracks = playlistDao.getAllPlaylistTracks()
    val favoriteTrackIds = favoriteDao.getAllFavoriteIds()
    val playHistory = historyDao.getRecentHistory()
    val savedQueue = queueDao.getSavedQueue()

    val recentPlaylistNames = trackStatDao.getRecentPlaylistNames()

    /** Every play since [since] (ms), oldest first; the listening stats are worked out from these. */
    fun historySince(since: Long) = historyDao.getHistorySince(since)

    val favoriteTracks: Flow<List<Track>> = combine(_allTracks, favoriteTrackIds) { tracks, favIds ->
        val favSet = favIds.toSet()
        tracks.filter { favSet.contains(it.id) }.map { it.copy(isFavorite = true) }
    }

    suspend fun toggleFavorite(trackId: String) {
        withContext(Dispatchers.IO) {
            if (favoriteDao.isFavorite(trackId)) {
                favoriteDao.removeFavorite(trackId)
            } else {
                favoriteDao.addFavorite(FavoriteEntity(trackId))
            }
        }
    }

    // Keeps a play's history row and the time later added to it in the order they were started;
    // callers launch both from the main thread, which takes the lock before the first suspension.
    private val historyLock = Mutex()

    /** [playlist] names the playlist the song was played from, if any, for playlist stats. */
    suspend fun recordHistory(track: Track, playlist: String? = null): Unit = historyLock.withLock {
        withContext(Dispatchers.IO) {
            historyDao.addHistory(
                HistoryEntity(
                    trackId = track.id,
                    playedAt = System.currentTimeMillis(),
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    uri = track.contentUri,
                    albumArtUri = track.albumArtUri,
                    playlist = playlist
                )
            )
            if (playlist != null) trackStatDao.recordPlaylistPlay(playlist)
        }
    }

    /** Wipes listening stats and play history; playlists, favourites and the library stay. */
    suspend fun resetStats() = withContext(Dispatchers.IO) {
        trackStatDao.clearPlaylistStats()
        historyDao.clearHistory()
    }

    /** Adds [ms] of actual playing time to the song's latest play in history. */
    suspend fun addListenTime(trackId: String, ms: Long) {
        if (ms <= 0) return
        historyLock.withLock { withContext(Dispatchers.IO) { historyDao.addListenTime(trackId, ms) } }
    }

    /**
     * Recursively scans only the persisted SAF tree rooted at [treeUri].
     * The traversal never leaves that tree and never falls back to MediaStore.
     * Files whose last-modified time matches a [known] track reuse its metadata instead of
     * being re-read, which is what makes rescans cheap.
     */
    suspend fun scanFolderUri(
        treeUri: Uri,
        known: Map<String, Track> = emptyMap()
    ): FolderScanResult = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        var folderName = "Music"

        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            folderName = queryDisplayName(treeUri, rootDocId) ?: folderName

            val pendingDirectories = ArrayDeque<String>().apply { add(rootDocId) }
            val visitedDirectories = mutableSetOf(rootDocId)

            while (pendingDirectories.isNotEmpty()) {
                val parentDocId = pendingDirectories.removeFirst()
                queryChildren(treeUri, parentDocId)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (idIdx < 0 || nameIdx < 0 || mimeIdx < 0) return@use
                    // A cover image applies to the songs beside it, and may be listed after them.
                    val dirTracks = mutableListOf<Track>()
                    val images = mutableMapOf<String, String>()

                    while (cursor.moveToNext()) {
                        val childDocId = cursor.getString(idIdx) ?: continue
                        val name = cursor.getString(nameIdx) ?: ""
                        val mime = cursor.getString(mimeIdx) ?: ""

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            // visited guard keeps traversal inside the tree even with odd providers
                            if (visitedDirectories.add(childDocId)) {
                                pendingDirectories.add(childDocId)
                            }
                        } else {
                            val audioMime = supportedAudioMime(name, mime)
                            if (audioMime != null) {
                                val lastModified = if (modIdx >= 0) cursor.getLong(modIdx) else 0L
                                // The type is re-derived for cached tracks too, so older scans pick up fixes.
                                // The address is too: it names the folder the file was reached through,
                                // and the cached one may go through a folder that's since been removed.
                                val cached = known[folderTrackId(childDocId)]
                                    ?.takeIf { lastModified != 0L && it.dateAdded == lastModified }
                                    ?.copy(
                                        mimeType = audioMime,
                                        contentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId).toString()
                                    )
                                dirTracks.add(
                                    cached ?: readFolderTrack(
                                        treeUri, childDocId, name, audioMime, lastModified, folderName
                                    )
                                )
                            } else if (mime.startsWith("image/")) {
                                images[name] = childDocId
                            }
                        }
                    }
                    val cover = pickCoverFile(images.keys)?.let {
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, images.getValue(it)).toString()
                    }
                    dirTracks.mapTo(tracks) { it.copy(albumArtUri = cover) }
                }
            }

            FolderScanResult(folderName = folderName, tracks = tracks)
        } catch (e: SecurityException) {
            Log.w(TAG, "Selected folder permission is missing or revoked (${e.javaClass.simpleName})")
            FolderScanResult(
                folderName = folderName,
                tracks = emptyList(),
                error = "Access to the selected folder was denied or revoked. Choose the folder again."
            )
        } catch (e: Exception) {
            Log.w(TAG, "Selected folder could not be read (${e.javaClass.simpleName})")
            FolderScanResult(
                folderName = folderName,
                tracks = emptyList(),
                error = "The selected folder could not be read. It may have been moved, deleted, or become unavailable."
            )
        }
    }

    private fun queryDisplayName(treeUri: Uri, docId: String): String? {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        context.contentResolver.query(
            docUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (nameIdx >= 0) return cursor.getString(nameIdx)
            }
        }
        return null
    }

    private fun queryChildren(treeUri: Uri, parentDocId: String): Cursor? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        return context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ),
            null, null, null
        )
    }

    private fun readFolderTrack(
        treeUri: Uri,
        docId: String,
        fileName: String,
        mimeType: String,
        lastModified: Long,
        folderName: String
    ): Track {
        val trackUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

        var title = fileName.substringBeforeLast('.').replace('_', ' ')
        var artist = "Unknown Artist"
        var album = folderName
        var durationMs = 0L
        // 0 = unknown; the player hides values it doesn't have rather than guessing
        var bitrateKbps = 0
        var sampleRateHz = 0

        val mmr = MediaMetadataRetriever()
        try {
            context.contentResolver.openFileDescriptor(trackUri, "r")?.use { pfd ->
                mmr.setDataSource(pfd.fileDescriptor)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { title = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { artist = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { album = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { durationMs = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { bitrateKbps = it / 1000 }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?.let { sampleRateHz = it }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read metadata for a track in the selected folder (${e.javaClass.simpleName})")
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {}
        }

        return Track(
            id = folderTrackId(docId),
            contentUri = trackUri.toString(),
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            mimeType = mimeType,
            folderPath = folderName,
            dateAdded = lastModified,
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRateHz
        )
    }

    private fun folderTrackId(docId: String) = "folder_${docId.hashCode()}"

    suspend fun setTracks(tracks: List<Track>) {
        _allTracks.value = tracks
        withContext(Dispatchers.IO) {
            libraryDao.replaceAll(tracks.map {
                LibraryTrackEntity(
                    id = it.id,
                    contentUri = it.contentUri,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    durationMs = it.durationMs,
                    folderPath = it.folderPath,
                    dateAdded = it.dateAdded,
                    mimeType = it.mimeType,
                    bitrateKbps = it.bitrateKbps,
                    sampleRateHz = it.sampleRateHz,
                    albumArtUri = it.albumArtUri
                )
            })
            // A song's id is its path, but its address also names the folder it was reached
            // through; after folders change (Music replaced by Music/J-pop...), saved copies would
            // point through a folder the app no longer has access to.
            // shortcut: three small updates per song on every scan; batch them if libraries grow to thousands.
            tracks.forEach {
                playlistDao.relink(it.id, it.contentUri, it.albumArtUri)
                queueDao.relink(it.id, it.contentUri, it.albumArtUri)
                historyDao.relink(it.id, it.contentUri, it.albumArtUri)
            }
        }
    }

    /** Publishes the last persisted scan immediately and returns it for incremental rescans. */
    suspend fun loadCachedLibrary(): List<Track> {
        val tracks = withContext(Dispatchers.IO) {
            libraryDao.getAll().map {
                Track(
                    id = it.id,
                    contentUri = it.contentUri,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    durationMs = it.durationMs,
                    folderPath = it.folderPath,
                    dateAdded = it.dateAdded,
                    mimeType = it.mimeType,
                    bitrateKbps = it.bitrateKbps,
                    sampleRateHz = it.sampleRateHz,
                    albumArtUri = it.albumArtUri
                )
            }
        }
        _allTracks.value = tracks
        return tracks
    }


    suspend fun saveQueue(tracks: List<Track>) {
        withContext(Dispatchers.IO) {
            val entities = tracks.mapIndexed { idx, track ->
                QueueEntity(
                    position = idx,
                    trackId = track.id,
                    uri = track.contentUri,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    albumArtUri = track.albumArtUri
                )
            }
            queueDao.replaceQueue(entities)
        }
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(PlaylistEntity(name = name.trim()))
    }

    suspend fun renamePlaylist(id: Long, name: String) = withContext(Dispatchers.IO) {
        playlistDao.renamePlaylist(id, name.trim())
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylistAndTracks(id)
    }

    /**
     * Saves a playlist's details. [photo] is a newly picked image: it's copied into the app, since
     * the photo picker's access ends with the app. A replaced or dropped photo file is deleted.
     */
    suspend fun updatePlaylistDetails(old: PlaylistEntity, new: PlaylistEntity, photo: Uri?) = withContext(Dispatchers.IO) {
        val coverUri = photo?.let { copyPlaylistPhoto(old.id, it) ?: old.coverUri } ?: new.coverUri
        playlistDao.updateDetails(old.id, new.name.trim(), new.description.trim(), coverUri, new.eqPreset)
        if (old.coverUri != coverUri) deletePlaylistPhoto(old.coverUri)
    }

    // A new name each time, so Coil doesn't show the cached old photo.
    private fun copyPlaylistPhoto(id: Long, photo: Uri): String? = try {
        val dir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
        val file = File(dir, "${id}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(photo)?.use { input -> file.outputStream().use { input.copyTo(it) } }
        Uri.fromFile(file).toString()
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't copy playlist photo: ${e.message}")
        null
    }

    fun deletePlaylistPhoto(coverUri: String?) {
        if (coverUri?.startsWith("file:") == true) Uri.parse(coverUri).path?.let { File(it).delete() }
    }

    suspend fun addTrackToPlaylist(playlistId: Long, track: Track) = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = track.id,
                // Append after the last song (the old time-based value wrapped every 100 s).
                sortOrder = playlistDao.nextSortOrder(playlistId),
                uri = track.contentUri,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                albumArtUri = track.albumArtUri
            )
        )
    }

    suspend fun removePlaylistRow(rowId: Long) = withContext(Dispatchers.IO) {
        playlistDao.removePlaylistRow(rowId)
    }

    suspend fun reorderPlaylist(rowIdsInOrder: List<Long>) = withContext(Dispatchers.IO) {
        playlistDao.reorder(rowIdsInOrder)
    }

    fun getPlaylistTracks(playlistId: Long): Flow<List<PlaylistTrackEntity>> {
        return playlistDao.getTracksForPlaylist(playlistId)
    }

    suspend fun extractAccentColor(track: Track?): Color = withContext(Dispatchers.IO) {
        if (track == null) return@withContext WarmIvory
        accentColorCache[track.id]?.let { return@withContext it }

        var bitmap: Bitmap? = null
        // Same order as the artwork shown: embedded art first, then the folder's cover image.
        try {
            if (track.contentUri.startsWith("content://")) {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(context, Uri.parse(track.contentUri))
                    val artBytes = mmr.embeddedPicture
                    if (artBytes != null) {
                        bitmap = decodeSampled { opts ->
                            BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, opts)
                        }
                    }
                } finally {
                    mmr.release()
                }
            }
        } catch (_: Exception) {}
        try {
            if (bitmap == null && track.albumArtUri != null) {
                val uri = Uri.parse(track.albumArtUri)
                bitmap = decodeSampled { opts ->
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                }
            }
        } catch (_: Exception) {}

        val extractedColor: Color = if (bitmap != null) {
            val palette = Palette.from(bitmap!!).generate()
            val rgb = accentFromSwatches(palette.swatches.map { it.rgb to it.population })

            if (rgb != null) {
                // Ensure contrast against dark terminal #121212 background (luminance > 0.35)
                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(rgb, hsl)
                if (hsl[2] < 0.45f) {
                    hsl[2] = 0.55f // brighten up dark colors for readability
                }
                Color(ColorUtils.HSLToColor(hsl))
            } else {
                // A black-and-white or grey cover gets a white accent, not a colour it barely has.
                MoonWhite
            }
        } else {
            WarmIvory
        }

        accentColorCache[track.id] = extractedColor
        extractedColor
    }

    // Palette only needs a thumbnail; decoding full-size cover art can allocate tens of MB.
    private fun decodeSampled(decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 200 && bounds.outHeight / (sample * 2) >= 200) sample *= 2
        return decode(BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
