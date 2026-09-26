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
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.kiroland.mediacenter.data.library.EpisodeRow
import com.kiroland.mediacenter.ui.theme.OnAccent
import com.kiroland.mediacenter.ui.theme.Shapes
import com.kiroland.mediacenter.ui.theme.SurfaceColor
import com.kiroland.mediacenter.ui.theme.TextPrimary
import androidx.compose.ui.graphics.Color
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbImages
import com.kiroland.mediacenter.util.formatBytes
import com.kiroland.mediacenter.util.formatDuration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesScreen(
    onPlay: (path: String) -> Unit,
    onFixMatch: (key: String, query: String) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val episodeInfo by viewModel.episodeInfo.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val all = episodes ?: return
    val seasons = overview ?: return
    if (all.isEmpty() || seasons.isEmpty()) {
        Text("A sorozat epizódjai nem érhetők el.", Modifier.padding(48.dp))
        return
    }
    val meta = all.firstNotNullOfOrNull { it.metadata?.takeIf { m -> m.tmdbId != null } }
    val title = meta?.title ?: all.first().media.title
    val ownedSeasons = seasons.count { !it.isMissing }
    val listedEpisodes = seasons.sumOf { it.listedEpisodes ?: it.ownedCount }
    // Open on the first season that still has something unwatched.
    val defaultSeason = all.firstOrNull { !it.isWatched }?.media?.season ?: seasons.first { !it.isMissing }.season
    var selectedSeason by rememberSaveable { mutableStateOf(defaultSeason) }
    val current = seasons.firstOrNull { it.season == selectedSeason } ?: seasons.first()
    val rows = current.rows
    val focusIndex = rows.indexOfFirst { it is EpisodeRow.Owned && !it.item.isWatched }
        .takeIf { it >= 0 } ?: rows.indexOfFirst { it is EpisodeRow.Owned }.coerceAtLeast(0)
    val episodeFocus = remember { FocusRequester() }
    val today = remember { LocalDate.now().toString() }

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
                        "$ownedSeasons évad",
                        "${all.size} rész",
                        "${all.count { it.isWatched }} megnézve",
                        // What exists beyond the library, per TMDB.
                        "összesen ${seasons.size} évad, $listedEpisodes rész".takeIf { seasons.size > ownedSeasons || listedEpisodes > all.size },
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
                all.first().media.metadataKey?.let { key ->
                    OutlinedButton(onClick = { onFixMatch(key, all.first().media.title) }, modifier = Modifier.padding(top = 4.dp)) {
                        ButtonContent(Icons.Outlined.ManageSearch, "Nem ez a sorozat?")
                    }
                }
            }

            if (seasons.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(seasons, key = { it.season }) { season ->
                        val listed = season.listedEpisodes
                        PillButton(
                            text = when {
                                season.isMissing -> "${seasonLabel(season.season)} · nincs meg"
                                listed != null && season.ownedCount < listed -> "${seasonLabel(season.season)} · ${season.ownedCount}/$listed"
                                else -> seasonLabel(season.season)
                            },
                            selected = season.season == selectedSeason,
                            onClick = { selectedSeason = season.season },
                            dimmed = season.isMissing,
                        )
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(rows, key = { _, row -> rowKey(row) }) { index, row ->
                    val focus = if (index == focusIndex) Modifier.focusRequester(episodeFocus) else Modifier
                    if (row is EpisodeRow.Missing) {
                        MissingEpisode(row, today, focus)
                        return@itemsIndexed
                    }
                    val item = (row as EpisodeRow.Owned).item
                    val media = item.media
                    val info = episodeInfo[(media.season ?: 0) to (media.episode ?: 0)]
                    val progress = item.progressFraction
                    ListItem(
                        selected = false,
                        onClick = { onPlay(media.path) },
                        onLongClick = { viewModel.toggleWatched(item) },
                        modifier = focus,
                        shape = RowShape,
                        colors = rowColors(),
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

/**
 * An episode TMDB lists but the library lacks: shown so one can see it exists, not playable.
 * Still focusable, so the D-pad can reach its title and text.
 */
@Composable
private fun MissingEpisode(row: EpisodeRow.Missing, today: String, modifier: Modifier) {
    val airDate = row.airDate
    val upcoming = airDate != null && airDate > today
    ListItem(
        selected = false,
        onClick = {},
        modifier = modifier.alpha(MISSING_ALPHA),
        shape = RowShape,
        colors = rowColors(missing = true),
        headlineContent = {
            Text(
                listOfNotNull(episodeCode(row.season, row.episode), row.info?.name).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                listOfNotNull(
                    if (upcoming) "Még nem jelent meg · ${formatAirDate(airDate)}" else "Nincs meg",
                    row.info?.overview,
                ).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Icon(if (upcoming) Icons.Outlined.Schedule else Icons.Outlined.RemoveCircleOutline, contentDescription = null)
        },
    )
}

private fun rowKey(row: EpisodeRow): String = when (row) {
    is EpisodeRow.Owned -> row.item.media.path
    is EpisodeRow.Missing -> "missing-${row.season}-${row.episode}"
}

private fun seasonLabel(season: Int) = if (season == 0) "Különkiadások" else "$season. évad"

private fun formatAirDate(isoDate: String?): String = runCatching {
    LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("yyyy. MMMM d.", Locale.forLanguageTag("hu-HU")))
}.getOrDefault(isoDate.orEmpty())

private const val MISSING_ALPHA = 0.5f

private val RowShape @Composable get() = ListItemDefaults.shape(Shapes.Card)

@Composable
private fun rowColors(missing: Boolean = false) = ListItemDefaults.colors(
    containerColor = if (missing) Color.Transparent else SurfaceColor.copy(alpha = 0.85f),
    contentColor = TextPrimary,
    focusedContainerColor = TextPrimary,
    focusedContentColor = OnAccent,
)
