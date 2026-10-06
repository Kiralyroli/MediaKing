package com.kiroland.mediacenter.ui.search

import kotlinx.coroutines.delay
import com.kiroland.mediacenter.data.remote.RemoteControl
import com.kiroland.mediacenter.ui.library.seasonsAndEpisodes
import com.kiroland.mediacenter.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kiroland.mediacenter.data.library.LibraryRepository
import com.kiroland.mediacenter.data.library.SearchMatcher
import com.kiroland.mediacenter.data.library.SeriesSummary
import com.kiroland.mediacenter.data.library.db.MediaWithProgress
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbImages
import com.kiroland.mediacenter.ui.library.PosterCard
import com.kiroland.mediacenter.ui.library.shelf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import com.kiroland.mediacenter.data.streaming.StreamingRepository
import com.kiroland.mediacenter.data.streaming.BrowseGenre
import com.kiroland.mediacenter.data.streaming.Genres
import com.kiroland.mediacenter.data.settings.SearchHistory
import com.kiroland.mediacenter.ui.library.PillButton
import androidx.compose.foundation.lazy.LazyRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

data class SearchResults(val query: String = "", val movies: List<MediaWithProgress> = emptyList(), val series: List<SeriesSummary> = emptyList())

@HiltViewModel
class SearchViewModel @Inject constructor(
    repository: LibraryRepository,
    private val streaming: StreamingRepository,
    private val history: SearchHistory,
) : ViewModel() {
    val query = MutableStateFlow("")

    val recentSearches: StateFlow<List<String>> = history.entries

    /** A result was opened: the search was worth remembering. */
    fun rememberSearch() = history.add(query.value)

    fun clearHistory() = history.clear()

    /** The genre picked for browsing (while the field is empty), and its titles on the user's services. */
    val genre = MutableStateFlow<BrowseGenre?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val genreResults: StateFlow<List<StreamingRepository.Title>> = genre
        .mapLatest { g -> g?.let { streaming.byGenre(it) }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** TMDB's films and series for the query, for "where to watch"; asked after a longer pause in typing. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val streamingResults: StateFlow<List<StreamingRepository.Title>> = query
        .debounce(600)
        .map { it.trim() }
        .distinctUntilChanged()
        .mapLatest { q -> if (q.length < 2) emptyList() else streaming.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(FlowPreview::class)
    val results: StateFlow<SearchResults> =
        combine(query.debounce(250), repository.movies, repository.series) { q, movies, series ->
            if (q.isBlank()) return@combine SearchResults(q)
            SearchResults(
                query = q,
                movies = movies.filter { m ->
                    SearchMatcher.matches(q, listOf(m.displayTitle, m.media.title, m.metadata?.originalTitle, m.media.fileName, m.metadata?.cast, m.metadata?.director))
                },
                series = series.filter { s ->
                    SearchMatcher.matches(q, listOf(s.title, s.metadata?.originalTitle, s.metadata?.cast))
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())
}

@Composable
fun SearchScreen(
    onOpenMovie: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenStreaming: (isMovie: Boolean, tmdbId: Int) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val streamingResults by viewModel.streamingResults.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val genre by viewModel.genre.collectAsStateWithLifecycle()
    val genreName = genre?.let { stringResource(it.name) }.orEmpty()
    val genreResults by viewModel.genreResults.collectAsStateWithLifecycle()
    var fieldFocused by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    // The result that was opened, to put focus back on it when coming back.
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    val openedCard = remember { FocusRequester() }
    val cardModifier = { key: String -> if (key == opened) Modifier.focusRequester(openedCard) else Modifier }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // A text field keeps D-pad focus on TV; leave it towards the results explicitly.
    val toResults = {
        keyboard?.hide()
        focusManager.moveFocus(FocusDirection.Down)
    }
    // A search typed on the phone remote: no on-screen keyboard, focus goes to the results.
    var jumpToResults by remember { mutableStateOf(false) }
    val remoteSearch by RemoteControl.searchRequests.collectAsStateWithLifecycle()
    LaunchedEffect(remoteSearch) {
        RemoteControl.takeSearch()?.let {
            viewModel.query.value = it
            viewModel.rememberSearch()
            jumpToResults = true
            keyboard?.hide()
        }
    }
    LaunchedEffect(jumpToResults, results, streamingResults) {
        if (!jumpToResults || (results.movies.isEmpty() && results.series.isEmpty() && streamingResults.isEmpty())) return@LaunchedEffect
        delay(300) // the shelves lay out first
        runCatching { field.requestFocus() }
        toResults()
        jumpToResults = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.search_title), style = MaterialTheme.typography.headlineMedium)
                BasicTextField(
                    value = query,
                    onValueChange = { viewModel.query.value = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { toResults() }),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    },
                    modifier = Modifier
                        .width(720.dp)
                        .focusRequester(field)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) toResults() else false
                        }
                        .onFocusChanged { fieldFocused = it.isFocused }
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, if (fieldFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
                if (results.query.isNotBlank() && results.movies.isEmpty() && results.series.isEmpty()) {
                    Text(stringResource(R.string.search_no_library_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (results.movies.isNotEmpty()) {
            shelf(R.string.search_movies, results.movies.size) {
                items(results.movies, key = { it.media.path }) { item ->
                    PosterCard(
                        title = item.displayTitle,
                        subtitle = item.displayYear?.toString(),
                        imageUrl = TmdbImages.poster(item.metadata?.posterPath),
                        progress = item.progressFraction,
                        watched = item.isWatched,
                        onClick = { opened = item.media.path; viewModel.rememberSearch(); onOpenMovie(item.media.path) },
                        modifier = cardModifier(item.media.path),
                    )
                }
            }
        }
        if (results.series.isNotEmpty()) {
            shelf(R.string.search_series, results.series.size) {
                items(results.series, key = { it.seriesKey }) { summary ->
                    PosterCard(
                        title = summary.title,
                        subtitle = seasonsAndEpisodes(summary.seasonCount, summary.episodeCount),
                        imageUrl = TmdbImages.poster(summary.metadata?.posterPath),
                        onClick = { opened = summary.seriesKey; viewModel.rememberSearch(); onOpenSeries(summary.seriesKey) },
                        modifier = cardModifier(summary.seriesKey),
                    )
                }
            }
        }
        if (streamingResults.isNotEmpty()) {
            shelf(R.string.search_streaming) {
                items(streamingResults, key = { (if (it.isMovie) "m" else "t") + it.tmdbId }) { title ->
                    val key = "stream:" + (if (title.isMovie) "m" else "t") + title.tmdbId
                    PosterCard(
                        title = title.title,
                        subtitle = listOfNotNull(stringResource(if (title.isMovie) R.string.kind_movie else R.string.kind_series), title.year?.toString()).joinToString(" · "),
                        imageUrl = TmdbImages.poster(title.posterPath),
                        onClick = { opened = key; viewModel.rememberSearch(); onOpenStreaming(title.isMovie, title.tmdbId) },
                        modifier = cardModifier(key),
                    )
                }
            }
        }

        // With an empty field: recent searches, and browsing by genre on the user's services.
        if (query.isBlank()) {
            if (recentSearches.isNotEmpty()) {
                item(key = "recent") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.search_recent), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 48.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(recentSearches, key = { it }) { q ->
                                PillButton(q, selected = false, onClick = { viewModel.query.value = q })
                            }
                            item(key = "clear") { PillButton(stringResource(R.string.search_clear_history), selected = false, dimmed = true, onClick = viewModel::clearHistory) }
                        }
                    }
                }
            }
            item(key = "genres") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.search_browse_genres), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 48.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(Genres.all, key = { it.name }) { g ->
                            PillButton(stringResource(g.name), selected = g == genre, onClick = { viewModel.genre.value = if (g == genre) null else g })
                        }
                    }
                }
            }
            val picked = genre
            if (picked != null && genreResults.isNotEmpty()) {
                shelf(R.string.search_genre_on_services, genreName) {
                    items(genreResults, key = { "g" + (if (it.isMovie) "m" else "t") + it.tmdbId }) { title ->
                        val key = "genre:" + (if (title.isMovie) "m" else "t") + title.tmdbId
                        PosterCard(
                            title = title.title,
                            subtitle = listOfNotNull(stringResource(if (title.isMovie) R.string.kind_movie else R.string.kind_series), title.year?.toString()).joinToString(" · "),
                            imageUrl = TmdbImages.poster(title.posterPath),
                            onClick = { opened = key; onOpenStreaming(title.isMovie, title.tmdbId) },
                            modifier = cardModifier(key),
                        )
                    }
                }
            }
        }
    }
    // Only on a fresh search; coming back from a result focuses that result instead.
    LaunchedEffect(Unit) { if (query.isEmpty() && !jumpToResults) runCatching { field.requestFocus() } }
    LaunchedEffect(results, streamingResults, genreResults) {
        val key = opened ?: return@LaunchedEffect
        val streamingKeys = streamingResults.map { "stream:" + (if (it.isMovie) "m" else "t") + it.tmdbId } +
            genreResults.map { "genre:" + (if (it.isMovie) "m" else "t") + it.tmdbId }
        if (results.movies.any { it.media.path == key } || results.series.any { it.seriesKey == key } || key in streamingKeys) {
            runCatching { openedCard.requestFocus() }
            opened = null
        }
    }
}
