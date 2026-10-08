package com.kiroland.mediacenter.util

import com.kiroland.mediacenter.R
import java.io.File
import java.util.Locale

/**
 * Serves [AppLocale.text] from the Hungarian strings.xml, since unit tests have no Android resources,
 * and makes Hungarian the app's language for formatting (dates, decimal commas, language names).
 */
object TestStrings {
    private val values: Map<String, String> by lazy {
        val xml = File("src/main/res/values/strings.xml").readText()
        Regex("""<string name="(\w+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .associate { it.groupValues[1] to unescape(it.groupValues[2]) }
    }

    fun install() {
        AppLocale.useForTests(Locale.forLanguageTag("hu"))
        AppLocale.testStrings = { id, args ->
            val name = R.string::class.java.fields.first { it.getInt(null) == id }.name
            String.format(Locale.forLanguageTag("hu"), values.getValue(name), *args)
        }
    }

    private fun unescape(s: String) = s
        .replace("\'", "'").replace("\\\"", "\"").replace("\n", "\n")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
