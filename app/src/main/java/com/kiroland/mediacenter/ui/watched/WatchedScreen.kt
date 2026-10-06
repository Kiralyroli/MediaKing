package com.kiroland.mediacenter.ui.watched

import com.kiroland.mediacenter.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kiroland.mediacenter.data.library.WatchedRepository
import com.kiroland.mediacenter.data.library.db.WatchedTitleEntity
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbImages
import com.kiroland.mediacenter.ui.library.PillButton
import com.kiroland.mediacenter.ui.library.PosterCard
import com.kiroland.mediacenter.ui.theme.TextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class WatchedViewModel @Inject constructor(watched: WatchedRepository) : ViewModel() {
    val all: StateFlow<List<WatchedTitleEntity>?> = watched.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private enum class WatchedFilter(@StringRes val label: Int) {
    ALL(R.string.watched_filter_all),
    MOVIES(R.string.watched_filter_movies),
    SERIES(R.string.watched_filter_series),
    BEST(R.string.watched_filter_best),
}

/** Everything the user has marked as watched, newest first; opens the title's page (to re-rate it). */
@Composable
fun WatchedScreen(onOpenStreaming: (isMovie: Boolean, tmdbId: Int) -> Unit, viewModel: WatchedViewModel = hiltViewModel()) {
    val state by viewModel.all.collectAsStateWithLifecycle()
    val all = state ?: return
    var filter by rememberSaveable { mutableStateOf(WatchedFilter.ALL) }
    val shown = when (filter) {
        WatchedFilter.ALL -> all
        WatchedFilter.MOVIES -> all.filter { it.isMovie }
        WatchedFilter.SERIES -> all.filterNot { it.isMovie }
        WatchedFilter.BEST -> all.filter { it.rating != null }.sortedWith(compareByDescending<WatchedTitleEntity> { it.rating }.thenByDescending { it.watchedAt })
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.watched_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (all.isEmpty()) {
                        stringResource(R.string.watched_empty)
                    } else {
                        listOf(
                            all.count { it.isMovie }.let { pluralStringResource(R.plurals.movies_count, it, it) },
                            all.count { !it.isMovie }.let { pluralStringResource(R.plurals.series_count, it, it) },
                            all.count { it.rating != null }.let { pluralStringResource(R.plurals.rated_count, it, it) },
                        ).joinToString(" · ")
                    },
                    color = TextMuted,
                )
                if (all.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WatchedFilter.entries.forEach { f -> PillButton(stringResource(f.label), selected = f == filter, onClick = { filter = f }) }
                    }
                }
            }
        }
        items(shown, key = { it.key }) { entry ->
            PosterCard(
                title = entry.title,
                // A poster caption fits one of them: the stars if rated, else when it was watched.
                subtitle = starsText(entry.rating) ?: watchedDate(entry.watchedAt),
                imageUrl = TmdbImages.poster(entry.posterPath),
                onClick = { onOpenStreaming(entry.isMovie, entry.tmdbId) },
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}
