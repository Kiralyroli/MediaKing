package com.kiroland.mediacenter.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The last searches that led somewhere (a result was opened), newest first, kept on the TV only. */
@Singleton
class SearchHistory @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    @Synchronized
    fun add(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        write(remember(_entries.value, q))
    }

    @Synchronized
    fun clear() = write(emptyList())

    private fun write(list: List<String>) {
        // A newline cannot be typed into the search field, so it is a safe separator.
        prefs.edit().putString(KEY, list.joinToString("\n")).apply()
        _entries.value = list
    }

    private fun read(): List<String> = prefs.getString(KEY, null)?.split("\n")?.filter { it.isNotBlank() }.orEmpty()

    companion object {
        private const val KEY = "entries"
        const val MAX = 10

        /** [query] first; the same search typed differently ("Dune" / "dune ") is kept once. */
        fun remember(old: List<String>, query: String): List<String> =
            (listOf(query) + old.filterNot { it.equals(query, ignoreCase = true) }).take(MAX)
    }
}
