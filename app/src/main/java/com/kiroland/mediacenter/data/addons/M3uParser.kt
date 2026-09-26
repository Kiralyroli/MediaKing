package com.kiroland.mediacenter.data.addons

/**
 * Extended M3U playlists (IPTV style):
 *
 * ```
 * #EXTM3U x-tvg-url="https://…/guide.xml.gz"
 * #EXTINF:-1 tvg-id="dw.de" tvg-logo="https://…/dw.png" group-title="Hírek",DW English
 * https://…/dw/index.m3u8
 * ```
 */
object M3uParser {

    data class Playlist(val channels: List<AddonChannel>, val guideUrl: String?)

    private val ATTRIBUTE = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    fun parse(text: String): Playlist {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val header = lines.firstOrNull()?.takeIf { it.startsWith("#EXTM3U") }
        val guide = header?.let { attributes(it)["x-tvg-url"] ?: attributes(it)["url-tvg"] }
            ?.split(',')?.firstOrNull()?.trim()?.takeIf { it.startsWith("http") }

        val channels = mutableListOf<AddonChannel>()
        var pending: String? = null
        for (line in lines) {
            when {
                line.startsWith("#EXTINF") -> pending = line
                line.startsWith("#") -> Unit // #EXTVLCOPT, #EXTGRP, comments
                pending != null -> {
                    if (line.startsWith("http://") || line.startsWith("https://")) channels += channel(pending, line, channels.size)
                    pending = null
                }
            }
        }
        // Ids must be unique within an add-on; playlists repeat channels in several groups.
        val seen = HashMap<String, Int>()
        return Playlist(
            channels.map { c ->
                val n = seen.merge(c.id, 1, Int::plus)!!
                if (n == 1) c else c.copy(id = "${c.id}~$n")
            },
            guide,
        )
    }

    private fun channel(info: String, url: String, index: Int): AddonChannel {
        // The title starts after the first comma outside quotes; titles may contain commas themselves.
        val split = firstCommaOutsideQuotes(info)
        val attrs = attributes(if (split >= 0) info.substring(0, split) else info)
        val title = if (split >= 0) info.substring(split + 1).trim() else ""
        val name = title.ifEmpty { attrs["tvg-name"] ?: "Csatorna ${index + 1}" }
        val epgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
        return AddonChannel(
            id = epgId ?: Integer.toHexString(url.hashCode()),
            name = name,
            group = attrs["group-title"]?.takeIf { it.isNotBlank() },
            url = url,
            logo = attrs["tvg-logo"]?.takeIf { it.startsWith("http") },
            epgId = epgId,
        )
    }

    private fun firstCommaOutsideQuotes(text: String): Int {
        var quoted = false
        text.forEachIndexed { i, ch ->
            when {
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> return i
            }
        }
        return -1
    }

    private fun attributes(text: String): Map<String, String> =
        ATTRIBUTE.findAll(text).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
}
