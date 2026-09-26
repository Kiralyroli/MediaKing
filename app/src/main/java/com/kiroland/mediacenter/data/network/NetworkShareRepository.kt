package com.kiroland.mediacenter.data.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Sign-in for one SMB share. An empty [username] means guest access. */
@Serializable
data class NetworkShare(
    val host: String,
    val share: String,
    val username: String = "",
    val password: String = "",
    val domain: String = "",
) {
    fun matches(host: String, share: String) = this.host.equals(host, ignoreCase = true) && this.share.equals(share, ignoreCase = true)
}

/** Saved shares, in the app's private preferences (the TV has no other user to hide them from). */
@Singleton
class NetworkShareRepository @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("network_shares", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _shares = MutableStateFlow(read())
    val shares: StateFlow<List<NetworkShare>> = _shares.asStateFlow()

    fun find(host: String, share: String): NetworkShare? = _shares.value.firstOrNull { it.matches(host, share) }

    @Synchronized
    fun save(share: NetworkShare) = write(_shares.value.filterNot { it.matches(share.host, share.share) } + share)

    @Synchronized
    fun remove(host: String, share: String) = write(_shares.value.filterNot { it.matches(host, share) })

    private fun write(shares: List<NetworkShare>) {
        prefs.edit().putString(KEY, json.encodeToString(shares)).apply()
        _shares.value = shares
    }

    private fun read(): List<NetworkShare> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString<List<NetworkShare>>(it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY = "shares"
    }
}
