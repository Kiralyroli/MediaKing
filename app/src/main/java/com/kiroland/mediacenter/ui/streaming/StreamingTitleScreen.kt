package com.kiroland.mediacenter.ui.streaming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import com.kiroland.mediacenter.data.library.WatchedRepository
import com.kiroland.mediacenter.data.library.WatchedTitle
import com.kiroland.mediacenter.data.library.db.WatchedTitleEntity
import com.kiroland.mediacenter.ui.watched.RatingRow
import androidx.tv.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kiroland.mediacenter.data.library.LibraryRepository
import com.kiroland.mediacenter.data.library.SeriesFacts
import com.kiroland.mediacenter.data.library.db.MetadataEntity
import com.kiroland.mediacenter.data.metadata.MetadataRepository
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbImages
import com.kiroland.mediacenter.data.streaming.StreamingRepository
import com.kiroland.mediacenter.data.streaming.searchTitle
import com.kiroland.mediacenter.ui.StreamingTitleRoute
import com.kiroland.mediacenter.ui.library.Backdrop
import com.kiroland.mediacenter.ui.library.ButtonContent
import com.kiroland.mediacenter.ui.library.Poster
import com.kiroland.mediacenter.ui.library.factsLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where a title found on TMDB sits in the library, if it is there. */
sealed interface InLibrary {
    data class Movie(val path: String) : InLibrary
    data class Series(val seriesKey: String) : InLibrary
}

