package com.kiroland.mediacenter.ui.search

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
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

data class SearchResults(val query: String = "", val movies: List<MediaWithProgress> = emptyList(), val series: List<SeriesSummary> = emptyList())

@HiltViewModel
class SearchViewModel @Inject constructor(repository: LibraryRepository) : ViewModel() {
    val query = MutableStateFlow("")

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
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    var fieldFocused by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Keresés", style = MaterialTheme.typography.headlineMedium)
                BasicTextField(
                    value = query,
                    onValueChange = { viewModel.query.value = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Cím, eredeti cím, szereplő…", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    },
                    modifier = Modifier
                        .width(720.dp)
                        .focusRequester(field)
                        .onFocusChanged { fieldFocused = it.isFocused }
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, if (fieldFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
                if (results.query.isNotBlank() && results.movies.isEmpty() && results.series.isEmpty()) {
                    Text("Nincs találat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (results.movies.isNotEmpty()) {
            shelf("Filmek (${results.movies.size})") {
                items(results.movies, key = { it.media.path }) { item ->
                    PosterCard(
                        title = item.displayTitle,
                        subtitle = item.displayYear?.toString(),
                        imageUrl = TmdbImages.poster(item.metadata?.posterPath),
                        progress = item.progressFraction,
                        watched = item.isWatched,
                        onClick = { onOpenMovie(item.media.path) },
                    )
                }
            }
        }
        if (results.series.isNotEmpty()) {
            shelf("Sorozatok (${results.series.size})") {
                items(results.series, key = { it.seriesKey }) { summary ->
                    PosterCard(
                        title = summary.title,
                        subtitle = "${summary.seasonCount} évad · ${summary.episodeCount} rész",
                        imageUrl = TmdbImages.poster(summary.metadata?.posterPath),
                        onClick = { onOpenSeries(summary.seriesKey) },
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
}
