package com.kiroland.mediacenter.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kiroland.mediacenter.data.library.ContinueItem
import com.kiroland.mediacenter.data.library.LibraryRepository
import com.kiroland.mediacenter.data.library.ScanState
import com.kiroland.mediacenter.data.library.SeriesSummary
import com.kiroland.mediacenter.data.library.SeasonOverview
import com.kiroland.mediacenter.data.library.SeriesOverview
import com.kiroland.mediacenter.data.library.SeriesFacts
import com.kiroland.mediacenter.data.library.WatchedRepository
import com.kiroland.mediacenter.data.library.WatchedTitle
import com.kiroland.mediacenter.data.library.db.WatchedTitleEntity
import com.kiroland.mediacenter.data.library.db.LibraryFolderEntity
import com.kiroland.mediacenter.data.library.db.EpisodeMetadataEntity
import com.kiroland.mediacenter.data.library.db.MediaWithProgress
import com.kiroland.mediacenter.data.metadata.MetadataRepository
import com.kiroland.mediacenter.data.streaming.StreamingRepository
import com.kiroland.mediacenter.ui.streaming.WatchState
import com.kiroland.mediacenter.ui.streaming.loadWhereToWatch
import kotlinx.coroutines.flow.mapLatest
import com.kiroland.mediacenter.ui.MovieRoute
import com.kiroland.mediacenter.ui.SeriesRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun <T> Flow<T>.stateIn(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

/** Shared by the home shelves, the movie grid and the series grid. null = still loading. */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
) : ViewModel() {
    val folders: StateFlow<List<LibraryFolderEntity>?> = repository.folders.stateIn(this, null)
    val continueWatching: StateFlow<List<ContinueItem>?> = repository.continueWatching.stateIn(this, null)
    val recentMovies: StateFlow<List<MediaWithProgress>?> = repository.recentMovies().stateIn(this, null)
    val movies: StateFlow<List<MediaWithProgress>?> = repository.movies.stateIn(this, null)
    val series: StateFlow<List<SeriesSummary>?> = repository.series.stateIn(this, null)
    val scanState: StateFlow<ScanState> = repository.scanState

    fun rescan() = repository.rescan()
}

@HiltViewModel
class MovieViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LibraryRepository,
    val streaming: StreamingRepository,
    private val watched: WatchedRepository,
) : ViewModel() {
    val path = savedStateHandle.toRoute<MovieRoute>().path
    val movie: StateFlow<MediaWithProgress?> = repository.media(path).stateIn(this, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val whereToWatch: StateFlow<WatchState> = movie
        .map { it?.metadata?.tmdbId }
        .distinctUntilChanged()
        .mapLatest { id -> loadWhereToWatch(streaming, isMovie = true, id) }
        .stateIn(this, WatchState.Loading)

    fun setWatched(watched: Boolean) {
        viewModelScope.launch {
            repository.markWatched(path, watched)
            // Taking it back also takes it off the watched list.
            if (!watched) movie.value?.metadata?.tmdbId?.let { this@MovieViewModel.watched.unmark(true, it) }
        }
    }

    /** The film's entry on the watched list (with the user's rating), if it is there. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val watchedEntry: StateFlow<WatchedTitleEntity?> = movie
        .map { it?.metadata?.tmdbId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else watched.entry(true, id) }
        .stateIn(this, null)

    fun rate(rating: Int?) {
        val id = movie.value?.metadata?.tmdbId ?: return
        viewModelScope.launch { watched.rate(true, id, rating) }
    }
}

@HiltViewModel
class SeriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LibraryRepository,
    metadataRepository: MetadataRepository,
    val streaming: StreamingRepository,
    private val watched: WatchedRepository,
) : ViewModel() {
    val seriesKey = savedStateHandle.toRoute<SeriesRoute>().seriesKey
    private val episodesFlow = repository.seriesEpisodes(seriesKey)
    val episodes: StateFlow<List<MediaWithProgress>?> = episodesFlow.stateIn(this, null)

    private val tvId = episodesFlow.map { list -> list.firstNotNullOfOrNull { it.metadata?.tmdbId } }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tmdbEpisodes = tvId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else metadataRepository.episodes(id) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tmdbSeasons = tvId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else metadataRepository.seasons(id) }

    /** TMDB episode names and texts keyed by (season, episode); empty until fetched. */
    val episodeInfo: StateFlow<Map<Pair<Int, Int>, EpisodeMetadataEntity>> = tmdbEpisodes
        .map { list -> list.associateBy { it.season to it.episode } }
        .stateIn(this, emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val whereToWatch: StateFlow<WatchState> = tvId
        .mapLatest { id -> loadWhereToWatch(streaming, isMovie = false, id) }
        .stateIn(this, WatchState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val watchedEntry: StateFlow<WatchedTitleEntity?> = tvId
        .flatMapLatest { id -> if (id == null) flowOf(null) else watched.entry(false, id) }
        .stateIn(this, null)

    /** The whole series on the watched list (or off it again). */
    fun toggleSeriesWatched() {
        val item = episodes.value?.firstOrNull { it.metadata?.tmdbId != null } ?: return
        val meta = item.metadata ?: return
        val id = meta.tmdbId ?: return
        viewModelScope.launch {
            if (watchedEntry.value != null) watched.unmark(false, id)
            else watched.markWatched(WatchedTitle(false, id, item.displayTitle, item.displayYear, meta.posterPath))
        }
    }

    fun rate(rating: Int?) {
        val id = episodes.value?.firstNotNullOfOrNull { it.metadata?.tmdbId } ?: return
        viewModelScope.launch { watched.rate(false, id, rating) }
    }

    /** Seasons, status and an announced season, live from TMDB; null until loaded or offline. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val seriesFacts: StateFlow<SeriesFacts?> = tvId
        .mapLatest { id -> id?.let { runCatching { metadataRepository.seriesFacts(it) }.getOrNull() } }
        .stateIn(this, null)

    /** Every season and episode: the library's files plus what TMDB lists but the library lacks. */
    val overview: StateFlow<List<SeasonOverview>?> =
        combine(episodesFlow, tmdbSeasons, tmdbEpisodes, SeriesOverview::build).stateIn(this, null)

    fun toggleWatched(item: MediaWithProgress) {
        viewModelScope.launch { repository.markWatched(item.media.path, !item.isWatched) }
    }
}

