package com.kiroland.mediacenter.data.addons

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
    val channels: List<AddonChannel>,
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
            throw IllegalArgumentException("Nem érvényes kiegészítő-fájl: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        validate(manifest)
        manifest
    }

    private fun validate(m: AddonManifest) {
        require(ID.matches(m.id)) { "Az azonosító csak kisbetűt, számot, pontot, kötőjelet tartalmazhat: ${m.id}" }
        require(m.name.isNotBlank()) { "Hiányzik a név" }
        require(m.channels.isNotEmpty()) { "Nincs egyetlen csatorna sem" }
        val ids = m.channels.map { it.id }
        require(ids.size == ids.toSet().size) { "Ismétlődő csatorna-azonosító" }
        m.channels.forEach { c ->
            require(c.id.isNotBlank() && c.name.isNotBlank()) { "Csatorna azonosító vagy név nélkül" }
            require(c.color == null || COLOR.matches(c.color)) { "Hibás szín (${c.name}): ${c.color}" }
            require(c.url == null || isHttp(c.url)) { "A cím csak http(s) lehet (${c.name})" }
            require(c.url != null || m.resolve.isNotEmpty()) { "${c.name}: nincs se url, se resolve lépés" }
        }
        m.resolve.forEachIndexed { i, step ->
            val ops = listOfNotNull(step.get, step.regex, step.json, step.replace)
            require(ops.size == 1) { "A(z) ${i + 1}. lépésben pontosan egy művelet kell (get, regex, json vagy replace)" }
            step.get?.let { require(isHttp(it)) { "A(z) ${i + 1}. lépés címe csak http(s) lehet" } }
            // Compiled here, with the platform's regex engine, so a bad pattern fails at install time.
            step.regex?.let { runCatching { Regex(it) }.getOrElse { e -> throw IllegalArgumentException("Hibás regex a(z) ${i + 1}. lépésben: ${e.message}") } }
            step.replace?.let { runCatching { Regex(it) }.getOrElse { e -> throw IllegalArgumentException("Hibás regex a(z) ${i + 1}. lépésben: ${e.message}") } }
            step.json?.let { runCatching { JsonPath.parse(it) }.getOrElse { e -> throw IllegalArgumentException("Hibás json-út a(z) ${i + 1}. lépésben: ${e.message}") } }
        }
    }

    private fun isHttp(url: String) = url.startsWith("https://") || url.startsWith("http://")
}
