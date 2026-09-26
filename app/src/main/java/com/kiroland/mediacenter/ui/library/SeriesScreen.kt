package com.kiroland.mediacenter.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbImages
import com.kiroland.mediacenter.util.formatBytes
import com.kiroland.mediacenter.util.formatDuration

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesScreen(
    onPlay: (path: String) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val episodeInfo by viewModel.episodeInfo.collectAsStateWithLifecycle()
    val all = episodes ?: return
    if (all.isEmpty()) {
        Text("A sorozat epizódjai nem érhetők el.", Modifier.padding(48.dp))
        return
    }
    val meta = all.firstNotNullOfOrNull { it.metadata?.takeIf { m -> m.tmdbId != null } }
    val title = meta?.title ?: all.first().media.title
    val seasons = all.mapNotNull { it.media.season }.distinct().sorted()
    // Open on the first season that still has something unwatched.
    val defaultSeason = all.firstOrNull { !it.isWatched }?.media?.season ?: seasons.first()
    var selectedSeason by rememberSaveable { mutableStateOf(defaultSeason) }
    val inSeason = all.filter { it.media.season == selectedSeason }
    val focusIndex = inSeason.indexOfFirst { !it.isWatched }.coerceAtLeast(0)
    val episodeFocus = remember { FocusRequester() }

    Backdrop(TmdbImages.backdrop(meta?.backdropPath)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                Modifier.padding(horizontal = 56.dp).widthIn(max = 1000.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.displaySmall)
                Text(
                    listOfNotNull(
                        factsLine(meta?.year, meta).ifBlank { null },
                        "${seasons.size} évad",
                        "${all.size} rész",
                        "${all.count { it.isWatched }} megnézve",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                meta?.overview?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "Tipp: hosszan nyomva az OK gombot egy részen megnézettnek jelölöd, vagy törlöd a jelölést.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (seasons.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(seasons) { season ->
                        FilterChip(selected = season == selectedSeason, onClick = { selectedSeason = season }) {
                            Text("$season. évad")
                        }
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(inSeason, key = { _, it -> it.media.path }) { index, item ->
                    val media = item.media
                    val info = episodeInfo[(media.season ?: 0) to (media.episode ?: 0)]
                    val progress = item.progressFraction
                    ListItem(
                        selected = false,
                        onClick = { onPlay(media.path) },
                        onLongClick = { viewModel.toggleWatched(item) },
                        modifier = if (index == focusIndex) Modifier.focusRequester(episodeFocus) else Modifier,
                        headlineContent = {
                            Text(
                                "${episodeCode(media.season, media.episode, media.episodeEnd)} · ${info?.name ?: media.fileName}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    progress?.let { "megállítva: ${formatDuration(item.positionMs ?: 0)}" },
                                    info?.overview,
                                    formatBytes(media.sizeBytes).takeIf { info?.overview == null },
                                ).joinToString(" · "),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingContent = {
                            Icon(
                                if (item.isWatched) Icons.Filled.CheckCircle else Icons.Outlined.PlayCircle,
                                contentDescription = if (item.isWatched) "Megnézve" else null,
                            )
                        },
                        trailingContent = progress?.let { { ProgressStrip(it, Modifier.width(120.dp)) } },
                    )
                }
            }
        }
    }
    LaunchedEffect(selectedSeason) { runCatching { episodeFocus.requestFocus() } }
}
