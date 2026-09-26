package com.kiroland.mediacenter.data.library

import java.text.Normalizer

/**
 * Remote-friendly matching: typing on a TV keyboard is slow, so accents and case do not matter
 * ("vegso" finds "Végső állomás") and every word of the query must appear in any of the texts.
 */
object SearchMatcher {

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()

    fun matches(query: String, texts: List<String?>): Boolean {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        val haystack = texts.filterNotNull().joinToString(" ") { normalize(it) }
        return words.all { it in haystack }
    }
}
