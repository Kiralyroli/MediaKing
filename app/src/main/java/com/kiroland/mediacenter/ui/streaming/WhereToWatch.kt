package com.kiroland.mediacenter.ui.streaming

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.kiroland.mediacenter.data.streaming.Offer
import com.kiroland.mediacenter.data.streaming.ProviderOffer
import com.kiroland.mediacenter.data.streaming.StreamingRepository
import com.kiroland.mediacenter.data.streaming.Subscriptions
import com.kiroland.mediacenter.ui.theme.OnAccent
import com.kiroland.mediacenter.ui.theme.Shapes
import com.kiroland.mediacenter.ui.theme.Success
import com.kiroland.mediacenter.ui.theme.TextMuted
import com.kiroland.mediacenter.ui.theme.TextPrimary

/** What the screen shows: still loading, unknown (no token or offline), or the offers. */
sealed interface WatchState {
    data object Loading : WatchState
    data object Unknown : WatchState
    /** [offers] sorted for the user; [mine] are their subscriptions. */
    data class Known(val offers: List<ProviderOffer>, val installed: Set<Int>, val mine: Set<Int>) : WatchState
}

/** Offers for a TMDB title, sorted for the user, with which providers' apps are on this TV. */
suspend fun loadWhereToWatch(streaming: StreamingRepository, isMovie: Boolean, tmdbId: Int?): WatchState {
    tmdbId ?: return WatchState.Unknown
    val availability = streaming.availability(isMovie, tmdbId) ?: return WatchState.Unknown
    val installed = availability.offers.filter { streaming.isAppInstalled(it.providerId) }.mapTo(HashSet()) { it.providerId }
    val mine = streaming.mySubscriptions()
    return WatchState.Known(Subscriptions.sortForUser(availability.offers, mine), installed, mine)
}

/**
 * "Where to watch" in Hungary: one pill per provider, subscriptions first; OK opens the provider's
 * app (its search for the title where it has one). Data by JustWatch through TMDB, credited.
 */
@Composable
fun WhereToWatch(state: WatchState, title: String, streaming: StreamingRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    when (state) {
        WatchState.Loading, WatchState.Unknown -> return
        is WatchState.Known -> Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Hol nézheted?", style = MaterialTheme.typography.titleLarge)
                Text("Forrás: JustWatch", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            if (state.offers.isEmpty()) {
                Text("Magyarországon most egyik streaming szolgáltatónál sem érhető el.", color = TextMuted)
                return@Column
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(state.offers, key = { it.providerId }) { offer ->
                    ProviderPill(offer, installed = offer.providerId in state.installed, included = Subscriptions.isIncluded(offer, state.mine)) {
                        val message = when (streaming.open(offer.providerId, title)) {
                            StreamingRepository.Opened.TITLE_SEARCHED, StreamingRepository.Opened.WEBSITE -> null
                            StreamingRepository.Opened.SEARCH_PAGE -> "Írd be a keresőbe: $title"
                            StreamingRepository.Opened.APP -> "Keresd meg a(z) ${offer.name} appban: $title"
                            StreamingRepository.Opened.STORE -> "A(z) ${offer.name} nincs telepítve: itt telepítheted."
                            StreamingRepository.Opened.FAILED -> "A(z) ${offer.name} nem nyitható meg ezen a TV-n."
                        }
                        message?.let { showLongToast(context.applicationContext, it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderPill(offer: ProviderOffer, installed: Boolean, included: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(Shapes.Pill),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = TextPrimary,
            focusedContainerColor = TextPrimary,
            focusedContentColor = OnAccent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            Modifier.height(56.dp).padding(start = 8.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)) {
                offer.logoUrl?.let { AsyncImage(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp)) }
            }
            Column {
                Text(offer.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // A green dot marks "included in a subscription"; the text keeps the pill's own colour,
                    // which stays readable both on the dark pill and on the light focused one.
                    if (included) Box(Modifier.size(8.dp).clip(Shapes.Pill).background(Success))
                    Text(
                        listOfNotNull(offerLabel(offer, included), "nincs telepítve".takeIf { !installed }).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun offerLabel(offer: ProviderOffer, included: Boolean): String = when (offer.best) {
    Offer.SUBSCRIPTION -> if (included) "benne van az előfizetésedben" else "előfizetéssel"
    Offer.FREE -> "ingyenes"
    Offer.RENT -> if (Offer.BUY in offer.offers) "kölcsönzés, vásárlás" else "kölcsönzés"
    Offer.BUY -> "vásárlás"
}


/**
 * A toast lasts at most ~3.5 s; the hint must stay up while the other app opens and the user starts
 * typing, so it is shown three times in a row (~10 s). Posted on the main looper with the application
 * context, so it keeps going after this screen goes to the background.
 */
private fun showLongToast(context: android.content.Context, text: String, times: Int = 3) {
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    repeat(times) { i ->
        handler.postDelayed({ Toast.makeText(context, text, Toast.LENGTH_LONG).show() }, i * 3_400L)
    }
}
