package com.kiroland.mediacenter.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.kiroland.mediacenter.ui.theme.FocusBorder
import com.kiroland.mediacenter.ui.theme.OnAccent
import com.kiroland.mediacenter.ui.theme.Shapes
import com.kiroland.mediacenter.ui.theme.TextPrimary
import kotlin.math.absoluteValue

val PosterWidth = 150.dp
val WideCardWidth = 280.dp

/** Stand-in artwork until TMDB posters arrive: a stable two-tone gradient derived from the title. */
fun placeholderBrush(seed: String): Brush {
    val hue = (seed.lowercase().hashCode().absoluteValue % 360).toFloat()
    return Brush.linearGradient(
        listOf(Color.hsv(hue, 0.55f, 0.42f), Color.hsv((hue + 40f) % 360f, 0.65f, 0.18f)),
    )
}

@Composable
fun PosterCard(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    progress: Float? = null,
    watched: Boolean = false,
    width: Dp = PosterWidth,
) {
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f), shape = CardShape, border = CardBorder, scale = CardScale) {
            Artwork(title = title, imageUrl = imageUrl, progress = progress, watched = watched, titleLines = 4)
        }
        // The focused card scales up ~10%; keep its caption out of the way.
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun WideCard(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    progress: Float? = null,
    badge: String? = null,
) {
    Column(modifier.width(WideCardWidth), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), shape = CardShape, border = CardBorder, scale = CardScale) {
            Box {
                Artwork(title = title, imageUrl = imageUrl, progress = progress, watched = false, titleLines = 2)
                if (badge != null) {
                    Text(
                        badge,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Artwork(title: String, imageUrl: String?, progress: Float?, watched: Boolean, titleLines: Int) {
    Box(Modifier.fillMaxSize().background(placeholderBrush(title))) {
        // The gradient and title stay underneath: visible while loading, or when there is no artwork.
        Text(
            title,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = titleLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (watched) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Megnézve",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(26.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(4.dp),
                tint = Color.White,
            )
        }
        if (progress != null) {
            ProgressStrip(progress, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
fun ProgressStrip(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.Black.copy(alpha = 0.5f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** A titled horizontal row of cards, as a LazyColumn item. */
fun LazyListScope.shelf(title: String, content: LazyListScope.() -> Unit) {
    item(key = "shelf-$title") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 32.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                content = content,
            )
        }
    }
}

fun episodeCode(season: Int?, episode: Int?, episodeEnd: Int? = null): String {
    val s = (season ?: 0).toString().padStart(2, '0')
    val e = (episode ?: 0).toString().padStart(2, '0')
    val end = episodeEnd?.let { "-E" + it.toString().padStart(2, '0') }.orEmpty()
    return "S${s}E$e$end"
}

// D2 look: rounded cards with a coral ring when focused.
private val CardShape @Composable get() = CardDefaults.shape(Shapes.Card)
private val CardBorder @Composable get() = CardDefaults.border(focusedBorder = FocusBorder)
private val CardScale @Composable get() = CardDefaults.scale(focusedScale = 1.05f)

/** A big rounded block of the home grid: tinted, or artwork with a scrim. */
@Composable
fun Tile(
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(Shapes.Tile),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = color,
            focusedContainerColor = color,
            contentColor = TextPrimary,
            focusedContentColor = TextPrimary,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = FocusBorder),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Box(Modifier.fillMaxSize(), content = content)
    }
}

/** The small uppercase label on a tile ("FOLYTATÁS"), as a filled chip or plain colored text. */
@Composable
fun TileLabel(text: String, color: Color, filled: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (filled) OnAccent else color,
        modifier = if (filled) modifier.background(color, Shapes.Pill).padding(horizontal = 10.dp, vertical = 3.dp) else modifier,
    )
}

/** A pill-shaped choice (seasons, filters): coral when chosen, light when focused. */
@Composable
fun PillButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = ClickableSurfaceDefaults.shape(Shapes.Pill),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) OnAccent else TextPrimary.copy(alpha = if (dimmed) 0.55f else 1f),
            focusedContainerColor = TextPrimary,
            focusedContentColor = OnAccent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Box(Modifier.padding(horizontal = 18.dp).height(42.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}