@HiltViewModel
class StreamingTitleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    metadata: MetadataRepository,
    library: LibraryRepository,
    val streaming: StreamingRepository,
    private val watched: WatchedRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<StreamingTitleRoute>()

    private val _details = MutableStateFlow<MetadataEntity?>(null)
    val details: StateFlow<MetadataEntity?> = _details.asStateFlow()

    val isMovie: Boolean get() = route.isMovie

    /** Seasons and what comes next (series only). */
    private val _seriesFacts = MutableStateFlow<SeriesFacts?>(null)
    val seriesFacts: StateFlow<SeriesFacts?> = _seriesFacts.asStateFlow()

    private val _whereToWatch = MutableStateFlow<WatchState>(WatchState.Loading)
    val whereToWatch: StateFlow<WatchState> = _whereToWatch.asStateFlow()

    /** The same title in the library: then its own page plays it from the drive. */
    val inLibrary: StateFlow<InLibrary?> = combine(library.movies, library.series) { movies, series ->
        if (route.isMovie) {
            movies.firstOrNull { it.metadata?.tmdbId == route.tmdbId }?.let { InLibrary.Movie(it.media.path) }
        } else {
            series.firstOrNull { it.metadata?.tmdbId == route.tmdbId }?.let { InLibrary.Series(it.seriesKey) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val inWatchlist: StateFlow<Boolean> = streaming.isInWatchlist(route.isMovie, route.tmdbId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val watchedEntry: StateFlow<WatchedTitleEntity?> = watched.entry(route.isMovie, route.tmdbId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleWatched() {
        val meta = _details.value ?: return
        viewModelScope.launch {
            if (watchedEntry.value != null) {
                watched.unmark(route.isMovie, route.tmdbId)
            } else {
                watched.markWatched(WatchedTitle(route.isMovie, route.tmdbId, meta.title ?: meta.originalTitle.orEmpty(), meta.year, meta.posterPath))
            }
        }
    }

    fun rate(rating: Int?) {
        viewModelScope.launch { watched.rate(route.isMovie, route.tmdbId, rating) }
    }

    fun toggleWatchlist() {
        val meta = _details.value ?: return
        val title = StreamingRepository.Title(route.isMovie, route.tmdbId, meta.title ?: meta.originalTitle.orEmpty(), meta.year, meta.posterPath)
        viewModelScope.launch { streaming.setInWatchlist(title, !inWatchlist.value) }
    }

    init {
        viewModelScope.launch { _details.value = runCatching { metadata.preview(route.isMovie, route.tmdbId) }.getOrNull() }
        viewModelScope.launch { _whereToWatch.value = loadWhereToWatch(streaming, route.isMovie, route.tmdbId) }
        if (!route.isMovie) viewModelScope.launch { _seriesFacts.value = runCatching { metadata.seriesFacts(route.tmdbId) }.getOrNull() }
    }
}

/** A film or series found by the streaming search: its data and where to watch it. */
@Composable
fun StreamingTitleScreen(
    onOpenMovie: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    viewModel: StreamingTitleViewModel = hiltViewModel(),
) {
    val details by viewModel.details.collectAsStateWithLifecycle()
    val whereToWatch by viewModel.whereToWatch.collectAsStateWithLifecycle()
    val inLibrary by viewModel.inLibrary.collectAsStateWithLifecycle()
    val seriesFacts by viewModel.seriesFacts.collectAsStateWithLifecycle()
    val inWatchlist by viewModel.inWatchlist.collectAsStateWithLifecycle()
    val watchedEntry by viewModel.watchedEntry.collectAsStateWithLifecycle()
    val watchedFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val meta = details ?: return
    val title = meta.title ?: meta.originalTitle.orEmpty()
    val libraryFocus = remember { FocusRequester() }

    Backdrop(TmdbImages.backdrop(meta.backdropPath)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            Poster(title, TmdbImages.poster(meta.posterPath), null)
            // Scrolls when the season row is focused below the fold.
            Column(Modifier.widthIn(max = 900.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                meta.originalTitle?.takeIf { it != title }?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(factsLine(meta.year, meta), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    meta.overview ?: "Nincs leírás.",
                    style = MaterialTheme.typography.bodyLarge,
                    // A series also lists its seasons below, so its text is kept shorter.
                    maxLines = if (viewModel.isMovie) 6 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    inLibrary?.let { found ->
                        Button(
                            onClick = {
                                when (found) {
                                    is InLibrary.Movie -> onOpenMovie(found.path)
                                    is InLibrary.Series -> onOpenSeries(found.seriesKey)
                                }
                            },
                            modifier = Modifier.focusRequester(libraryFocus),
                        ) { ButtonContent(Icons.Outlined.VideoLibrary, "Megvan a médiatárban") }
                    }
                    OutlinedButton(onClick = viewModel::toggleWatched, modifier = Modifier.focusRequester(watchedFocus)) {
                        if (watchedEntry != null) ButtonContent(Icons.Filled.CheckCircle, "Megnézve")
                        else ButtonContent(Icons.Outlined.CheckCircle, "Megnéztem")
                    }
                    // Something already seen is not "to watch" any more.
                    if (watchedEntry == null) {
                        OutlinedButton(onClick = viewModel::toggleWatchlist) {
                            if (inWatchlist) ButtonContent(Icons.Filled.Bookmark, "Megnézendő")
                            else ButtonContent(Icons.Outlined.BookmarkAdd, "Megnézendők közé")
                        }
                    }
                }
                watchedEntry?.let { RatingRow(it.rating, viewModel::rate) }
                WhereToWatch(whereToWatch, searchTitle(meta.originalTitle, title), viewModel.streaming, Modifier.padding(top = 8.dp))
                seriesFacts?.let { SeasonStrip(it, Modifier.padding(top = 4.dp)) }
                if (viewModel.isMovie) meta.director?.let { Text("Rendező: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                if (viewModel.isMovie) meta.cast?.let { Text("Szereplők: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
            }
        }
    }
    // Start on the library button when there is one, else on "watched".
    LaunchedEffect(inLibrary != null) {
        runCatching { if (inLibrary != null) libraryFocus.requestFocus() else watchedFocus.requestFocus() }
        // Focusing scrolls the button towards the middle; the title should stay in sight on arrival.
        kotlinx.coroutines.delay(50)
        scroll.scrollTo(0)
    }
}
