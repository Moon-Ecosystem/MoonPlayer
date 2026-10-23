package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.example.data.db.FavoriteEntity
import com.example.data.db.HistoryEntity
import com.example.data.db.MoonPlayerDatabase
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistTrackEntity
import com.example.data.db.QueueEntity
import com.example.data.model.Track
import com.example.ui.theme.WarmIvory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MusicRepository(private val context: Context) {

    private val db = MoonPlayerDatabase.getInstance(context)
    private val playlistDao = db.playlistDao()
    private val favoriteDao = db.favoriteDao()
    private val historyDao = db.historyDao()
    private val queueDao = db.queueDao()
    private val trackStatDao = db.trackStatDao()

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())
    val allTracks = _allTracks.asStateFlow()

    private val accentColorCache = mutableMapOf<String, Color>()

    val allPlaylists = playlistDao.getAllPlaylists()
    val favoriteTrackIds = favoriteDao.getAllFavoriteIds()
    val playHistory = historyDao.getRecentHistory()
    val savedQueue = queueDao.getSavedQueue()

    val topPlayedTracks = trackStatDao.getTopPlayedTracks()
    val mostListenedTracks = trackStatDao.getMostListenedTracks()
    val totalPlays = trackStatDao.getTotalPlays()
    val totalListenTimeMs = trackStatDao.getTotalListenTimeMs()

    val favoriteTracks: Flow<List<Track>> = combine(_allTracks, favoriteTrackIds) { tracks, favIds ->
        val favSet = favIds.toSet()
        tracks.filter { favSet.contains(it.id) }.map { it.copy(isFavorite = true) }
    }

    suspend fun scanMediaStore(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )

        // Strict MP3 filtering as requested by specification
        val selection = "(${MediaStore.Audio.Media.MIME_TYPE} = ? OR ${MediaStore.Audio.Media.DATA} LIKE '%.mp3')"
        val selectionArgs = arrayOf("audio/mpeg")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackNumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Track"
                    val artist = cursor.getString(artistCol).takeIf { it != "<unknown>" } ?: "Unknown Artist"
                    val album = cursor.getString(albumCol).takeIf { it != "<unknown>" } ?: "Unknown Album"
                    val duration = cursor.getLong(durCol)
                    val trackNum = cursor.getInt(trackNumCol)
                    val year = cursor.getInt(yearCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val dataPath = cursor.getString(dataCol) ?: ""
                    val dateAdded = cursor.getLong(dateAddedCol)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    val folder = if (dataPath.isNotEmpty()) {
                        File(dataPath).parentFile?.name ?: "Music"
                    } else "Music"

                    tracks.add(
                        Track(
                            id = id.toString(),
                            contentUri = contentUri,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            trackNumber = trackNum,
                            year = year,
                            mimeType = "audio/mpeg",
                            albumArtUri = albumArtUri,
                            folderPath = folder,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Always check bundled demo tracks if media store returned empty or to supplement
        if (tracks.isEmpty()) {
            val demoTracks = loadBundledDemoTracks()
            tracks.addAll(demoTracks)
        }

        _allTracks.value = tracks
        tracks
    }

    suspend fun loadBundledDemoTracks(): List<Track> = withContext(Dispatchers.IO) {
        val demoList = mutableListOf<Track>()
        val musicDir = File(context.filesDir, "sample_music").apply { mkdirs() }

        try {
            val sampleFiles = context.assets.list("sample_tracks") ?: emptyArray()
            val metaData = mapOf(
                "neon_nightfall.mp3" to Triple("Neon Nightfall", "Moon Cat", "Terminal Sessions"),
                "cyber_dreamer.mp3" to Triple("Cyber Dreamer", "Aether Code", "Terminal Sessions"),
                "coffee_and_monospace.mp3" to Triple("Coffee & Monospace", "Lo-Fi Bit", "Terminal Sessions"),
                "midnight_protocol.mp3" to Triple("Midnight Protocol", "Starlight Unit", "Zero Gravity")
            )

            for ((index, fileName) in sampleFiles.withIndex()) {
                val destFile = File(musicDir, fileName)
                if (!destFile.exists() || destFile.length() == 0L) {
                    context.assets.open("sample_tracks/$fileName").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                val mmr = MediaMetadataRetriever()
                var durationMs = 30000L
                try {
                    mmr.setDataSource(destFile.absolutePath)
                    val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    durStr?.toLongOrNull()?.let { durationMs = it }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try { mmr.release() } catch (_: Exception) {}
                }

                val info = metaData[fileName] ?: Triple(fileName.removeSuffix(".mp3"), "Moon Artist", "Terminal Beats")
                val track = Track(
                    id = "demo_$index",
                    contentUri = Uri.fromFile(destFile).toString(),
                    title = info.first,
                    artist = info.second,
                    album = info.third,
                    durationMs = durationMs,
                    trackNumber = index + 1,
                    year = 2026,
                    mimeType = "audio/mpeg",
                    albumArtUri = null, // uses default mascot cover
                    folderPath = "Internal / Moon Samples",
                    dateAdded = System.currentTimeMillis()
                )
                demoList.add(track)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        demoList
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

    suspend fun recordHistory(track: Track) {
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
                    albumArtUri = track.albumArtUri
                )
            )
            // Record listening statistics
            trackStatDao.recordPlay(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs
            )
        }
    }

    suspend fun scanFolderUri(treeUri: Uri): Pair<String, List<Track>> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        var folderName = "Linked Folder"

        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        folderName = c.getString(nameIdx) ?: "Linked Folder"
                    }
                }
            }

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            context.contentResolver.query(
                childrenUri,
                projection,
                null, null, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val mime = cursor.getString(mimeIdx) ?: ""
                    val name = cursor.getString(nameIdx) ?: ""
                    val childDocId = cursor.getString(idIdx)
                    val lastMod = cursor.getLong(modIdx)

                    // EXCLUSIVELY MP3 FILES - ignores directories and non-mp3 files
                    val isMp3 = name.endsWith(".mp3", ignoreCase = true) || mime == "audio/mpeg" || mime == "audio/mp3"
                    val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR

                    if (isMp3 && !isDirectory) {
                        val trackUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)

                        var title = name.removeSuffix(".mp3").replace('_', ' ')
                        var artist = "Unknown Artist"
                        var album = folderName
                        var durationMs = 0L

                        try {
                            val mmr = MediaMetadataRetriever()
                            context.contentResolver.openFileDescriptor(trackUri, "r")?.use { pfd ->
                                mmr.setDataSource(pfd.fileDescriptor)
                                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { title = it }
                                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { artist = it }
                                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { album = it }
                                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { durationMs = it }
                            }
                            mmr.release()
                        } catch (_: Exception) {}

                        tracks.add(
                            Track(
                                id = "folder_${childDocId.hashCode()}",
                                contentUri = trackUri.toString(),
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = durationMs,
                                mimeType = "audio/mpeg",
                                folderPath = folderName,
                                dateAdded = lastMod
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Pair(folderName, tracks)
    }

    suspend fun importFolderAsPlaylist(folderName: String, tracks: List<Track>): Long = withContext(Dispatchers.IO) {
        val playlistId = playlistDao.insertPlaylist(PlaylistEntity(name = folderName))
        tracks.forEachIndexed { index, track ->
            playlistDao.insertPlaylistTrack(
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = track.id,
                    sortOrder = index,
                    uri = track.contentUri,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    albumArtUri = track.albumArtUri
                )
            )
        }
        playlistId
    }

    fun addCustomTracks(newTracks: List<Track>) {
        val current = _allTracks.value.toMutableList()
        val existingIds = current.map { it.id }.toSet()
        val filteredNew = newTracks.filterNot { existingIds.contains(it.id) }
        if (filteredNew.isNotEmpty()) {
            current.addAll(filteredNew)
            _allTracks.value = current
        }
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
        playlistDao.deletePlaylist(id)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, track: Track) = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = track.id,
                sortOrder = (System.currentTimeMillis() % 100000).toInt(),
                uri = track.contentUri,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                albumArtUri = track.albumArtUri
            )
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) = withContext(Dispatchers.IO) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)
    }

    fun getPlaylistTracks(playlistId: Long): Flow<List<PlaylistTrackEntity>> {
        return playlistDao.getTracksForPlaylist(playlistId)
    }

    suspend fun extractAccentColor(track: Track?): Color = withContext(Dispatchers.IO) {
        if (track == null) return@withContext WarmIvory
        accentColorCache[track.id]?.let { return@withContext it }

        var bitmap: Bitmap? = null
        try {
            if (track.albumArtUri != null) {
                val uri = Uri.parse(track.albumArtUri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    bitmap = BitmapFactory.decodeStream(stream)
                }
            } else if (track.contentUri.startsWith("content://")) {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(context, Uri.parse(track.contentUri))
                val artBytes = mmr.embeddedPicture
                if (artBytes != null) {
                    bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                }
                mmr.release()
            }
        } catch (_: Exception) {}

        val extractedColor: Color = if (bitmap != null) {
            val palette = Palette.from(bitmap!!).generate()
            val swatch = palette.dominantSwatch
                ?: palette.vibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.mutedSwatch
                ?: palette.lightMutedSwatch

            if (swatch != null) {
                // Ensure contrast against dark terminal #121212 background (luminance > 0.35)
                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(swatch.rgb, hsl)
                if (hsl[2] < 0.45f) {
                    hsl[2] = 0.55f // brighten up dark colors for readability
                }
                Color(ColorUtils.HSLToColor(hsl))
            } else {
                WarmIvory
            }
        } else {
            WarmIvory
        }

        accentColorCache[track.id] = extractedColor
        extractedColor
    }
}
