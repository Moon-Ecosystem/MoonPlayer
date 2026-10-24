package com.example.data

import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import com.example.data.model.Track
import okio.Buffer

/**
 * Coil model for cover art stored inside an audio file (ID3 APIC frame), falling back to
 * [coverUri] (the folder's cover.jpg or similar) when the file has none.
 */
data class EmbeddedArt(val contentUri: String, val coverUri: String? = null)

/** What to hand Coil for a track's artwork. */
val Track.artModel: Any
    get() = EmbeddedArt(contentUri, albumArtUri)

class EmbeddedArtFetcher(
    private val data: EmbeddedArt,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val retriever = MediaMetadataRetriever()
        val embedded = try {
            retriever.setDataSource(options.context, Uri.parse(data.contentUri))
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
        val bytes = embedded ?: data.coverUri?.let { uri ->
            options.context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
        }
        // Throwing (not returning null) makes Coil report an error, so the caller's placeholder stays.
        checkNotNull(bytes) { "No artwork" }
        return SourceResult(
            source = ImageSource(Buffer().write(bytes), options.context),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<EmbeddedArt> {
        override fun create(data: EmbeddedArt, options: Options, imageLoader: ImageLoader): Fetcher =
            EmbeddedArtFetcher(data, options)
    }
}

/** Lets Coil's memory cache hold decoded thumbnails so scrolling back doesn't re-read files. */
class EmbeddedArtKeyer : Keyer<EmbeddedArt> {
    override fun key(data: EmbeddedArt, options: Options): String = "${data.contentUri}|${data.coverUri}"
}
