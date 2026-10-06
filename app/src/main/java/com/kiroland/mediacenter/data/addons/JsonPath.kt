package com.kiroland.mediacenter.data.addons

import com.kiroland.mediacenter.R
import com.kiroland.mediacenter.util.AppLocale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A deliberately small path language for picking one value out of JSON:
 *
 * - `a.b.c` — object fields
 * - `list[0]` — array index
 * - `list[type=hls]` — first element whose field equals a value
 * - `list[file!~bumper]` — first element whose field does not contain a text (`~` = contains)
 * - `list` followed by a field (`list.file`) — the first element
 */
class JsonPath private constructor(private val segments: List<Segment>) {

    private sealed interface Segment {
        data class Field(val name: String) : Segment
        data class Index(val index: Int) : Segment
        data class Filter(val field: String, val op: String, val value: String) : Segment
    }

    fun evaluate(root: JsonElement): JsonElement? {
        var current: JsonElement? = root
        for (segment in segments) {
            current = when (segment) {
                is Segment.Field -> {
                    val node = if (current is JsonArray) current.firstOrNull() else current
                    (node as? JsonObject)?.get(segment.name)
                }
                is Segment.Index -> (current as? JsonArray)?.getOrNull(segment.index)
                is Segment.Filter -> (current as? JsonArray)?.firstOrNull { matches(it, segment) }
            } ?: return null
        }
        return current
    }

    private fun matches(element: JsonElement, filter: Segment.Filter): Boolean {
        val value = ((element as? JsonObject)?.get(filter.field) as? JsonPrimitive)?.content ?: return filter.op == "!~" || filter.op == "!="
        return when (filter.op) {
            "=" -> value == filter.value
            "!=" -> value != filter.value
            "~" -> filter.value in value
            "!~" -> filter.value !in value
            else -> false
        }
    }

    companion object {
        private val FILTER = Regex("""^([A-Za-z0-9_]+)(!=|!~|=|~)(.*)$""")

        fun parse(path: String): JsonPath {
            require(path.isNotBlank()) { AppLocale.text(R.string.json_path_empty) }
            val segments = mutableListOf<Segment>()
            // Split on dots that are not inside brackets.
            var depth = 0
            val parts = mutableListOf(StringBuilder())
            for (ch in path) {
                when {
                    ch == '[' -> { depth++; parts.last().append(ch) }
                    ch == ']' -> { depth--; parts.last().append(ch) }
                    ch == '.' && depth == 0 -> parts += StringBuilder()
                    else -> parts.last().append(ch)
                }
            }
            require(depth == 0) { AppLocale.text(R.string.json_path_unclosed) }
            for (part in parts.map { it.toString() }) {
                val name = part.substringBefore('[')
                if (name.isNotEmpty()) segments += Segment.Field(name)
                Regex("""\[([^\]]*)\]""").findAll(part).forEach { m ->
                    val inner = m.groupValues[1].trim()
                    val index = inner.toIntOrNull()
                    segments += when {
                        index != null -> Segment.Index(index)
                        else -> FILTER.matchEntire(inner)?.let { Segment.Filter(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
                            ?: throw IllegalArgumentException(AppLocale.text(R.string.json_path_bad_filter, inner))
                    }
                }
                require(name.isNotEmpty() || part.startsWith("[")) { AppLocale.text(R.string.json_path_bad_part, part) }
            }
            return JsonPath(segments)
        }
    }
}
