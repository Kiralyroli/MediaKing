package com.kiroland.mediacenter.ui.streaming

import com.kiroland.mediacenter.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kiroland.mediacenter.data.library.SeasonFacts
import com.kiroland.mediacenter.data.library.SeasonState
import com.kiroland.mediacenter.data.library.SeriesFacts
import com.kiroland.mediacenter.ui.theme.OnAccent
import com.kiroland.mediacenter.ui.theme.Shapes
import com.kiroland.mediacenter.ui.theme.TextMuted
import com.kiroland.mediacenter.ui.theme.TextPrimary

/** "3 évad · 30 rész · Folytatódik · 4. évad: 2027. július 8." */
@Composable
fun seriesSummary(facts: SeriesFacts): String {
    val announced = facts.seasons.firstOrNull { it.second == SeasonState.ANNOUNCED }?.first
    return listOfNotNull(
        pluralStringResource(R.plurals.seasons_count, facts.airedSeasons, facts.airedSeasons),
        pluralStringResource(R.plurals.episodes_count, facts.airedEpisodes, facts.airedEpisodes).takeIf { facts.airedEpisodes > 0 },
        statusLabel(facts.status)?.let { stringResource(it) },
        announced?.let { s -> stringResource(R.string.season_next_on, s.number, SeasonFacts.longDate(s.airDate) ?: stringResource(R.string.season_announced)) },
    ).joinToString(" · ")
}

/** TMDB's series status in the app's language. */
@StringRes
fun statusLabel(tmdbStatus: String?): Int? = when (tmdbStatus) {
    "Returning Series" -> R.string.status_returning
    "Ended" -> R.string.status_ended
    "Canceled" -> R.string.status_canceled
    "In Production" -> R.string.status_in_production
    "Planned" -> R.string.status_planned
    "Pilot" -> R.string.status_pilot
    else -> null
}

/** One card per season: episodes and premiere; an announced one in the accent colour. Focusable so the row scrolls. */
@Composable
fun SeasonStrip(facts: SeriesFacts, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.seasons_title), style = MaterialTheme.typography.titleLarge)
        Text(seriesSummary(facts), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(facts.seasons, key = { it.first.number }) { (season, state) ->
                val announced = state == SeasonState.ANNOUNCED
                Surface(
                    onClick = {},
                    modifier = Modifier.width(150.dp),
                    shape = ClickableSurfaceDefaults.shape(Shapes.Card),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (announced) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = TextPrimary,
                        focusedContainerColor = TextPrimary,
                        focusedContentColor = OnAccent,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(stringResource(R.string.season_label, season.number), style = MaterialTheme.typography.titleSmall)
                        Text(
                            when (state) {
                                SeasonState.ANNOUNCED -> stringResource(R.string.season_announced)
                                SeasonState.AIRING -> pluralStringResource(R.plurals.episodes_count, season.episodeCount, season.episodeCount) + " · " + stringResource(R.string.season_airing)
                                SeasonState.AIRED -> pluralStringResource(R.plurals.episodes_count, season.episodeCount, season.episodeCount)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (announced) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.8f),
                        )
                        Text(
                            SeasonFacts.shortDate(season.airDate) ?: stringResource(R.string.season_no_date),
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalContentColor.current.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}
