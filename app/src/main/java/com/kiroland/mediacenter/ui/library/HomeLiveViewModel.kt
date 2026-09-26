package com.kiroland.mediacenter.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiroland.mediacenter.data.addons.AddonRepository
import com.kiroland.mediacenter.data.epg.EpgRepository
import com.kiroland.mediacenter.data.live.PublicChannels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A channel on the home screen's "live now" tile. */
data class LiveNow(val addonId: String, val channelId: String, val name: String, val now: String?)

/** The built-in channels an add-on plays in the app, with what is on now. */
@HiltViewModel
class HomeLiveViewModel @Inject constructor(addons: AddonRepository, epg: EpgRepository) : ViewModel() {

    private val minutes = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    val channels: StateFlow<List<LiveNow>> = combine(addons.addons, epg.guides, minutes) { _, _, now ->
        PublicChannels.all.mapNotNull { channel ->
            val (addon, addonChannel) = addons.forBuiltin(channel.id) ?: return@mapNotNull null
            LiveNow(addon.id, addonChannel.id, channel.name, epg.nowNext(addon.id, addonChannel, now).first?.title)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
