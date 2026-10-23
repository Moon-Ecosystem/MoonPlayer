package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.data.EmbeddedArtFetcher
import com.example.data.EmbeddedArtKeyer

class MoonPlayerApplication : Application(), ImageLoaderFactory {
    // Every AsyncImage uses this loader, so artwork embedded in audio files works app-wide.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(EmbeddedArtKeyer())
            add(EmbeddedArtFetcher.Factory())
        }
        .build()
}
