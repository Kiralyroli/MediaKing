package com.kiroland.mediacenter.data.library

import com.kiroland.mediacenter.data.library.db.EpisodeMetadataEntity
import com.kiroland.mediacenter.data.library.db.MediaEntity
import com.kiroland.mediacenter.data.library.db.MediaKind
import com.kiroland.mediacenter.data.library.db.MediaWithProgress
import com.kiroland.mediacenter.data.library.db.SeasonMetadataEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesOverviewTest {

    private fun file(season: Int, episode: Int, end: Int? = null) = MediaWithProgress(
        media = MediaEntity(
            path = "/hdd/Show/S${season}E$episode.mkv", libraryRoot = "/hdd", fileName = "S${season}E$episode.mkv",
            sizeBytes = 1, lastModified = 0, kind = MediaKind.EPISODE, title = "Show", year = null, seriesKey = "show",
            season = season, episode = episode, episodeEnd = end, addedAt = 0,
        ),
        positionMs = null, durationMs = null, finished = null, progressUpdatedAt = null,
    )

    private fun season(n: Int, count: Int) = SeasonMetadataEntity(1, n, "$n. évad", count, null, 0)
    private fun ep(s: Int, e: Int) = EpisodeMetadataEntity(1, s, e, "Rész $e", null, null, "2020-01-0$e", null)

    private fun describe(o: SeasonOverview) = o.rows.joinToString(" ") { row ->
        when (row) {
            is EpisodeRow.Owned -> "${row.episode}"
            is EpisodeRow.Missing -> "(${row.episode})"
        }
    }

    @Test
    fun `gaps and later seasons show as missing`() {
        val overview = SeriesOverview.build(
            files = listOf(file(1, 1), file(1, 3)),
            seasons = listOf(season(0, 5), season(1, 4), season(2, 3)),
            episodes = listOf(ep(1, 1), ep(1, 2), ep(1, 3), ep(1, 4)),
        )
        // No specials: none in the library.
        assertEquals(listOf(1, 2), overview.map { it.season })
        assertEquals("1 (2) 3 (4)", describe(overview[0]))
        assertEquals("Rész 2", (overview[0].rows[1] as EpisodeRow.Missing).info?.name)
        // Season 2's episode list is not loaded yet: the count alone gives placeholders.
        assertEquals("(1) (2) (3)", describe(overview[1]))
        assertTrue(overview[1].isMissing)
        assertEquals(2, overview[0].ownedCount)
    }

    @Test
    fun `a double episode file covers both numbers`() {
        val overview = SeriesOverview.build(listOf(file(1, 1, end = 2)), listOf(season(1, 3)), emptyList())
        assertEquals("1 (3)", describe(overview.single()))
    }

    @Test
    fun `without TMDB data only the files are listed`() {
        val overview = SeriesOverview.build(listOf(file(2, 5), file(2, 6)), emptyList(), emptyList())
        assertEquals("5 6", describe(overview.single()))
        assertEquals(null, overview.single().listedEpisodes)
    }
}
