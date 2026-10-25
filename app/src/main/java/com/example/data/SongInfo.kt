package com.example.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.flac.PictureFrame
import androidx.media3.extractor.metadata.flac.VorbisComment
import androidx.media3.extractor.metadata.id3.ApicFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.Id3Frame
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.example.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One titled block of the song info sheet, e.g. FILE with its (name, value) rows. */
data class InfoSection(val title: String, val rows: List<Pair<String, String>>)

/** The sheet's contents; [missing] when the file is no longer there (moved, renamed or deleted). */
data class SongInfo(val sections: List<InfoSection>, val missing: Boolean = false)

/**
 * False only when the provider says the file is gone; no permission isn't "missing". Folder
 * (tree) URIs report a gone file as IllegalArgumentException, not FileNotFoundException.
 */
fun fileExists(context: Context, uri: Uri): Boolean = try {
    context.contentResolver.openFileDescriptor(uri, "r")?.close()
    true
} catch (_: java.io.FileNotFoundException) {
    false
} catch (_: IllegalArgumentException) {
    false
} catch (_: Exception) {
    true
}

/** The whole sheet as plain text, for Copy. */
fun songInfoText(title: String, sections: List<InfoSection>): String =
    (listOf(title) + sections.map { s ->
        s.title + "\n" + s.rows.joinToString("\n") { (k, v) -> "$k: $v" }
    }).joinToString("\n\n")

private val ID3_NAMES = mapOf(
    "TIT2" to "Title", "TPE1" to "Artist", "TPE2" to "Album artist", "TALB" to "Album",
    "TRCK" to "Track", "TPOS" to "Disc", "TYER" to "Year", "TDRC" to "Date", "TDRL" to "Release date",
    "TCON" to "Genre", "TCOM" to "Composer", "TEXT" to "Lyricist", "TSSE" to "Encoder settings",
    "TENC" to "Encoded by", "TBPM" to "BPM", "TKEY" to "Key", "TCOP" to "Copyright",
    "TPUB" to "Publisher", "TLAN" to "Language", "TSRC" to "ISRC", "TLEN" to "Length (ms)",
    "TIT1" to "Grouping", "TIT3" to "Subtitle", "TPE3" to "Conductor", "TMED" to "Media",
    "TSOA" to "Album sort", "TSOP" to "Artist sort", "TSOT" to "Title sort", "TSO2" to "Album artist sort"
)

/** Codec name for a stream, e.g. ("audio/mp4a-latm", "mp4a.40.2") -> "AAC-LC". */
fun codecLabel(mimeType: String?, codecs: String?): String = when (mimeType) {
    "audio/mpeg" -> "MPEG Layer III"
    "audio/mpeg-L2" -> "MPEG Layer II"
    "audio/flac" -> "FLAC"
    "audio/opus" -> "Opus"
    "audio/vorbis" -> "Vorbis"
    "audio/raw" -> "PCM"
    "audio/alac" -> "ALAC"
    "audio/mp4a-latm" -> when (codecs) {
        "mp4a.40.2" -> "AAC-LC"
        "mp4a.40.5" -> "HE-AAC"
        "mp4a.40.29" -> "HE-AAC v2"
        else -> "AAC"
    }
    null -> "Unknown"
    else -> mimeType.substringAfter('/').uppercase()
}

/**
 * Tag rows from the file's metadata, in file order: ID3 frames (MP3, and MP4 atoms that Media3
 * maps onto them) and Vorbis comments (FLAC, Ogg, Opus). Pictures are left to the artwork section.
 */
@OptIn(UnstableApi::class)
fun tagRows(entries: List<Metadata.Entry>): List<Pair<String, String>> = entries.mapNotNull { e ->
    when (e) {
        is TextInformationFrame -> {
            val name = if (e.id == "TXXX") e.description ?: "TXXX" else ID3_NAMES[e.id] ?: e.id
            name to e.values.joinToString("; ")
        }
        // MP4 files repeat the comment as its description.
        is CommentFrame -> (if (e.description.isBlank() || e.description == e.text) "Comment" else "Comment (${e.description})") to e.text
        is InternalFrame -> e.description to e.text
        is VorbisComment -> e.key to e.value
        is ApicFrame, is PictureFrame -> null
        is Id3Frame -> e.id to e.toString()
        else -> null
    }
}.filter { it.second.isNotBlank() }

private fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000) "%.1f MB".format(Locale.US, bytes / 1_000_000.0) else "%.0f KB".format(Locale.US, bytes / 1000.0)

private fun exactDuration(ms: Long): String =
    "%02d:%02d.%03d".format(Locale.US, ms / 60_000, ms / 1000 % 60, ms % 1000)

private fun pictureRow(mime: String, bytes: ByteArray, width: Int = 0, height: Int = 0): String {
    var w = width
    var h = height
    if (w <= 0 || h <= 0) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        w = bounds.outWidth
        h = bounds.outHeight
    }
    val type = mime.substringAfter('/').uppercase().ifBlank { "IMAGE" }
    return listOfNotNull(type, "$w × $h".takeIf { w > 0 && h > 0 }, formatSize(bytes.size.toLong())).joinToString(" · ")
}

