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

/** Coil model for cover art stored inside an audio file (ID3 APIC frame). */
data class EmbeddedArt(val contentUri: String)

/** What to hand Coil for a track's artwork: a standalone cover if known, else the file's embedded art. */
val Track.artModel: Any
    get() = albumArtUri ?: EmbeddedArt(contentUri)

class EmbeddedArtFetcher(
    private val data: EmbeddedArt,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val retriever = MediaMetadataRetriever()
        val bytes = try {
            retriever.setDataSource(options.context, Uri.parse(data.contentUri))
            retriever.embeddedPicture
        } finally {
            retriever.release()
        }
        // Throwing (not returning null) makes Coil report an error, so the caller's placeholder stays.
        checkNotNull(bytes) { "No embedded artwork" }
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
    override fun key(data: EmbeddedArt, options: Options): String = data.contentUri
}
