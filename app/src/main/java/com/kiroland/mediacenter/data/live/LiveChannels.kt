package com.kiroland.mediacenter.data.live

/**
 * Hungarian public-service channels. Playback always happens in MTVA's own apps: this app only opens
 * the official live page there (their terms forbid extracting or re-hosting the streams).
 */
data class LiveChannel(
    val id: String,
    val name: String,
    /** The channel's live page on mediaklikk.hu, handed to the official app. */
    val pageUrl: String,
    /** ARGB colour for the tile, so channels are told apart without using broadcaster logos. */
    val color: Long,
    val sport: Boolean = false,
)

object PublicChannels {
    private const val BASE = "https://mediaklikk.hu/elo/"

    val all = listOf(
        LiveChannel("m1", "M1", BASE + "mtv1live", 0xFF1F5FA8),
        LiveChannel("m2", "M2", BASE + "mtv2live", 0xFF3E8E41),
        LiveChannel("m4", "M4 Sport", BASE + "mtv4live", 0xFFB3261E, sport = true),
        LiveChannel("m4plus", "M4 Sport+", BASE + "mtv4plus", 0xFF8C1D18, sport = true),
        LiveChannel("m5", "M5", BASE + "mtv5live", 0xFF6A4C93),
        LiveChannel("duna", "Duna", BASE + "dunalive", 0xFF1B6B73),
        LiveChannel("dunaworld", "Duna World", BASE + "dunaworldlive", 0xFF2E4A62),
    )
}

object OfficialApps {
    const val MEDIAKLIKK = "hu.mtva.mediaklikk"
    const val M4_SPORT = "me.appsters.m4"
    const val PLAY_STORE = "com.android.vending"

    /** Built-in tuner apps ("Live TV") of common TV makers, most specific first. */
    val TUNER_APPS = listOf(
        "com.mediatek.wwtv.tvcenter", // Xiaomi and other MediaTek TVs
        "com.sony.dtv.tvx",
        "com.tcl.tvinput",
        "com.google.android.tv", // Google Live Channels
        "com.android.tv",
    )
}

/** One way of opening a channel, tried in order until one works. */
sealed interface LaunchStep {
    /** The official app, straight on the channel's live page (if it handles the link). */
    data class OpenLinkInApp(val packageName: String, val url: String) : LaunchStep
    data class LaunchApp(val packageName: String) : LaunchStep
    data class OpenStorePage(val packageName: String) : LaunchStep
    /** The channel's official live page in the TV's web browser (current Médiaklikk builds do not install on older TVs). */
    data class OpenWebsite(val url: String) : LaunchStep
}

/** What a tile offers, for its caption. */
enum class LaunchMode { MEDIAKLIKK, M4_SPORT_APP, WEBSITE, INSTALL }

object LiveTvPlanner {

    fun mode(channel: LiveChannel, installed: Set<String>, hasBrowser: Boolean): LaunchMode = when {
        OfficialApps.MEDIAKLIKK in installed -> LaunchMode.MEDIAKLIKK
        channel.sport && OfficialApps.M4_SPORT in installed -> LaunchMode.M4_SPORT_APP
        hasBrowser -> LaunchMode.WEBSITE
        else -> LaunchMode.INSTALL
    }

    fun steps(channel: LiveChannel, installed: Set<String>, hasBrowser: Boolean): List<LaunchStep> =
        when (mode(channel, installed, hasBrowser)) {
            LaunchMode.MEDIAKLIKK -> listOf(
                LaunchStep.OpenLinkInApp(OfficialApps.MEDIAKLIKK, channel.pageUrl),
                LaunchStep.LaunchApp(OfficialApps.MEDIAKLIKK),
            )
            LaunchMode.M4_SPORT_APP -> listOf(LaunchStep.LaunchApp(OfficialApps.M4_SPORT))
            LaunchMode.WEBSITE -> listOf(LaunchStep.OpenWebsite(channel.pageUrl))
            LaunchMode.INSTALL -> listOf(
                LaunchStep.OpenStorePage(if (channel.sport) OfficialApps.M4_SPORT else OfficialApps.MEDIAKLIKK),
            )
        }

    fun tunerApp(installed: Set<String>): String? = OfficialApps.TUNER_APPS.firstOrNull { it in installed }
}
