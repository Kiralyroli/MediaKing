package com.kiroland.mediacenter.data.metadata

import com.kiroland.mediacenter.R
import com.kiroland.mediacenter.util.AppLocale
import android.content.Context
import com.kiroland.mediacenter.BuildConfig
import com.kiroland.mediacenter.data.metadata.tmdb.TmdbApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The TMDB read access token. A build without local.properties carries none, so it can also be given on the upload page;
 * a token given there wins over the one built in from local.properties.
 */
@Singleton
class TmdbCredentials @Inject constructor(
    @ApplicationContext context: Context,
    private val okHttp: OkHttpClient,
) {
    enum class Source { USER, BUILD }

    private val prefs = context.getSharedPreferences("tmdb", Context.MODE_PRIVATE)

    @Volatile
    private var stored: String = prefs.getString(KEY, null).orEmpty()

    val token: String get() = stored.ifBlank { BuildConfig.TMDB_TOKEN }
    val isConfigured: Boolean get() = token.isNotBlank()
    val source: Source?
        get() = when {
            stored.isNotBlank() -> Source.USER
            BuildConfig.TMDB_TOKEN.isNotBlank() -> Source.BUILD
            else -> null
        }

    /** Checks the token with TMDB first; a rejected token is not stored. */
    suspend fun set(token: String): Result<Unit> = runCatching {
        val candidate = token.trim()
        require(candidate.length in 20..2048 && candidate.none { it.isWhitespace() }) { AppLocale.text(R.string.tmdb_token_invalid) }
        val accepted = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(TmdbApi.BASE_URL + "authentication")
                .header("Authorization", "Bearer $candidate")
                .build()
            okHttp.newCall(request).execute().use { it.isSuccessful }
        }
        require(accepted) { "A TMDB nem fogadta el a tokent (az „API Read Access Token” kell, nem az API-kulcs)" }
        save(candidate)
    }

    fun clear() = save("")

    private fun save(value: String) {
        stored = value
        prefs.edit().putString(KEY, value).apply()
    }

    private companion object {
        const val KEY = "token"
    }
}
