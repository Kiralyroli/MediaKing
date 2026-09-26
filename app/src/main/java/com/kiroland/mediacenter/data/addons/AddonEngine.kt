package com.kiroland.mediacenter.data.addons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder

/** Downloads [url] with [headers] and returns the body as text (implemented with OkHttp in the app). */
fun interface Fetcher {
    suspend fun get(url: String, headers: Map<String, String>): String
}

/** Runs an add-on's resolve steps for one channel. Pure apart from the [Fetcher]. */
class AddonEngine(private val fetcher: Fetcher) {

    private val json = Json { isLenient = true }

    /** @throws AddonException with a human-readable reason when a step fails. */
    suspend fun resolve(addon: AddonManifest, channel: AddonChannel): String {
        channel.url?.let { return it }
        var value = ""
        addon.resolve.forEachIndexed { i, step ->
            val n = i + 1
            value = when {
                step.get != null -> {
                    val url = template(step.get, channel, encode = true)
                    runCatching { fetcher.get(url, addon.headers + step.headers) }
                        .getOrElse { throw AddonException("$n. lépés: a letöltés nem sikerült (${it.message})") }
                }
                step.regex != null -> {
                    val match = Regex(step.regex, RegexOption.DOT_MATCHES_ALL).find(value)
                        ?: throw AddonException("$n. lépés: a regex nem talált egyezést")
                    match.groupValues.getOrNull(step.group)
                        ?: throw AddonException("$n. lépés: nincs ${step.group}. csoport")
                }
                step.json != null -> {
                    val root = runCatching { json.parseToJsonElement(value) }
                        .getOrElse { throw AddonException("$n. lépés: nem JSON") }
                    val found = JsonPath.parse(step.json).evaluate(root) as? JsonPrimitive
                        ?: throw AddonException("$n. lépés: nincs ilyen mező: ${step.json}")
                    found.content
                }
                step.replace != null -> Regex(step.replace).replace(value, template(step.with, channel, encode = false))
                else -> throw AddonException("$n. lépés: nincs művelet")
            }
        }
        val url = value.trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw AddonException("A kapott cím nem http(s): ${url.take(60)}")
        }
        return url
    }

    /** {id}, {name} and the channel's vars; values are URL-encoded inside URLs. */
    private fun template(text: String, channel: AddonChannel, encode: Boolean): String {
        val values = mapOf("id" to channel.id, "name" to channel.name) + channel.vars
        return Regex("""\{([A-Za-z0-9_]+)\}""").replace(text) { m ->
            val v = values[m.groupValues[1]] ?: return@replace m.value
            if (encode) URLEncoder.encode(v, "UTF-8") else v
        }
    }
}

class AddonException(message: String) : Exception(message)
