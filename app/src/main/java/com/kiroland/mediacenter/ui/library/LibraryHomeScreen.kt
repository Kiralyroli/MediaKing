package com.kiroland.mediacenter.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kiroland.mediacenter.data.library.ScanState
import com.kiroland.mediacenter.data.library.db.MediaKind
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbImages

@Composable
fun LibraryHomeScreen(
    onOpenMovie: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onPlay: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val continueState by viewModel.continueWatching.collectAsStateWithLifecycle()
    val recentState by viewModel.recentMovies.collectAsStateWithLifecycle()
    val series by viewModel.series.collectAsStateWithLifecycle()
    // Shelves load independently; pick the initial focus only once all of them are in, or it lands on
    // whichever shelf happened to arrive first and then jumps.
    val ready = folders != null && continueState != null && recentState != null && series != null
    val continueWatching = continueState.orEmpty()
    val recentMovies = recentState.orEmpty()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    val recentSeries = series.orEmpty().sortedByDescending { it.latestAddedAt }.take(20)
    val firstItem = remember { FocusRequester() }
    // The first card of the first visible shelf gets initial focus.
    val firstShelf = when {
        !ready -> -1
        continueWatching.isNotEmpty() -> 0
        recentMovies.isNotEmpty() -> 1
        recentSeries.isNotEmpty() -> 2
        else -> -1
    }
    fun Modifier.initialFocus(shelf: Int, index: Int) =
        if (shelf == firstShelf && index == 0) focusRequester(firstItem) else this

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Kezdőlap", style = MaterialTheme.typography.headlineMedium)
                ScanStatus(scanState)
            }
        }

        if (folders?.isEmpty() == true) {
            item { EmptyLibraryHint(Modifier.padding(horizontal = 48.dp)) }
        }

        if (continueWatching.isNotEmpty()) {
            shelf("Folytatás") {
                itemsIndexed(continueWatching, key = { _, it -> it.item.media.path }) { index, entry ->
                    val media = entry.item.media
                    val isEpisode = media.kind == MediaKind.EPISODE
                    WideCard(
                        title = entry.item.displayTitle,
                        imageUrl = TmdbImages.backdrop(entry.item.metadata?.backdropPath),
                        subtitle = if (isEpisode) episodeCode(media.season, media.episode, media.episodeEnd) else entry.item.displayYear?.toString(),
                        progress = entry.item.progressFraction,
                        badge = if (entry.isNextUp) "Következő" else null,
                        onClick = { onPlay(media.path) },
                        modifier = Modifier.initialFocus(0, index),
                    )
                }
            }
        }

        if (recentMovies.isNotEmpty()) {
            shelf("Legutóbb hozzáadott filmek") {
                itemsIndexed(recentMovies, key = { _, it -> it.media.path }) { index, item ->
                    PosterCard(
                        title = item.displayTitle,
                        subtitle = item.displayYear?.toString(),
                        imageUrl = TmdbImages.poster(item.metadata?.posterPath),
                        progress = item.progressFraction,
                        watched = item.isWatched,
                        onClick = { onOpenMovie(item.media.path) },
                        modifier = Modifier.initialFocus(1, index),
                    )
                }
            }
        }

        if (recentSeries.isNotEmpty()) {
            shelf("Sorozatok") {
                itemsIndexed(recentSeries, key = { _, it -> it.seriesKey }) { index, summary ->
                    PosterCard(
                        title = summary.title,
                        imageUrl = TmdbImages.poster(summary.metadata?.posterPath),
                        subtitle = "${summary.seasonCount} évad · ${summary.episodeCount} rész",
                        watched = summary.watchedCount == summary.episodeCount,
                        onClick = { onOpenSeries(summary.seriesKey) },
                        modifier = Modifier.initialFocus(2, index),
                    )
                }
            }
        }
    }

    LaunchedEffect(firstShelf) {
        if (firstShelf >= 0) runCatching { firstItem.requestFocus() }
    }
}

@Composable
fun ScanStatus(state: ScanState) {
    if (state is ScanState.Scanning) {
        Text(
            "Médiatár frissítése… ${state.found} fájl",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun EmptyLibraryHint(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("A médiatár még üres.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Nyisd meg a Tárhelyek menüben a filmeket vagy sorozatokat tartalmazó mappát, " +
                "és válaszd a „Hozzáadás a médiatárhoz” gombot.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
