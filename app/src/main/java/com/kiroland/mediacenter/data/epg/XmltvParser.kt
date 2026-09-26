package com.kiroland.mediacenter.data.epg

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class Programme(val start: Long, val stop: Long, val title: String, val description: String? = null)

/**
 * Streaming XMLTV reader. Guides for a whole country run to tens of MB, so only programmes of [wanted]
 * channels that overlap [from]..[to] are kept, and nothing else is held in memory.
 */
object XmltvParser {

    private val TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun parse(input: InputStream, wanted: Set<String>, from: Long, to: Long, language: String = "hu"): Map<String, List<Programme>> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, null)
        val result = HashMap<String, MutableList<Programme>>()

        var channel: String? = null
        var start = 0L
        var stop = 0L
        var titles = mutableMapOf<String?, String>()
        var descriptions = mutableMapOf<String?, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "programme" -> {
                        val id = parser.getAttributeValue(null, "channel")
                        channel = id?.takeIf { it in wanted }
                        start = time(parser.getAttributeValue(null, "start"))
                        stop = time(parser.getAttributeValue(null, "stop"))
                        titles = mutableMapOf()
                        descriptions = mutableMapOf()
                    }
                    "title" -> if (channel != null) titles[parser.getAttributeValue(null, "lang")] = parser.nextText().trim()
                    "desc" -> if (channel != null) descriptions[parser.getAttributeValue(null, "lang")] = parser.nextText().trim()
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "programme") {
                val id = channel
                if (id != null && start > 0 && stop > start && stop > from && start < to) {
                    val title = titles[language] ?: titles.values.firstOrNull()
                    if (!title.isNullOrBlank()) {
                        result.getOrPut(id) { mutableListOf() } += Programme(start, stop, title, descriptions[language] ?: descriptions.values.firstOrNull())
                    }
                }
                channel = null
            }
            event = parser.next()
        }
        return result.mapValues { (_, list) -> list.sortedBy { it.start } }
    }

    /** "20260926201500 +0200" (offset optional, UTC then) → epoch millis; 0 when unreadable. */
    fun time(value: String?): Long {
        if (value == null || value.length < 14) return 0
        return runCatching {
            val local = LocalDateTime.parse(value.substring(0, 14), TIME)
            val offsetText = value.substring(14).trim()
            val offset = if (offsetText.length == 5) ZoneOffset.of(offsetText.substring(0, 3) + ":" + offsetText.substring(3)) else ZoneOffset.UTC
            local.toInstant(offset).toEpochMilli()
        }.getOrDefault(0)
    }

    /** What is on at [now], and what comes after it. */
    fun nowNext(programmes: List<Programme>?, now: Long): Pair<Programme?, Programme?> {
        if (programmes.isNullOrEmpty()) return null to null
        val index = programmes.indexOfFirst { now >= it.start && now < it.stop }
        if (index >= 0) return programmes[index] to programmes.getOrNull(index + 1)
        return null to programmes.firstOrNull { it.start > now }
    }
}
