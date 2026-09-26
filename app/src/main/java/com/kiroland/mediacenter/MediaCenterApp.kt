package com.kiroland.mediacenter

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.kiroland.mediacenter.data.library.LibraryScanner
import com.kiroland.mediacenter.data.storage.StorageRepository
import com.kiroland.mediacenter.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

@HiltAndroidApp
class MediaCenterApp : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var scanner: LibraryScanner
    @Inject lateinit var storageRepository: StorageRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        // Rescan at start and whenever a drive comes or goes (mount events arrive in bursts).
        appScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            storageRepository.volumeChanges()
                .onStart { emit(Unit) }
                .debounce(2_000)
                .collect { scanner.scanAll() }
        }
    }

    /** Posters and backdrops: small memory cache (the TV has ~1.8 GB RAM), generous disk cache. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
