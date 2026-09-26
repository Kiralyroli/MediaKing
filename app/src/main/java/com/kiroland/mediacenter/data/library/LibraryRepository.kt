package com.kiroland.mediacenter.data.library

import com.kiroland.mediacenter.data.library.db.LibraryDao
import com.kiroland.mediacenter.data.library.db.LibraryFolderEntity
import com.kiroland.mediacenter.data.library.db.MediaEntity
import com.kiroland.mediacenter.data.library.db.MediaKind
import com.kiroland.mediacenter.data.library.db.MediaWithProgress
import com.kiroland.mediacenter.data.library.db.MetadataEntity
import com.kiroland.mediacenter.data.library.db.WatchProgressEntity
import com.kiroland.mediacenter.data.watchnext.WatchNextPublisher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class SeriesSummary(
    val seriesKey: String,
    val title: String,
    val year: Int?,
    val metadata: MetadataEntity?,
    val seasonCount: Int,
    val episodeCount: Int,
    val watchedCount: Int,
    val latestAddedAt: Long,
)

/** A row of the "Continue watching" shelf: something half-watched, or the episode after the last finished one. */
data class ContinueItem(val item: MediaWithProgress, val isNextUp: Boolean, val sortTime: Long)

@Singleton
class LibraryRepository @Inject constructor(
    private val dao: LibraryDao,
    private val scanner: LibraryScanner,
    private val watchNext: WatchNextPublisher,
) {
    val folders: Flow<List<LibraryFolderEntity>> = dao.observeFolders()
    private val collator = Collator.getInstance(Locale.forLanguageTag("hu-HU"))

    val movies: Flow<List<MediaWithProgress>> = dao.observeMovies().map { list ->
        list.sortedWith(compareBy(collator) { it.displayTitle })
    }
    val scanState = scanner.state

    fun recentMovies(limit: Int = 20): Flow<List<MediaWithProgress>> = dao.observeRecentMovies(limit)

    val series: Flow<List<SeriesSummary>> = dao.observeEpisodes().map { episodes ->
        episodes.groupBy { it.media.seriesKey.orEmpty() }.map { (key, items) ->
            val metadata = items.firstNotNullOfOrNull { it.metadata?.takeIf { m -> m.tmdbId != null } }
            SeriesSummary(
                seriesKey = key,
                title = metadata?.title ?: items.first().media.title,
                year = metadata?.year ?: items.firstNotNullOfOrNull { it.media.year },
                metadata = metadata,
                seasonCount = items.mapNotNull { it.media.season }.distinct().size,
                episodeCount = items.size,
                watchedCount = items.count { it.isWatched },
                latestAddedAt = items.maxOf { it.media.addedAt },
            )
        }.sortedWith(compareBy(collator) { it.title })
    }

    fun seriesEpisodes(seriesKey: String): Flow<List<MediaWithProgress>> = dao.observeSeries(seriesKey)

    fun media(path: String): Flow<MediaWithProgress?> = dao.observeMedia(path)

    val continueWatching: Flow<List<ContinueItem>> =
        combine(dao.observeInProgress(limit = 30), dao.observeEpisodes()) { inProgress, episodes ->
            val started = inProgress.map { ContinueItem(it, isNextUp = false, sortTime = it.progressUpdatedAt ?: 0) }
            val startedSeries = inProgress.mapNotNullTo(HashSet()) { it.media.seriesKey }
            val nextUp = episodes.groupBy { it.media.seriesKey }
                .filterKeys { it != null && it !in startedSeries }
                .mapNotNull { (_, list) -> nextUpIn(list) }
            (started + nextUp).sortedByDescending { it.sortTime }.take(20)
        }

    /** After the most recently finished episode of a show, the first unwatched one that follows. */
    private fun nextUpIn(episodes: List<MediaWithProgress>): ContinueItem? {
        val lastWatched = episodes.filter { it.isWatched }.maxByOrNull { it.progressUpdatedAt ?: 0 } ?: return null
        val index = episodes.indexOf(lastWatched)
        val next = episodes.drop(index + 1).firstOrNull { !it.isWatched } ?: return null
        return ContinueItem(next, isNextUp = true, sortTime = lastWatched.progressUpdatedAt ?: 0)
    }

    suspend fun addFolder(path: String) {
        // A parent folder supersedes folders inside it; otherwise files would belong to two roots.
        dao.folders().filter { it.path.startsWith("$path/") }.forEach { dao.removeFolder(it.path) }
        dao.insertFolder(LibraryFolderEntity(path, System.currentTimeMillis()))
        scanner.scanFolder(path)
    }

    suspend fun removeFolder(path: String) = dao.removeFolder(path)

    fun rescan() = scanner.scanAll()

    // --- Playback ---

    suspend fun progress(path: String): WatchProgressEntity? = dao.progress(path)

    suspend fun saveProgress(path: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val finished = positionMs >= durationMs * FINISHED_FRACTION || durationMs - positionMs < FINISHED_REMAINING_MS
        dao.upsertProgress(WatchProgressEntity(path, positionMs, durationMs, finished, System.currentTimeMillis()))
        watchNext.update(path)
    }

    suspend fun markWatched(path: String, watched: Boolean) {
        if (watched) {
            val duration = dao.progress(path)?.durationMs ?: 0
            dao.upsertProgress(WatchProgressEntity(path, duration, duration, true, System.currentTimeMillis()))
        } else {
            dao.deleteProgress(path)
        }
        watchNext.update(path)
    }

    suspend fun nextEpisode(path: String): MediaEntity? {
        val current = dao.media(path) ?: return null
        if (current.kind != MediaKind.EPISODE) return null
        val key = current.seriesKey ?: return null
        return dao.nextEpisode(key, current.season ?: 0, current.episodeEnd ?: current.episode ?: 0)
    }

    private companion object {
        const val FINISHED_FRACTION = 0.93
        const val FINISHED_REMAINING_MS = 2 * 60_000L
    }
}
