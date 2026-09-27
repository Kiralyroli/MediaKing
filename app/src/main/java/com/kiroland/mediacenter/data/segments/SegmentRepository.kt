package com.kiroland.mediacenter.data.segments

import android.util.Log
import com.kiroland.mediacenter.data.library.db.MediaKind
import com.kiroland.mediacenter.data.library.db.MediaWithProgress
import com.kiroland.mediacenter.data.library.db.MetadataDao
import com.kiroland.mediacenter.data.library.db.SegmentCacheEntity
import com.kiroland.mediacenter.data.storage.MediaFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the intro, recap and credits are in a video: the file's own chapters when they are named
 * ("Intro", "Credits"), otherwise TheIntroDB, a community database keyed by TMDB id (cached).
 */
@Singleton
class SegmentRepository @Inject constructor(
    private val files: MediaFiles,
    private val dao: MetadataDao,
    okHttp: OkHttpClient,
) {
    private val http = okHttp.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun segmentsFor(item: MediaWithProgress): List<Segment> {
        val path = item.media.path
        val fromFile = if (path.endsWith(".mkv", ignoreCase = true)) {
            withContext(Dispatchers.IO) {
                MkvChapters.read { offset, length -> files.readRange(path, offset, length) }
            }?.let(ChapterSegments::fromChapters).orEmpty()
        } else {
            emptyList()
        }
        val fromDb = item.metadata?.tmdbId?.let { tmdbId -> introDb(tmdbId, item) }.orEmpty()
        // The file's own chapters match this very release; the database only fills the gaps.
        val known = fromFile.map { it.type }.toSet()
        return (fromFile + fromDb.filter { it.type !in known }).sortedBy { it.startMs }
    }

    private suspend fun introDb(tmdbId: Int, item: MediaWithProgress): List<Segment> {
        val media = item.media
        val episode = media.kind == MediaKind.EPISODE
        if (episode && (media.season == null || media.episode == null)) return emptyList()
        val key = if (episode) "tv:$tmdbId:${media.season}:${media.episode}" else "movie:$tmdbId"
        val cached = dao.cachedSegments(key)
        if (cached != null) {
            val segments = json.decodeFromString<List<Segment>>(cached.segmentsJson)
            // Episodes nobody has timed yet get another look after a while.
            val maxAge = if (segments.isEmpty()) RETRY_MISSING_MS else REFRESH_MS
            if (System.currentTimeMillis() - cached.fetchedAt < maxAge) return segments
        }
        val url = if (episode) {
            "$BASE?tmdb_id=$tmdbId&type=tv&season=${media.season}&episode=${media.episode}"
        } else {
            "$BASE?tmdb_id=$tmdbId&type=movie"
        }
        return try {
            val segments = withContext(Dispatchers.IO) { fetch(url) } ?: return cached?.let {
                json.decodeFromString<List<Segment>>(it.segmentsJson)
            }.orEmpty()
            dao.cacheSegments(SegmentCacheEntity(key, json.encodeToString(segments), System.currentTimeMillis()))
            segments
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "TheIntroDB lookup for $key failed: ${e.message}")
            emptyList()
        }
    }

    /** null when the request failed (try again next time), empty when the title is not in the database. */
    private fun fetch(url: String): List<Segment>? {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.code == 404) return emptyList()
            if (!response.isSuccessful) return null
            return json.decodeFromString<IntroDbMedia>(response.body.string()).toSegments()
        }
    }

    @Serializable
    private data class Range(@SerialName("start_ms") val start: Long? = null, @SerialName("end_ms") val end: Long? = null)

    @Serializable
    private data class IntroDbMedia(
        val intro: List<Range> = emptyList(),
        val recap: List<Range> = emptyList(),
        val credits: List<Range> = emptyList(),
        val preview: List<Range> = emptyList(),
    ) {
        fun toSegments(): List<Segment> =
            listOf(SegmentType.INTRO to intro, SegmentType.RECAP to recap, SegmentType.CREDITS to credits, SegmentType.PREVIEW to preview)
                .flatMap { (type, ranges) -> ranges.map { Segment(type, it.start ?: 0L, it.end) } }
                .filter { it.endMs == null || it.endMs > it.startMs }
    }

    private companion object {
        const val TAG = "SegmentRepository"
        const val BASE = "https://api.theintrodb.org/v3/media"
        const val REFRESH_MS = 30L * 24 * 60 * 60 * 1000
        const val RETRY_MISSING_MS = 7L * 24 * 60 * 60 * 1000
    }
}
