package com.kiroland.mediacenter.data.settings

import android.content.Context
import com.kiroland.mediacenter.data.streaming.Regions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    /** Play the next episode when one ends. */
    val autoNextEpisode: Boolean = true,
    /** Pick subtitles automatically (forced Hungarian with Hungarian audio, full Hungarian otherwise). */
    val autoSubtitles: Boolean = true,
    /** Show the built-in tuner tile on the Live TV screen (needs an antenna). */
    val showAntenna: Boolean = false,
    /** Start the Wi-Fi upload server whenever the app starts. */
    val uploadAutoStart: Boolean = false,
    /**
     * Streaming services the user subscribes to, by TMDB provider id; null until chosen, which means
     * "the ones whose app is on this TV".
     */
    val subscriptions: Set<Int>? = null,
    /** Country for "where to watch" (ISO code); null follows the TV's region (see Regions). */
    val region: String? = null,
)

/** App preferences; small and synchronous, so SharedPreferences behind a StateFlow is enough. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val current: Settings get() = _settings.value

    /** The country "where to watch" is about. */
    val region: String get() = Regions.effective(current.region)

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putBoolean(AUTO_NEXT, next.autoNextEpisode)
            .putBoolean(AUTO_SUBTITLES, next.autoSubtitles)
            .putBoolean(SHOW_ANTENNA, next.showAntenna)
            .putBoolean(UPLOAD_AUTOSTART, next.uploadAutoStart)
            .apply {
                val subscriptions = next.subscriptions
                if (subscriptions == null) remove(SUBSCRIPTIONS) else putStringSet(SUBSCRIPTIONS, subscriptions.mapTo(HashSet()) { it.toString() })
                if (next.region == null) remove(REGION) else putString(REGION, next.region)
            }
            .apply()
        _settings.value = next
    }

    private fun read() = Settings(
        autoNextEpisode = prefs.getBoolean(AUTO_NEXT, true),
        autoSubtitles = prefs.getBoolean(AUTO_SUBTITLES, true),
        showAntenna = prefs.getBoolean(SHOW_ANTENNA, false),
        uploadAutoStart = prefs.getBoolean(UPLOAD_AUTOSTART, false),
        subscriptions = prefs.getStringSet(SUBSCRIPTIONS, null)?.mapNotNullTo(HashSet()) { it.toIntOrNull() },
        region = prefs.getString(REGION, null),
    )

    private companion object {
        const val AUTO_NEXT = "auto_next_episode"
        const val AUTO_SUBTITLES = "auto_subtitles"
        const val SHOW_ANTENNA = "show_antenna"
        const val UPLOAD_AUTOSTART = "upload_autostart"
        const val SUBSCRIPTIONS = "streaming_subscriptions"
        const val REGION = "streaming_region"
    }
}
