package com.kiroland.mediacenter.ui.library

import com.kiroland.mediacenter.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.kiroland.mediacenter.data.library.db.MetadataEntity
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbImages
import com.kiroland.mediacenter.ui.streaming.WhereToWatch
import com.kiroland.mediacenter.ui.watched.RatingRow
import com.kiroland.mediacenter.data.streaming.searchTitle
import com.kiroland.mediacenter.util.formatBytes
import com.kiroland.mediacenter.util.formatDuration
import java.util.Locale

@Composable
fun MovieScreen(
    onPlay: (path: String, fromStart: Boolean) -> Unit,
    onFixMatch: (key: String, query: String) -> Unit,
    viewModel: MovieViewModel = hiltViewModel(),
) {
    val item by viewModel.movie.collectAsStateWithLifecycle()
    val whereToWatch by viewModel.whereToWatch.collectAsStateWithLifecycle()
    val watchedEntry by viewModel.watchedEntry.collectAsStateWithLifecycle()
    val movie = item ?: return
    val media = movie.media
    val meta = movie.metadata?.takeIf { it.tmdbId != null }
    val resumeAt = movie.positionMs?.takeIf { movie.progressFraction != null }
    val playFocus = remember { FocusRequester() }

    Backdrop(TmdbImages.backdrop(meta?.backdropPath)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            Poster(movie.displayTitle, TmdbImages.poster(meta?.posterPath), movie.progressFraction)

            Column(Modifier.widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(movie.displayTitle, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                meta?.originalTitle?.takeIf { it != movie.displayTitle }?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    factsLine(movie.displayYear, meta),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    movie.isWatched -> Text(stringResource(R.string.detail_watched), color = MaterialTheme.colorScheme.primary)
                    resumeAt != null -> Text(
                        stringResource(R.string.detail_paused_at, formatDuration(resumeAt), formatDuration(movie.durationMs ?: 0)),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    meta?.overview ?: stringResource(R.string.detail_no_overview_library),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { onPlay(media.path, false) }, modifier = Modifier.focusRequester(playFocus)) {
                        ButtonContent(Icons.Filled.PlayArrow, stringResource(if (resumeAt != null) R.string.detail_continue else R.string.detail_play))
                    }
                    if (resumeAt != null) {
                        OutlinedButton(onClick = { onPlay(media.path, true) }) {
                            ButtonContent(Icons.Filled.Replay, stringResource(R.string.detail_from_start))
                        }
                    }
                    OutlinedButton(onClick = { viewModel.setWatched(!movie.isWatched) }) {
                        if (movie.isWatched) ButtonContent(Icons.Outlined.RemoveDone, stringResource(R.string.detail_not_watched))
                        else ButtonContent(Icons.Outlined.CheckCircle, stringResource(R.string.detail_watched))
                    }
                    media.metadataKey?.let { key ->
                        OutlinedButton(onClick = { onFixMatch(key, media.title) }) {
                            ButtonContent(Icons.Outlined.ManageSearch, stringResource(R.string.detail_wrong_movie))
                        }
                    }
                }

                watchedEntry?.let { RatingRow(it.rating, viewModel::rate) }
                WhereToWatch(whereToWatch, searchTitle(meta?.originalTitle, movie.displayTitle), viewModel.streaming, Modifier.padding(top = 8.dp))

                Spacer(Modifier.height(8.dp))
                meta?.director?.let { Credit(stringResource(R.string.detail_director), it) }
                meta?.cast?.let { Credit(stringResource(R.string.detail_cast), it) }
                Text(
                    "${media.fileName} · ${formatBytes(media.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
}

/** "2017 · Horror, Sci-Fi · 1 ó 44 p · ★ 6,4" — whatever is known. */
@Composable
fun factsLine(year: Int?, meta: MetadataEntity?): String = listOfNotNull(
    year?.toString(),
    meta?.genres,
    meta?.runtimeMinutes?.let { if (it >= 60) stringResource(R.string.runtime_hm, it / 60, it % 60) else stringResource(R.string.runtime_m, it) },
    meta?.rating?.let { "★ " + String.format(com.kiroland.mediacenter.util.AppLocale.current, "%.1f", it) },
).joinToString(" · ")

/** Full-screen artwork, darkened towards the left and bottom so text stays readable. */
@Composable
fun Backdrop(imageUrl: String?, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            val background = MaterialTheme.colorScheme.background
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(0f to background, 0.55f to background.copy(alpha = 0.85f), 1f to background.copy(alpha = 0.35f))),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to background)),
            )
        }
        content()
    }
}

@Composable
fun Poster(title: String, imageUrl: String?, progress: Float?) {
    Box(
        Modifier
            .width(240.dp)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .background(placeholderBrush(title)),
    ) {
        if (imageUrl != null) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        progress?.let { ProgressStrip(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

@Composable
private fun Credit(label: String, names: String) {
    Text(
        stringResource(R.string.detail_label_value, label, names),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun ButtonContent(icon: ImageVector, label: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(20.dp))
    Text(label)
}