/**
 * Everything the phone can tell about [track]'s file: name and size from the document provider,
 * the stream and every tag from Media3's own parse of the file (what ffprobe would show).
 * Rows the file doesn't report are left out.
 */
@OptIn(UnstableApi::class)
suspend fun readSongInfo(context: Context, track: Track): SongInfo = withContext(Dispatchers.IO) {
    val uri = Uri.parse(track.contentUri)
    if (!fileExists(context, uri)) {
        // Only what Moon Player saved about it; the file can't be read.
        return@withContext SongInfo(
            listOf(
                InfoSection(
                    "LAST KNOWN",
                    listOfNotNull(
                        "Artist" to track.artist,
                        track.album.takeIf { it.isNotBlank() }?.let { "Album" to it },
                        track.durationMs.takeIf { it > 0 }?.let { "Duration" to exactDuration(it) },
                        "Location" to Uri.decode(track.contentUri).substringAfterLast(':')
                    )
                )
            ),
            missing = true
        )
    }

    val file = mutableListOf<Pair<String, String>>()
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it) }
                    ?.let { file += "Name" to it }
                // Path-style document IDs ("primary:Music/J-pop/a.m4a") give the full folder.
                val docPath = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                    ?.substringAfter(':')?.takeIf { '/' in it }?.substringBeforeLast('/')
                (docPath ?: track.folderPath.ifBlank { null })?.let { file += "Folder" to it }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }
                    ?.let { file += "Size" to "${formatSize(it)} (${"%,d".format(Locale.US, it)} bytes)" }
                c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED).takeIf { it >= 0 && !c.isNull(it) }
                    ?.let { c.getLong(it) }?.takeIf { it > 0 }
                    ?.let { file += "Modified" to DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) }
            }
        }
    } catch (_: Exception) {
        // A provider that won't answer: the file section just has less in it.
    }
    if (track.mimeType.isNotBlank()) file += "Type" to track.mimeType

    val format: Format? = try {
        MetadataRetriever.retrieveMetadata(context, MediaItem.fromUri(uri)).get(10, TimeUnit.SECONDS)
            .let { groups -> (0 until groups.length).map { groups[it].getFormat(0) } }
            .firstOrNull { it.sampleMimeType?.startsWith("audio/") == true }
    } catch (_: Exception) {
        null
    }

    val stream = mutableListOf<Pair<String, String>>()
    stream += "Codec" to codecLabel(format?.sampleMimeType ?: track.mimeType.ifBlank { null }, format?.codecs)
    format?.codecs?.let { stream += "Codec string" to it }
    val bitrate = format?.averageBitrate?.takeIf { it > 0 } ?: format?.bitrate?.takeIf { it > 0 }
    (bitrate?.let { it / 1000 } ?: track.bitrateKbps.takeIf { it > 0 })?.let { stream += "Bitrate" to "$it kbps" }
    format?.peakBitrate?.takeIf { it > 0 && it != bitrate }?.let { stream += "Peak bitrate" to "${it / 1000} kbps" }
    (format?.sampleRate?.takeIf { it > 0 } ?: track.sampleRateHz.takeIf { it > 0 })
        ?.let { stream += "Sample rate" to "%,d Hz".format(Locale.US, it) }
    format?.channelCount?.takeIf { it > 0 }?.let {
        stream += "Channels" to when (it) { 1 -> "1 (mono)"; 2 -> "2 (stereo)"; else -> "$it" }
    }
    when (format?.pcmEncoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 32
        else -> null
    }?.let { stream += "Bit depth" to "$it-bit" }
    if (track.durationMs > 0) stream += "Duration" to exactDuration(track.durationMs)
    if ((format?.encoderDelay ?: 0) > 0 || (format?.encoderPadding ?: 0) > 0) {
        stream += "Gapless" to "delay ${format!!.encoderDelay} · padding ${format.encoderPadding} samples"
    }

    val entries = format?.metadata?.let { m -> (0 until m.length()).map { m[it] } }.orEmpty()
    val art = mutableListOf<Pair<String, String>>()
    entries.firstNotNullOfOrNull { e ->
        when (e) {
            is ApicFrame -> pictureRow(e.mimeType, e.pictureData)
            is PictureFrame -> pictureRow(e.mimeType, e.pictureData, e.width, e.height)
            else -> null
        }
    }?.let { art += "Source" to "Embedded in the file"; art += "Image" to it }
        ?: track.albumArtUri?.let {
            art += "Source" to "Folder image"
            art += "File" to Uri.decode(it).substringAfterLast('/')
        }

    SongInfo(
        listOf(
            InfoSection("FILE", file),
            InfoSection("AUDIO STREAM", stream),
            InfoSection("TAGS", tagRows(entries)),
            InfoSection("ARTWORK", art)
        ).filter { it.rows.isNotEmpty() }
    )
}
