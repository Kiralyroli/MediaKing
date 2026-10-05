package com.kiroland.mediacenter.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiroland.mediacenter.data.library.db.WatchlistEntity
import com.kiroland.mediacenter.data.settings.SettingsRepository
import com.kiroland.mediacenter.data.streaming.StreamingRepository
import com.kiroland.mediacenter.data.streaming.Subscriptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A home shelf of what is popular on one of the user's services. */
data class PopularShelf(val providerName: String, val titles: List<StreamingRepository.Title>)

/** The home screen's streaming shelves: the watchlist, and what is popular on each subscription. */
@HiltViewModel
class HomeStreamingViewModel @Inject constructor(
    private val streaming: StreamingRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val watchlist: StateFlow<List<WatchlistEntity>> =
        streaming.watchlist.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Reloaded when the subscriptions change; one shelf per subscription, in the settings' order. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val popular: StateFlow<List<PopularShelf>> = settings.settings
        .map { streaming.mySubscriptions() }
        .distinctUntilChanged()
        .mapLatest { mine ->
            Subscriptions.choices.filter { it.providerId in mine }.mapNotNull { choice ->
                streaming.popularOn(choice.providerId).takeIf { it.isNotEmpty() }?.let { PopularShelf(choice.name, it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
