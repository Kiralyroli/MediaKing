package com.kiroland.mediacenter.data.library.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MediaKind { MOVIE, EPISODE }

/** A video file inside a library folder, with what the file name parser recognised. */
@Entity(
    tableName = "media",
    indices = [Index("libraryRoot"), Index("seriesKey"), Index("addedAt")],
)
data class MediaEntity(
    @PrimaryKey val path: String,
    val libraryRoot: String,
    val fileName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val kind: MediaKind,
    /** Movie title, or the series title for episodes. */
    val title: String,
    val year: Int?,
    val seriesKey: String?,
    val season: Int?,
    val episode: Int?,
    val episodeEnd: Int?,
    /** When the file first showed up in the library (file mtime on the first scan). */
    val addedAt: Long,
    /** Links to [MetadataEntity.key]: "movie:<title>:<year>" or "tv:<seriesKey>". */
    @ColumnInfo(defaultValue = "NULL") val metadataKey: String? = null,
)

/**
 * TMDB data for a movie or a show, shared by every file with the same [key].
 * [tmdbId] null means "looked up, nothing found"; retried after a while.
 */
@Entity(tableName = "metadata")
data class MetadataEntity(
    @PrimaryKey val key: String,
    val tmdbId: Int?,
    val title: String?,
    val originalTitle: String?,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val year: Int?,
    val rating: Double?,
    val genres: String?,
    val runtimeMinutes: Int?,
    /** Director for movies, creators for shows. */
    val director: String?,
    val cast: String?,
    val fetchedAt: Long,
)

@Entity(tableName = "episode_metadata", primaryKeys = ["tvId", "season", "episode"])
data class EpisodeMetadataEntity(
    val tvId: Int,
    val season: Int,
    val episode: Int,
    val name: String?,
    val overview: String?,
    val stillPath: String?,
    val airDate: String?,
    val runtimeMinutes: Int?,
)

/** Keyed by path, not by media row, so progress survives rescans and a drive being unplugged. */
@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val path: String,
    val positionMs: Long,
    val durationMs: Long,
    val finished: Boolean,
    val updatedAt: Long,
)

/** A folder the user added to the library from the file browser. */
@Entity(tableName = "library_folder")
data class LibraryFolderEntity(
    @PrimaryKey val path: String,
    val addedAt: Long,
)

data class MediaWithProgress(
    @Embedded val media: MediaEntity,
    val positionMs: Long?,
    val durationMs: Long?,
    val finished: Boolean?,
    val progressUpdatedAt: Long?,
    @Relation(parentColumn = "metadataKey", entityColumn = "key")
    val metadata: MetadataEntity? = null,
) {
    /** TMDB's (Hungarian) title when known, else what the file name said. */
    val displayTitle: String get() = metadata?.title?.takeIf { it.isNotBlank() } ?: media.title
    val displayYear: Int? get() = metadata?.year ?: media.year

    /** 0..1 for partially watched items, null when never started or finished. */
    val progressFraction: Float?
        get() {
            val position = positionMs ?: return null
            val duration = durationMs?.takeIf { it > 0 } ?: return null
            if (finished == true || position <= 0) return null
            return (position.toFloat() / duration).coerceIn(0f, 1f)
        }

    val isWatched: Boolean get() = finished == true
}

data class PathAddedAt(val path: String, val addedAt: Long)
