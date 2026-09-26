package com.kiroland.mediacenter.data.addons

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Installed add-ons, kept as their original JSON files in the app's private storage. */
@Singleton
class AddonRepository @Inject constructor(
    @param:ApplicationContext context: Context,
    okHttp: OkHttpClient,
) {
    private val dir = File(context.filesDir, "addons").apply { mkdirs() }
    private val http = okHttp.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _addons = MutableStateFlow(load())
    val addons: StateFlow<List<AddonManifest>> = _addons.asStateFlow()

    private val engine = AddonEngine { url, headers -> fetch(url, headers, MAX_PAGE_BYTES) }

    /** Validates and stores an add-on; one with the same id is replaced (updates). */
    @Synchronized
    fun install(text: String): Result<AddonManifest> = AddonParser.parse(text).mapCatching { addon ->
        val target = File(dir, "${addon.id}.json")
        val temp = File(dir, "${addon.id}.json.tmp")
        temp.writeText(text)
        check(temp.renameTo(target) || (target.delete() && temp.renameTo(target))) { "Nem sikerült menteni" }
        _addons.value = (_addons.value.filterNot { it.id == addon.id } + addon).sortedBy { it.name.lowercase() }
        Log.i(TAG, "Installed add-on ${addon.id} v${addon.version}")
        addon
    }

    suspend fun installFromUrl(url: String): Result<AddonManifest> = runCatching {
        require(url.startsWith("https://") || url.startsWith("http://")) { "Csak http(s) cím adható meg" }
        fetch(url, emptyMap(), MAX_ADDON_BYTES)
    }.mapCatching { install(it).getOrThrow() }

    @Synchronized
    fun remove(id: String): Boolean {
        val removed = File(dir, "$id.json").delete()
        _addons.value = _addons.value.filterNot { it.id == id }
        return removed
    }

    fun find(addonId: String): AddonManifest? = _addons.value.firstOrNull { it.id == addonId }

    /** The add-on channel that plays a built-in tile (e.g. "m1"), if any add-on offers one. */
    fun forBuiltin(builtinId: String): Pair<AddonManifest, AddonChannel>? =
        _addons.value.firstNotNullOfOrNull { addon -> addon.channels.firstOrNull { it.builtin == builtinId }?.let { addon to it } }

    /** @throws AddonException when the add-on or channel is gone, or a step fails. */
    suspend fun resolve(addonId: String, channelId: String): String {
        val addon = find(addonId) ?: throw AddonException("A kiegészítő már nincs telepítve")
        val channel = addon.channels.firstOrNull { it.id == channelId } ?: throw AddonException("Ismeretlen csatorna")
        return engine.resolve(addon, channel)
    }

    private suspend fun fetch(url: String, headers: Map<String, String>, limit: Long): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw AddonException("HTTP ${response.code}")
            val source = response.body.source()
            source.request(limit + 1)
            if (source.buffer.size > limit) throw AddonException("Túl nagy válasz (${limit / 1024} KB fölött)")
            source.readUtf8()
        }
    }

    private fun load(): List<AddonManifest> =
        dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { file ->
                AddonParser.parse(file.readText())
                    .onFailure { Log.w(TAG, "Skipping broken add-on ${file.name}: ${it.message}") }
                    .getOrNull()
            }
            .sortedBy { it.name.lowercase() }

    private companion object {
        const val TAG = "AddonRepository"
        const val MAX_ADDON_BYTES = 256L * 1024
        const val MAX_PAGE_BYTES = 5L * 1024 * 1024
    }
}
