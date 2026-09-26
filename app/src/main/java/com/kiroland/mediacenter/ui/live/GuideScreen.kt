package com.kiroland.mediacenter.ui.live

import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.kiroland.mediacenter.data.addons.AddonChannel
import com.kiroland.mediacenter.data.addons.AddonRepository
import com.kiroland.mediacenter.data.epg.EpgRepository
import com.kiroland.mediacenter.data.epg.GuideLayout
import com.kiroland.mediacenter.data.epg.Programme
import com.kiroland.mediacenter.player.PlayerActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

data class GuideRow(val addonId: String, val channel: AddonChannel, val programmes: List<Programme>)

@HiltViewModel
class GuideViewModel @Inject constructor(addons: AddonRepository, epg: EpgRepository) : ViewModel() {
    /** Channels that have guide data, in the order the add-ons list them. */
    val rows: StateFlow<List<GuideRow>> = combine(addons.addons, addons.catalog, epg.guides) { installed, catalog, guides ->
        installed.flatMap { addon ->
            catalog[addon.id].orEmpty().mapNotNull { channel ->
                val programmes = channel.epgId?.let { guides[addon.id]?.get(it) }
                if (programmes.isNullOrEmpty()) null else GuideRow(addon.id, channel, programmes)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private val MinuteWidth = 6.dp
private val ChannelColumn = 170.dp
private val RowHeight = 64.dp
private const val WINDOW_HOURS = 12

private fun Float.minutesToDp(): Dp = MinuteWidth * this

@Composable
fun GuideScreen(viewModel: GuideViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    // The window stays put while the screen is open; only the "now" line moves.
    val from = remember { GuideLayout.windowStart(System.currentTimeMillis()) }
    val to = from + WINDOW_HOURS * 60 * 60 * 1000L
    // One scroll position shared by the ruler and every row, so the time axis lines up.
    val scroll = rememberScrollState()
    var selected by remember { mutableStateOf<Pair<GuideRow, Programme>?>(null) }
    val initial = remember { FocusRequester() }

    Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
        Column(Modifier.padding(horizontal = 48.dp).height(150.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Műsorújság", style = MaterialTheme.typography.headlineMedium)
            val sel = selected
            if (sel == null) {
                Text(
                    if (rows.isEmpty()) "Nincs műsorújság. Egy kiegészítő adhat hozzá (\"epg\" mező), vagy még töltődik." else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val (row, p) = sel
                Text(p.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${row.channel.name} · ${clock(p.start)}–${clock(p.stop)}",
                    color = MaterialTheme.colorScheme.primary,
                )
                p.description?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Ruler(from, to, now, scroll)

        LazyColumn(contentPadding = PaddingValues(bottom = 36.dp)) {
            itemsIndexed(rows, key = { _, r -> r.addonId + "/" + r.channel.id }) { index, row ->
                val blocks = remember(row.programmes, from) { GuideLayout.blocks(row.programmes, from, to) }
                Row(Modifier.height(RowHeight).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChannelLabel(row.channel, Modifier.padding(start = 48.dp).width(ChannelColumn))
                    Box(Modifier.horizontalScroll(scroll)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            blocks.forEach { block ->
                                val p = block.programme
                                val onAir = p != null && p.start <= now && now < p.stop
                                GuideCell(
                                    block = block,
                                    onAir = onAir,
                                    past = block.end <= now,
                                    modifier = if (index == 0 && onAir) Modifier.focusRequester(initial) else Modifier,
                                    onFocus = { selected = p?.let { row to it } },
                                    onClick = {
                                        when {
                                            onAir -> context.startActivity(
                                                PlayerActivity.liveIntent(context, row.addonId, row.channel.id),
                                            )
                                            p != null && p.stop <= now -> Toast.makeText(context, "Ez a műsor már véget ért", Toast.LENGTH_SHORT).show()
                                            p != null -> Toast.makeText(context, "${clock(p.start)}-kor kezdődik", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(rows.isNotEmpty()) { if (rows.isNotEmpty()) runCatching { initial.requestFocus() } }
}

@Composable
private fun Ruler(from: Long, to: Long, now: Long, scroll: ScrollState) {
    Row(Modifier.fillMaxWidth().height(34.dp)) {
        Box(Modifier.width(ChannelColumn + 48.dp))
        Box(Modifier.horizontalScroll(scroll)) {
            val total = ((to - from) / 60_000f).minutesToDp()
            Box(Modifier.width(total).fillMaxHeight()) {
                var t = from
                while (t < to) {
                    Text(
                        clock(t),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.offset(x = ((t - from) / 60_000f).minutesToDp() + 6.dp, y = 4.dp),
                    )
                    t += GuideLayout.SLOT_MS
                }
                if (now in from until to) {
                    Box(
                        Modifier
                            .offset(x = ((now - from) / 60_000f).minutesToDp())
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelLabel(channel: AddonChannel, modifier: Modifier) {
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
        if (channel.logo != null) {
            AsyncImage(channel.logo, contentDescription = channel.name, contentScale = ContentScale.Fit, modifier = Modifier.height(40.dp).width(110.dp))
        } else {
            Text(channel.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun GuideCell(
    block: GuideLayout.Block,
    onAir: Boolean,
    past: Boolean,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val width = block.minutes.minutesToDp()
    val p = block.programme
    if (p == null) {
        // Holes in the schedule are not focusable: D-pad jumps over them.
        Box(Modifier.width(width).fillMaxHeight())
        return
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .padding(horizontal = 2.dp)
            .onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                onAir -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (past) 0.4f else 0.8f)
            },
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        // A few-minute slot is too narrow for text; the header shows it when focused.
        if (width < 56.dp) return@Surface
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
            Text(p.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(clock(p.start), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

private fun clock(millis: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.forLanguageTag("hu-HU")).format(java.util.Date(millis))
