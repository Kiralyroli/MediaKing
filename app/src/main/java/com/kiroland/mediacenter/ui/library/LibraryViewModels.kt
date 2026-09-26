package com.kiroland.mediacenter.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kiroland.mediacenter.data.library.ContinueItem
import com.kiroland.mediacenter.data.library.LibraryRepository
import com.kiroland.mediacenter.data.library.ScanState
import com.kiroland.mediacenter.data.library.SeriesSummary
import com.kiroland.mediacenter.data.library.db.LibraryFolderEntity
import com.kiroland.mediacenter.data.library.db.EpisodeMetadataEntity
import com.kiroland.mediacenter.data.library.db.MediaWithProgress
import com.kiroland.mediacenter.data.metadata.MetadataRepository
import com.kiroland.mediacenter.ui.MovieRoute
import com.kiroland.mediacenter.ui.SeriesRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
) : ViewModel() {
    val path = savedStateHandle.toRoute<MovieRoute>().path
    val movie: StateFlow<MediaWithProgress?> = repository.media(path).stateIn(this, null)

    fun setWatched(watched: Boolean) {
        viewModelScope.launch { repository.markWatched(path, watched) }
    }
}

@HiltViewModel
class SeriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LibraryRepository,
    metadataRepository: MetadataRepository,
) : ViewModel() {
    val seriesKey = savedStateHandle.toRoute<SeriesRoute>().seriesKey
    private val episodesFlow = repository.seriesEpisodes(seriesKey)
    val episodes: StateFlow<List<MediaWithProgress>?> = episodesFlow.stateIn(this, null)

    /** TMDB episode names and texts keyed by (season, episode); empty until fetched. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val episodeInfo: StateFlow<Map<Pair<Int, Int>, EpisodeMetadataEntity>> = episodesFlow
        .map { list -> list.firstNotNullOfOrNull { it.metadata?.tmdbId } }
        .distinctUntilChanged()
        .flatMapLatest { tvId ->
            if (tvId == null) flowOf(emptyList()) else metadataRepository.episodes(tvId)
        }
        .map { list -> list.associateBy { it.season to it.episode } }
        .stateIn(this, emptyMap())

    fun toggleWatched(item: MediaWithProgress) {
        viewModelScope.launch { repository.markWatched(item.media.path, !item.isWatched) }
    }
}
