package com.kiroland.mediacenter.ui.streaming

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
fun seriesSummary(facts: SeriesFacts): String {
    val announced = facts.seasons.firstOrNull { it.second == SeasonState.ANNOUNCED }?.first
    return listOfNotNull(
        "${facts.airedSeasons} évad",
        "${facts.airedEpisodes} rész".takeIf { facts.airedEpisodes > 0 },
        facts.status,
        announced?.let { s -> "${s.number}. évad: " + (SeasonFacts.longDate(s.airDate) ?: "bejelentve") },
    ).joinToString(" · ")
}

/** One card per season: episodes and premiere; an announced one in the accent colour. Focusable so the row scrolls. */
@Composable
fun SeasonStrip(facts: SeriesFacts, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Évadok", style = MaterialTheme.typography.titleLarge)
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
                        Text("${season.number}. évad", style = MaterialTheme.typography.titleSmall)
                        Text(
                            when (state) {
                                SeasonState.ANNOUNCED -> "Bejelentve"
                                SeasonState.AIRING -> "${season.episodeCount} rész · most fut"
                                SeasonState.AIRED -> "${season.episodeCount} rész"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (announced) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.8f),
                        )
                        Text(
                            SeasonFacts.shortDate(season.airDate) ?: "dátum még nincs",
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalContentColor.current.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}
