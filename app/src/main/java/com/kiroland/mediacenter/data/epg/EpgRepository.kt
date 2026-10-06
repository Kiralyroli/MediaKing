package com.kiroland.mediacenter.data.epg

import com.kiroland.mediacenter.R
import com.kiroland.mediacenter.util.AppLocale
import android.util.Log
import com.kiroland.mediacenter.data.addons.AddonChannel
import com.kiroland.mediacenter.data.addons.AddonRepository
import com.kiroland.mediacenter.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Programme guides of the installed add-ons, for "now / next" on the Live TV tiles and in the player.
 * Refreshed when the channel catalogue changes and every few hours; only a day's window is kept.
 */
@Singleton
class EpgRepository @Inject constructor(
    private val addons: AddonRepository,
    okHttp: OkHttpClient,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val http = okHttp.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
    private val mutex = Mutex()

    /** add-on id → epg id → programmes (sorted). */
    private val _guides = MutableStateFlow<Map<String, Map<String, List<Programme>>>>(emptyMap())
    val guides: StateFlow<Map<String, Map<String, List<Programme>>>> = _guides.asStateFlow()

    /** add-on id → (guide url, the epg ids it was loaded for, when). */
    private val loaded = HashMap<String, Triple<String, Set<String>, Long>>()

    init {
        @OptIn(FlowPreview::class)
        scope.launch { addons.catalog.debounce(2_000).collect { refresh() } }
        scope.launch {
            while (true) {
                delay(REFRESH_MS)
                refresh(force = true)
            }
        }
    }

    fun nowNext(addonId: String, channel: AddonChannel, now: Long = System.currentTimeMillis()): Pair<Programme?, Programme?> {
        val epgId = channel.epgId ?: return null to null
        return XmltvParser.nowNext(_guides.value[addonId]?.get(epgId), now)
    }

    suspend fun refresh(force: Boolean = false) = mutex.withLock {
        val catalog = addons.catalog.value
        for (addon in addons.addons.value) {
            val url = addons.guideUrl(addon) ?: continue
            val wanted = catalog[addon.id].orEmpty().mapNotNullTo(HashSet()) { it.epgId }
            if (wanted.isEmpty()) continue
            val previous = loaded[addon.id]
            val fresh = previous != null && previous.first == url && previous.second == wanted &&
                System.currentTimeMillis() - previous.third < REFRESH_MS
            if (fresh && !force) continue
            runCatching { download(url, wanted) }
                .onSuccess { guide ->
                    loaded[addon.id] = Triple(url, wanted, System.currentTimeMillis())
                    _guides.value = _guides.value + (addon.id to guide)
                    Log.i(TAG, "Guide for ${addon.id}: ${guide.size}/${wanted.size} channels")
                }
                .onFailure { Log.w(TAG, "Guide for ${addon.id} failed: ${it.message}") }
        }
        // Forget guides of removed add-ons.
        val ids = addons.addons.value.map { it.id }.toSet()
        _guides.value = _guides.value.filterKeys { it in ids }
    }

    private suspend fun download(url: String, wanted: Set<String>): Map<String, List<Programme>> = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val raw = BufferedInputStream(LimitedStream(response.body.byteStream(), MAX_GUIDE_BYTES))
            // Guides are often served gzipped without saying so; sniff the magic bytes.
            raw.mark(2)
            val gzip = raw.read() == 0x1f && raw.read() == 0x8b
            raw.reset()
            val input = if (gzip) GZIPInputStream(raw) else raw
            val now = System.currentTimeMillis()
            XmltvParser.parse(input, wanted, from = now - WINDOW_BEFORE_MS, to = now + WINDOW_AFTER_MS)
        }
    }

    /** Guards the TV's memory and storage against runaway downloads. */
    private class LimitedStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) check(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) check(it.toLong()) }
        private fun check(n: Long) {
            count += n
            if (count > limit) throw IOException(AppLocale.text(R.string.epg_too_large))
        }
    }

    private companion object {
        const val TAG = "EpgRepository"
        const val REFRESH_MS = 6 * 60 * 60 * 1000L
        const val WINDOW_BEFORE_MS = 2 * 60 * 60 * 1000L
        const val WINDOW_AFTER_MS = 30 * 60 * 60 * 1000L
        const val MAX_GUIDE_BYTES = 200L * 1024 * 1024
    }
}
