package com.kiroland.mediacenter.data.addons

import com.kiroland.mediacenter.R
import com.kiroland.mediacenter.util.AppLocale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A declarative add-on: a list of live channels and, optionally, the steps that turn a channel into a
 * stream URL. Add-ons are data, never code: the app only performs the listed HTTP GETs and text steps.
 * Format documented in docs/addons.md.
 */
@Serializable
data class AddonManifest(
    val id: String,
    val name: String,
    val version: Int = 1,
    val description: String? = null,
    /** Default headers for every `get` step (e.g. User-Agent). */
    val headers: Map<String, String> = emptyMap(),
    val channels: List<AddonChannel> = emptyList(),
    /** An M3U playlist whose entries become channels (in addition to [channels]). */
    val playlist: String? = null,
    /** An XMLTV programme guide (optionally .gz) for now / next information; matched by `epgId`. */
    val epg: String? = null,
    /** Applied to channels without a fixed `url`. */
    val resolve: List<ResolveStep> = emptyList(),
    val stream: StreamOptions? = null,
)

@Serializable
data class AddonChannel(
    val id: String,
    val name: String,
    /** "#RRGGBB" tile colour. */
    val color: String? = null,
    /** Row title on the Live TV screen; defaults to the add-on's name. */
    val group: String? = null,
    /** Id of a built-in channel tile this channel plays for (e.g. "m1"), instead of a tile of its own. */
    val builtin: String? = null,
    /** A fixed stream URL; no resolve steps needed. */
    val url: String? = null,
    /** Tile image (URL). */
    val logo: String? = null,
    /** Channel id in the add-on's XMLTV guide. */
    val epgId: String? = null,
    /** Extra template values for the resolve steps, e.g. {"stream": "news-hd"} → {stream}. */
    val vars: Map<String, String> = emptyMap(),
)

/** Exactly one of get / regex / json / replace per step. The value flows from one step to the next. */
@Serializable
data class ResolveStep(
    /** Download a URL (templated); the response body becomes the value. */
    val get: String? = null,
    val headers: Map<String, String> = emptyMap(),
    /** Keep capture group [group] of the first match (dot matches newlines). */
    val regex: String? = null,
    val group: Int = 1,
    /** Pick a field from JSON: `playlist[file!~bumper].file`. See [JsonPath]. */
    val json: String? = null,
    /** Replace every match of this regex with [with]. */
    val replace: String? = null,
    val with: String = "",
)

@Serializable
data class StreamOptions(
    /** e.g. "application/x-mpegURL" when the URL has no telling extension. */
    val mimeType: String? = null,
    /** Headers the player sends when fetching the stream. */
    val headers: Map<String, String> = emptyMap(),
)

object AddonParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val ID = Regex("""[a-z0-9][a-z0-9._-]{1,63}""")
    private val COLOR = Regex("""#[0-9a-fA-F]{6}""")

    /** Parses and validates; the failure message is shown to the user as is. */
    fun parse(text: String): Result<AddonManifest> = runCatching {
        val manifest = try {
            json.decodeFromString(AddonManifest.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException(AppLocale.text(R.string.addon_invalid_file, e.message?.lineSequence()?.firstOrNull().orEmpty()))
        }
        validate(manifest)
        manifest
    }

    private fun validate(m: AddonManifest) {
        require(ID.matches(m.id)) { AppLocale.text(R.string.addon_bad_id, m.id) }
        require(m.name.isNotBlank()) { AppLocale.text(R.string.addon_missing_name) }
        require(m.channels.isNotEmpty() || m.playlist != null) { "Nincs egyetlen csatorna sem (se channels, se playlist)" }
        require(m.playlist == null || isHttp(m.playlist)) { AppLocale.text(R.string.addon_playlist_not_http) }
        require(m.epg == null || isHttp(m.epg)) { AppLocale.text(R.string.addon_epg_not_http) }
        val ids = m.channels.map { it.id }
        require(ids.size == ids.toSet().size) { AppLocale.text(R.string.addon_duplicate_channel) }
        m.channels.forEach { c ->
            require(c.id.isNotBlank() && c.name.isNotBlank()) { AppLocale.text(R.string.addon_channel_without_id) }
            require(c.color == null || COLOR.matches(c.color)) { AppLocale.text(R.string.addon_bad_color, c.name, c.color.orEmpty()) }
            require(c.url == null || isHttp(c.url)) { AppLocale.text(R.string.addon_url_not_http, c.name) }
            require(c.url != null || m.resolve.isNotEmpty()) { AppLocale.text(R.string.addon_no_url, c.name) }
        }
        m.resolve.forEachIndexed { i, step ->
            val ops = listOfNotNull(step.get, step.regex, step.json, step.replace)
            require(ops.size == 1) { AppLocale.text(R.string.addon_step_one_op, i + 1) }
            step.get?.let { require(isHttp(it)) { AppLocale.text(R.string.addon_step_not_http, i + 1) } }
            // Compiled here, with the platform's regex engine, so a bad pattern fails at install time.
            step.regex?.let { runCatching { Regex(it) }.getOrElse { e -> throw IllegalArgumentException(AppLocale.text(R.string.addon_step_bad_regex, i + 1, e.message.orEmpty())) } }
            step.replace?.let { runCatching { Regex(it) }.getOrElse { e -> throw IllegalArgumentException(AppLocale.text(R.string.addon_step_bad_regex, i + 1, e.message.orEmpty())) } }
            step.json?.let { runCatching { JsonPath.parse(it) }.getOrElse { e -> throw IllegalArgumentException(AppLocale.text(R.string.addon_step_bad_json_path, i + 1, e.message.orEmpty())) } }
        }
    }

    private fun isHttp(url: String) = url.startsWith("https://") || url.startsWith("http://")
}
