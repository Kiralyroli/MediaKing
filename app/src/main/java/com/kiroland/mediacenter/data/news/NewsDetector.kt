package com.kiroland.mediacenter.data.news

enum class NewsKind {
    /** Now on one of the user's services. */
    AVAILABLE,
    /** A new season has started. */
    SEASON_OUT,
    /** A new season was announced, or got its date. */
    SEASON_ANNOUNCED,
}

/** What a check sees of one title right now. */
data class TitleState(
    /** Providers on the user's services offering it by subscription or free; null for library series (not asked). */
    val providers: Set<Int>?,
    val airedSeasons: Int?,
    val announcedSeason: Int?,
    val announcedDate: String?,
)

data class NewsEvent(val kind: NewsKind, val providerId: Int? = null, val season: Int? = null, val date: String? = null)

/** Compares a followed title with its last check. Pure, so it is unit tested. */
object NewsDetector {

    /** Nothing on the first check: what is there already is not news. */
    fun detect(previous: TitleState?, now: TitleState): List<NewsEvent> {
        if (previous == null) return emptyList()
        val events = mutableListOf<NewsEvent>()

        if (now.providers != null && previous.providers != null) {
            (now.providers - previous.providers).sorted().forEach { events += NewsEvent(NewsKind.AVAILABLE, providerId = it) }
        }

        val airedBefore = previous.airedSeasons
        val airedNow = now.airedSeasons
        if (airedBefore != null && airedNow != null && airedNow > airedBefore) {
            // Several at once (a long pause between checks): the latest says it all.
            events += NewsEvent(NewsKind.SEASON_OUT, season = airedNow)
        }

        val announced = now.announcedSeason
        if (announced != null) {
            val isNewSeason = announced != previous.announcedSeason
            val gotDate = now.announcedDate != null && now.announcedDate != previous.announcedDate
            if (isNewSeason || gotDate) events += NewsEvent(NewsKind.SEASON_ANNOUNCED, season = announced, date = now.announcedDate)
        }
        return events
    }
}
