package com.kiroland.mediacenter.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.kiroland.mediacenter.data.library.ContinueItem
import com.kiroland.mediacenter.data.library.ScanState
import com.kiroland.mediacenter.data.library.db.MediaKind
import com.kiroland.mediacenter.data.library.db.MediaWithProgress
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbImages
import com.kiroland.mediacenter.player.PlayerActivity
import com.kiroland.mediacenter.ui.theme.Background
import com.kiroland.mediacenter.ui.theme.Shapes
import com.kiroland.mediacenter.ui.theme.Teal
import com.kiroland.mediacenter.ui.theme.TextMuted
import com.kiroland.mediacenter.ui.theme.TextPrimary
import com.kiroland.mediacenter.ui.theme.TextSecondary
import com.kiroland.mediacenter.ui.theme.TileTints
import com.kiroland.mediacenter.ui.theme.Violet

/** One of the home grid's featured items: something to continue, or something new. */
private data class Feature(
    val label: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val progress: Float?,
    val onClick: () -> Unit,
)

@Composable
fun LibraryHomeScreen(
    onOpenMovie: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onPlay: (String) -> Unit,
    onOpenLiveTv: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    liveViewModel: HomeLiveViewModel = hiltViewModel(),
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val continueState by viewModel.continueWatching.collectAsStateWithLifecycle()
    val recentState by viewModel.recentMovies.collectAsStateWithLifecycle()
    val series by viewModel.series.collectAsStateWithLifecycle()
    val live by liveViewModel.channels.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Shelves load independently; pick the initial focus only once all of them are in, or it lands on
    // whichever arrived first and then jumps.
    val ready = folders != null && continueState != null && recentState != null && series != null
    val continueWatching = continueState.orEmpty()
    val recentMovies = recentState.orEmpty()
    val recentSeries = series.orEmpty().sortedByDescending { it.latestAddedAt }.take(20)

    // The grid shows up to three things to continue, topped up with the newest films.
    val continuing = continueWatching.mapTo(HashSet()) { it.item.media.path }
    val features = (
        continueWatching.map { it.toFeature(onPlay) } +
            recentMovies.filterNot { it.media.path in continuing }.map { it.toNewFeature(onOpenMovie) }
    ).take(3)
    val shownPaths = continueWatching.take(3).map { it.item.media.path }.toSet()
    val moreToContinue = continueWatching.filterNot { it.item.media.path in shownPaths }
    val firstItem = remember { FocusRequester() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 28.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        if (scanState is ScanState.Scanning || folders?.isEmpty() == true) {
            item {
                Column(Modifier.padding(horizontal = 32.dp)) {
                    ScanStatus(scanState)
                    if (folders?.isEmpty() == true) EmptyLibraryHint()
                }
            }
        }

        if (features.isNotEmpty()) {
            item(key = "grid") {
                Row(Modifier.padding(horizontal = 32.dp).height(GridHeight), horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                    Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(GridGap)) {
                        HeroTile(features[0], Modifier.fillMaxWidth().weight(1.7f).focusRequester(firstItem))
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                            features.getOrNull(1)?.let { SmallTile(it, TileTints.Violet, Violet, Modifier.weight(1f).fillMaxHeight()) }
                                ?: Spacer(Modifier.weight(1f))
                            features.getOrNull(2)?.let { SmallTile(it, TileTints.Green, Color(0xFFA9E08F), Modifier.weight(1f).fillMaxHeight()) }
                                ?: Spacer(Modifier.weight(1f))
                        }
                    }
                    LiveTile(
                        channels = live,
                        onPlay = { context.startActivity(PlayerActivity.liveIntent(context, it.addonId, it.channelId)) },
                        onOpenLiveTv = onOpenLiveTv,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }

        if (moreToContinue.isNotEmpty()) {
            shelf("Folytatás") {
                itemsIndexed(moreToContinue, key = { _, it -> it.item.media.path }) { _, entry ->
                    val media = entry.item.media
                    WideCard(
                        title = entry.item.displayTitle,
                        imageUrl = TmdbImages.backdrop(entry.item.metadata?.backdropPath),
                        subtitle = if (media.kind == MediaKind.EPISODE) episodeCode(media.season, media.episode, media.episodeEnd) else entry.item.displayYear?.toString(),
                        progress = entry.item.progressFraction,
                        badge = if (entry.isNextUp) "Következő" else null,
                        onClick = { onPlay(media.path) },
                    )
                }
            }
        }

        if (recentMovies.isNotEmpty()) {
            shelf("Legutóbb hozzáadva") {
                itemsIndexed(recentMovies, key = { _, it -> it.media.path }) { _, item ->
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

        if (recentSeries.isNotEmpty()) {
            shelf("Sorozatok") {
                itemsIndexed(recentSeries, key = { _, it -> it.seriesKey }) { _, summary ->
                    PosterCard(
                        title = summary.title,
                        imageUrl = TmdbImages.poster(summary.metadata?.posterPath),
                        subtitle = "${summary.seasonCount} évad · ${summary.episodeCount} rész",
                        watched = summary.watchedCount == summary.episodeCount,
                        onClick = { onOpenSeries(summary.seriesKey) },
                    )
                }
            }
        }
    }

    LaunchedEffect(ready && features.isNotEmpty()) {
        if (ready && features.isNotEmpty()) runCatching { firstItem.requestFocus() }
    }
}

private val GridHeight = 320.dp
private val GridGap = 14.dp

@Composable
private fun HeroTile(feature: Feature, modifier: Modifier) {
    Tile(onClick = feature.onClick, color = TileTints.Brown, modifier = modifier) {
        if (feature.imageUrl != null) {
            AsyncImage(feature.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Background.copy(alpha = 0.95f), Background.copy(alpha = 0.1f)))))
        Column(
            Modifier.fillMaxHeight().fillMaxWidth(0.72f).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TileLabel(feature.label, MaterialTheme.colorScheme.primary, filled = true)
            Text(feature.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            feature.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, maxLines = 1) }
            Spacer(Modifier.weight(1f))
            feature.progress?.let { Bar(it) }
        }
    }
}

@Composable
private fun SmallTile(feature: Feature, tint: Color, labelColor: Color, modifier: Modifier) {
    Tile(onClick = feature.onClick, color = tint, modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TileLabel(feature.label, labelColor)
            Text(feature.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            feature.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** What is on the built-in channels now; each line starts its channel. */
@Composable
private fun LiveTile(channels: List<LiveNow>, onPlay: (LiveNow) -> Unit, onOpenLiveTv: () -> Unit, modifier: Modifier) {
    if (channels.isEmpty()) {
        Tile(onClick = onOpenLiveTv, color = TileTints.Blue, modifier = modifier) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TileLabel("Élő TV", Teal)
                Text("Csatornák és műsorújság", style = MaterialTheme.typography.headlineSmall)
                Text("Kiegészítővel a beépített lejátszóban.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        return
    }
    Column(
        modifier.background(TileTints.Blue, Shapes.Tile).padding(horizontal = 10.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TileLabel("Élő TV · most", Teal, modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))
        channels.take(5).forEach { channel ->
            Surface(
                onClick = { onPlay(channel) },
                shape = ClickableSurfaceDefaults.shape(Shapes.Card),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = TextPrimary,
                    focusedContainerColor = TextPrimary,
                    focusedContentColor = Background,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(channel.name, style = MaterialTheme.typography.titleMedium)
                    // Empty until the guide has loaded.
                    channel.now?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Bar(progress: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.2f), Shapes.Pill)) {
        Box(Modifier.fillMaxWidth(progress).height(6.dp).background(MaterialTheme.colorScheme.primary, Shapes.Pill))
    }
}

private fun ContinueItem.toFeature(onPlay: (String) -> Unit): Feature {
    val media = item.media
    val left = remaining(item)
    return Feature(
        label = if (isNextUp) "Következő rész" else "Folytatás",
        title = item.displayTitle,
        subtitle = if (media.kind == MediaKind.EPISODE) {
            listOfNotNull(episodeCode(media.season, media.episode, media.episodeEnd), left).joinToString(" · ")
        } else {
            listOfNotNull(item.displayYear?.toString(), item.metadata?.genres?.substringBefore(','), left).joinToString(" · ")
        },
        imageUrl = TmdbImages.backdrop(item.metadata?.backdropPath),
        progress = item.progressFraction,
        onClick = { onPlay(media.path) },
    )
}

private fun MediaWithProgress.toNewFeature(onOpenMovie: (String) -> Unit) = Feature(
    label = "Új",
    title = displayTitle,
    subtitle = listOfNotNull(displayYear?.toString(), metadata?.genres?.substringBefore(',')).joinToString(" · "),
    imageUrl = TmdbImages.backdrop(metadata?.backdropPath),
    progress = null,
    onClick = { onOpenMovie(media.path) },
)

private fun remaining(item: MediaWithProgress): String? {
    val position = item.positionMs ?: return null
    val duration = item.durationMs ?: return null
    val minutes = ((duration - position) / 60_000).toInt()
    if (minutes <= 0) return null
    return if (minutes >= 60) "még ${minutes / 60} ó ${minutes % 60} p" else "még $minutes p"
}

@Composable
fun ScanStatus(state: ScanState) {
    if (state is ScanState.Scanning) {
        Text(
            "Médiatár frissítése… ${state.found} fájl",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
    }
}

@Composable
fun EmptyLibraryHint(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("A médiatár még üres.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Nyisd meg a Beállítások › Tárhelyek menüben a filmeket vagy sorozatokat tartalmazó mappát, " +
                "és válaszd a „Hozzáadás a médiatárhoz” gombot.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
