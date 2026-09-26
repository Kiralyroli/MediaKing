package com.kiroland.mediacenter.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbImages

@Composable
fun MoviesScreen(onOpenMovie: (String) -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val movies by viewModel.movies.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val list = movies

    PosterGrid(
        title = "Filmek" + (list?.let { " (${it.size})" } ?: ""),
        header = {
            ScanStatus(scanState)
            if (folders?.isEmpty() == true) EmptyLibraryHint()
            else if (list?.isEmpty() == true) Text("Nincs film a médiatárban.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        itemCount = list?.size ?: 0,
    ) { first ->
        itemsIndexed(list.orEmpty(), key = { _, it -> it.media.path }) { index, item ->
            PosterCard(
                title = item.displayTitle,
                subtitle = item.displayYear?.toString(),
                imageUrl = TmdbImages.poster(item.metadata?.posterPath),
                progress = item.progressFraction,
                watched = item.isWatched,
                onClick = { onOpenMovie(item.media.path) },
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
}

@Composable
fun SeriesListScreen(onOpenSeries: (String) -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val series by viewModel.series.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val list = series

    PosterGrid(
        title = "Sorozatok" + (list?.let { " (${it.size})" } ?: ""),
        header = {
            ScanStatus(scanState)
            if (folders?.isEmpty() == true) EmptyLibraryHint()
            else if (list?.isEmpty() == true) Text("Nincs sorozat a médiatárban.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        itemCount = list?.size ?: 0,
    ) { first ->
        itemsIndexed(list.orEmpty(), key = { _, it -> it.seriesKey }) { index, summary ->
            PosterCard(
                title = summary.title,
                imageUrl = TmdbImages.poster(summary.metadata?.posterPath),
                subtitle = "${summary.seasonCount} évad · ${summary.episodeCount} rész",
                watched = summary.watchedCount == summary.episodeCount,
                onClick = { onOpenSeries(summary.seriesKey) },
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
}

@Composable
private fun PosterGrid(
    title: String,
    header: @Composable () -> Unit,
    itemCount: Int,
    content: LazyGridScope.(first: FocusRequester) -> Unit,
) {
    val first = remember { FocusRequester() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(PosterWidth + 8.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                header()
            }
        }
        content(first)
    }
    LaunchedEffect(itemCount > 0) {
        if (itemCount > 0) runCatching { first.requestFocus() }
    }
}
