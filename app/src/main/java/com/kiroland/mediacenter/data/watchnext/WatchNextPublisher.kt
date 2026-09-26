package com.kiroland.mediacenter.data.watchnext

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.kiroland.mediacenter.data.library.db.LibraryDao
import com.kiroland.mediacenter.data.library.db.MediaEntity
import com.kiroland.mediacenter.data.library.db.MediaKind
import com.kiroland.mediacenter.data.library.db.MediaWithProgress
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbImages
import com.kiroland.mediacenter.ui.library.episodeCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors "continue watching" onto the Android TV home screen's Watch Next row: half-watched files as
 * CONTINUE, the episode after a finished one as NEXT. Programs are keyed by file path
 * (internal provider id) and open through [WatchNextActivity].
 */
@Singleton
class WatchNextPublisher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: LibraryDao,
) {
    /** Call after the progress of [path] changed (played, finished, marked watched or unwatched). */
    suspend fun update(path: String) = withContext(Dispatchers.IO) {
        runCatching {
            val item = dao.observeMedia(path).first() ?: return@runCatching remove(path)
            when {
                item.isWatched -> {
                    remove(path)
                    if (item.media.kind == MediaKind.EPISODE) publishNext(item.media)
                }
                item.progressFraction != null -> {
                    upsert(item, TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    // A newer episode being watched supersedes an older "next" suggestion of the show.
                    item.media.seriesKey?.let { key -> removeOthersOfSeries(key, keep = path) }
                }
                else -> remove(path)
            }
        }.onFailure { Log.w(TAG, "Watch Next update failed: ${it.message}") }
    }

    private suspend fun publishNext(finished: MediaEntity) {
        val key = finished.seriesKey ?: return
        val next = dao.nextEpisode(key, finished.season ?: 0, finished.episodeEnd ?: finished.episode ?: 0) ?: return
        val item = dao.observeMedia(next.path).first() ?: return
        if (item.isWatched) return
        upsert(item, TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT)
    }

    @SuppressLint("RestrictedApi")
    private fun upsert(item: MediaWithProgress, type: Int) {
        val media = item.media
        val isEpisode = media.kind == MediaKind.EPISODE
        val meta = item.metadata?.takeIf { it.tmdbId != null }
        val builder = WatchNextProgram.Builder()
            .setType(if (isEpisode) TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE else TvContractCompat.PreviewPrograms.TYPE_MOVIE)
            .setWatchNextType(type)
            .setLastEngagementTimeUtcMillis(item.progressUpdatedAt ?: System.currentTimeMillis())
            .setTitle(item.displayTitle)
            .setInternalProviderId(media.path)
            .setIntentUri(WatchNextActivity.uriFor(media.path))
        if (isEpisode) {
            builder.setEpisodeTitle(episodeCode(media.season, media.episode, media.episodeEnd))
            media.season?.let { builder.setSeasonNumber(it) }
            media.episode?.let { builder.setEpisodeNumber(it) }
        }
        meta?.overview?.let { builder.setDescription(it) }
        (TmdbImages.backdrop(meta?.backdropPath) ?: TmdbImages.poster(meta?.posterPath))?.let {
            builder.setPosterArtUri(Uri.parse(it))
                .setPosterArtAspectRatio(
                    if (meta?.backdropPath != null) TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9
                    else TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3,
                )
        }
        if (type == TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE) {
            item.positionMs?.let { builder.setLastPlaybackPositionMillis(it.toInt()) }
            item.durationMs?.let { builder.setDurationMillis(it.toInt()) }
        }
        val values = builder.build().toContentValues()
        val existing = findId(media.path)
        val resolver = context.contentResolver
        if (existing != null) {
            resolver.update(TvContractCompat.buildWatchNextProgramUri(existing), values, null, null)
        } else {
            resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)
        }
    }

    private fun remove(path: String) {
        findId(path)?.let { context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(it), null, null) }
    }

    private suspend fun removeOthersOfSeries(seriesKey: String, keep: String) {
        val paths = dao.observeSeries(seriesKey).first().map { it.media.path }.filter { it != keep }
        ours().filter { (_, path) -> path in paths }.forEach { (id, _) ->
            context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null)
        }
    }

    private fun findId(path: String): Long? = ours().firstOrNull { it.second == path }?.first

    /** (row id, file path) of the programs this app published; the provider only returns our own. */
    @SuppressLint("RestrictedApi")
    private fun ours(): List<Pair<Long, String>> {
        val cursor = context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI, WatchNextProgram.PROJECTION, null, null, null,
        ) ?: return emptyList()
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val program = WatchNextProgram.fromCursor(it)
                    program.internalProviderId?.let { path -> add(program.id to path) }
                }
            }
        }
    }

    private companion object {
        const val TAG = "WatchNext"
    }
}
