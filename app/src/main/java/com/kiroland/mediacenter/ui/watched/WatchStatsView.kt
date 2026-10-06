package com.kiroland.mediacenter.ui.watched

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kiroland.mediacenter.R
import com.kiroland.mediacenter.data.library.WatchStats
import com.kiroland.mediacenter.ui.theme.TextMuted
import com.kiroland.mediacenter.util.AppLocale
import java.time.format.DateTimeFormatter

private val PanelShape = RoundedCornerShape(18.dp)

/** The watched list in numbers; laid out to fit one screen, since nothing in it takes focus. */
@Composable
fun WatchStatsView(stats: WatchStats?) {
    if (stats == null) {
        Text(stringResource(R.string.stats_loading), color = TextMuted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.height(92.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(
                stringResource(R.string.stats_this_year),
                pluralStringResource(R.plurals.movies_count, stats.moviesThisYear, stats.moviesThisYear) + " · " +
                    pluralStringResource(R.plurals.series_count, stats.seriesThisYear, stats.seriesThisYear),
            )
            Tile(stringResource(R.string.stats_total), pluralStringResource(R.plurals.titles_count, stats.total, stats.total))
            Tile(
                stringResource(R.string.stats_average),
                stats.averageRating?.let { "★ " + String.format(AppLocale.current, "%.1f", it) } ?: "–",
            )
            Tile(
                stringResource(R.string.stats_hours),
                pluralStringResource(R.plurals.hours_count, stats.hoursThisYear, stats.hoursThisYear),
                stringResource(R.string.stats_hours_hint),
            )
        }
        Row(Modifier.height(244.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(stringResource(R.string.stats_per_month), Modifier.weight(2.4f)) { MonthBars(stats.perMonth) }
            Panel(stringResource(R.string.stats_genres), Modifier.weight(1f)) {
                if (stats.topGenres.isEmpty()) Text("–", color = TextMuted)
                HorizontalBars(stats.topGenres.take(5))
            }
            Panel(stringResource(R.string.stats_ratings), Modifier.weight(1f)) {
                HorizontalBars(stats.ratings.map { (star, count) -> "★".repeat(star) to count })
            }
        }
    }
}

@Composable
private fun RowScope.Tile(label: String, value: String, hint: String? = null) {
    Column(
        Modifier.weight(1f).fillMaxHeight().clip(PanelShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label.uppercase(AppLocale.current), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Panel(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxHeight().clip(PanelShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        content()
    }
}

@Composable
private fun MonthBars(months: List<Pair<java.time.YearMonth, Int>>) {
    val max = months.maxOf { it.second }.coerceAtLeast(1)
    val format = DateTimeFormatter.ofPattern("LLLL", AppLocale.current)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        months.forEach { (month, count) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (count > 0) count.toString() else "", style = MaterialTheme.typography.labelMedium)
                Box(
                    Modifier
                        .width(16.dp)
                        .height(BarHeight * (count.toFloat() / max).coerceAtLeast(0.03f))
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (count > 0) MaterialTheme.colorScheme.primary else TextMuted.copy(alpha = 0.25f)),
                )
                Text(format.format(month).take(3), style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
            }
        }
    }
}

private val BarHeight = 130.dp

@Composable
private fun HorizontalBars(rows: List<Pair<String, Int>>) {
    val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        rows.forEach { (label, count) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row {
                    Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(count.toString(), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(TextMuted.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(count.toFloat() / max).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}
