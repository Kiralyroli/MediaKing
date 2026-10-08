package com.kiroland.mediacenter.data.subtitles

import android.content.Context
import com.kiroland.mediacenter.BuildConfig
import com.kiroland.mediacenter.R
import com.kiroland.mediacenter.util.AppLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** A subtitle OpenSubtitles offers for a film or episode. */
data class SubtitleOffer(
    val fileId: Long,
    val language: String,
    val release: String,
    val downloads: Int,
    val hearingImpaired: Boolean,
    val forced: Boolean,
    val machineTranslated: Boolean,
)

/** What to look for: a film by TMDB id, or an episode by the series' TMDB id. */
data class SubtitleQuery(val isMovie: Boolean, val tmdbId: Int, val season: Int?, val episode: Int?, val languages: List<String>)

class SubtitleException(message: String) : IOException(message)

/**
 * OpenSubtitles.com (REST API v1). The API key is the user's own (a free "API consumer" on the site),
 * given on the upload page or built in from local.properties; an account is optional and only raises the daily download limit.
 * Everything is stored on the TV only.
 */
@Singleton
class OpenSubtitles @Inject constructor(
    @param:ApplicationContext context: Context,
    private val okHttp: OkHttpClient,
) {
    private val prefs = context.getSharedPreferences("opensubtitles", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Volatile
    private var token: String? = null

    val apiKey: String get() = prefs.getString(KEY_API, null).orEmpty().ifBlank { BuildConfig.OPENSUBTITLES_KEY }
    val username: String get() = prefs.getString(KEY_USER, null).orEmpty()
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /** Checks the key (and the account, if given) with OpenSubtitles before storing them. A blank key keeps the built-in one. */
    suspend fun setAccount(apiKey: String, username: String, password: String): Result<Unit> = runCatching {
        val key = apiKey.trim().ifBlank { BuildConfig.OPENSUBTITLES_KEY }
        require(key.length in 16..128 && key.none { it.isWhitespace() }) { AppLocale.text(R.string.subs_key_invalid) }
        withContext(Dispatchers.IO) {
            get("$BASE/infos/languages", key).use { if (!it.isSuccessful) throw SubtitleException(AppLocale.text(R.string.subs_key_rejected)) }
            if (username.isNotBlank()) login(key, username.trim(), password)
        }
        prefs.edit().putString(KEY_API, apiKey.trim()).putString(KEY_USER, username.trim()).putString(KEY_PASSWORD, password).apply()
    }

    fun clear() {
        token = null
        prefs.edit().clear().apply()
    }

    /** Most downloaded first. */
    suspend fun search(query: SubtitleQuery): List<SubtitleOffer> = withContext(Dispatchers.IO) {
        val url = "$BASE/subtitles".toHttpUrl().newBuilder().apply {
            addQueryParameter("languages", query.languages.distinct().sorted().joinToString(","))
            if (query.isMovie) {
                addQueryParameter("tmdb_id", query.tmdbId.toString())
            } else {
                addQueryParameter("parent_tmdb_id", query.tmdbId.toString())
                query.season?.let { addQueryParameter("season_number", it.toString()) }
                query.episode?.let { addQueryParameter("episode_number", it.toString()) }
            }
            addQueryParameter("order_by", "download_count")
        }.build().toString()
        val body = get(url, apiKey).use { response ->
            if (!response.isSuccessful) throw SubtitleException(AppLocale.text(R.string.subs_search_failed, response.code))
            response.body.string()
        }
        json.decodeFromString<SearchResponse>(body).data.mapNotNull { entry ->
            val a = entry.attributes
            val file = a.files.firstOrNull() ?: return@mapNotNull null
            SubtitleOffer(
                fileId = file.fileId,
                language = a.language ?: return@mapNotNull null,
                release = a.release?.takeIf { it.isNotBlank() } ?: file.fileName.orEmpty(),
                downloads = a.downloadCount,
                hearingImpaired = a.hearingImpaired,
                forced = a.foreignPartsOnly,
                machineTranslated = a.machineTranslated || a.aiTranslated,
            )
        }
    }

    /** The subtitle file's bytes (SRT). */
    suspend fun download(fileId: Long): ByteArray = withContext(Dispatchers.IO) {
        val key = apiKey
        if (token == null && username.isNotBlank()) {
            runCatching { login(key, username, prefs.getString(KEY_PASSWORD, null).orEmpty()) }
        }
        val request = Request.Builder()
            .url("$BASE/download")
            .headers(key)
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .post("""{"file_id":$fileId}""".toRequestBody(JSON))
            .build()
        val link = okHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            val parsed = runCatching { json.decodeFromString<DownloadResponse>(text) }.getOrNull()
            if (!response.isSuccessful || parsed?.link == null) {
                throw SubtitleException(
                    if (response.code == 406 || response.code == 429) AppLocale.text(R.string.subs_quota)
                    else parsed?.message ?: AppLocale.text(R.string.subs_download_failed, response.code),
                )
            }
            parsed.link
        }
        okHttp.newCall(Request.Builder().url(link).header("User-Agent", USER_AGENT).build()).execute().use { response ->
            if (!response.isSuccessful) throw SubtitleException(AppLocale.text(R.string.subs_download_failed, response.code))
            val bytes = response.body.bytes()
            if (bytes.size > MAX_BYTES) throw SubtitleException(AppLocale.text(R.string.subs_download_failed, 413))
            bytes
        }
    }

    private fun login(key: String, username: String, password: String) {
        val payload = json.encodeToString(LoginRequest.serializer(), LoginRequest(username, password))
        val request = Request.Builder().url("$BASE/login").headers(key).post(payload.toRequestBody(JSON)).build()
        token = okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw SubtitleException(AppLocale.text(R.string.subs_login_failed))
            json.decodeFromString<LoginResponse>(response.body.string()).token
        }
    }

    private fun get(url: String, key: String) = okHttp.newCall(Request.Builder().url(url).headers(key).build()).execute()

    private fun Request.Builder.headers(key: String) = header("Api-Key", key).header("User-Agent", USER_AGENT).header("Accept", "application/json")

    @Serializable private data class SearchResponse(val data: List<Entry> = emptyList())
    @Serializable private data class Entry(val attributes: Attributes)
    @Serializable private data class Attributes(
        val language: String? = null,
        val release: String? = null,
        @SerialName("download_count") val downloadCount: Int = 0,
        @SerialName("hearing_impaired") val hearingImpaired: Boolean = false,
        @SerialName("foreign_parts_only") val foreignPartsOnly: Boolean = false,
        @SerialName("machine_translated") val machineTranslated: Boolean = false,
        @SerialName("ai_translated") val aiTranslated: Boolean = false,
        val files: List<FileEntry> = emptyList(),
    )
    @Serializable private data class FileEntry(@SerialName("file_id") val fileId: Long, @SerialName("file_name") val fileName: String? = null)
    @Serializable private data class DownloadResponse(val link: String? = null, val message: String? = null)
    @Serializable private data class LoginRequest(val username: String, val password: String)
    @Serializable private data class LoginResponse(val token: String)

    companion object {
        private const val BASE = "https://api.opensubtitles.com/api/v1"
        private val USER_AGENT = "MediaKing v${BuildConfig.VERSION_NAME}"
        private val JSON = "application/json".toMediaType()
        private const val MAX_BYTES = 2 * 1024 * 1024
        private const val KEY_API = "api_key"
        private const val KEY_USER = "username"
        private const val KEY_PASSWORD = "password"
    }
}
