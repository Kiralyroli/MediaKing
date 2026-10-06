package com.kiroland.mediacenter.ui.live

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.SettingsInputAntenna
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import com.kiroland.mediacenter.ui.theme.FocusBorder
import com.kiroland.mediacenter.ui.theme.Shapes
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.kiroland.mediacenter.data.live.LaunchMode
import com.kiroland.mediacenter.data.live.LiveChannel
import com.kiroland.mediacenter.data.addons.AddonChannel
import com.kiroland.mediacenter.data.addons.AddonManifest
import com.kiroland.mediacenter.data.addons.AddonRepository
import com.kiroland.mediacenter.data.live.LiveTvLauncher
import com.kiroland.mediacenter.data.live.LiveTvPlanner
import com.kiroland.mediacenter.data.live.PublicChannels
import com.kiroland.mediacenter.data.epg.EpgRepository
import com.kiroland.mediacenter.data.epg.Programme
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import com.kiroland.mediacenter.data.settings.SettingsRepository
import com.kiroland.mediacenter.player.PlayerActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import javax.inject.Inject

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val launcher: LiveTvLauncher,
    private val addonRepository: AddonRepository,
    private val epg: EpgRepository,
) : ViewModel() {
    val addons: StateFlow<List<AddonManifest>> = addonRepository.addons
    /** Channels per add-on, including those from M3U playlists. */
    val catalog: StateFlow<Map<String, List<AddonChannel>>> = addonRepository.catalog
    val guides: StateFlow<Map<String, Map<String, List<Programme>>>> = epg.guides

    fun nowNext(addonId: String, channel: AddonChannel, now: Long) = epg.nowNext(addonId, channel, now)

    fun installed(): Set<String> = launcher.installed()
    fun hasBrowser(): Boolean = launcher.hasBrowser()
    fun open(channel: LiveChannel) = launcher.open(channel)
    fun openTuner() = launcher.openTuner()

    /** The installed add-on channel that plays this built-in tile in the app's own player, if any. */
    fun addonFor(channel: LiveChannel): Pair<AddonManifest, AddonChannel>? = addonRepository.forBuiltin(channel.id)

    /** The tuner needs an antenna and a channel scan, which not every home has: off unless asked for. */
    var showAntenna: Boolean
        get() = settings.current.showAntenna
        set(value) = settings.update { it.copy(showAntenna = value) }
}

@Composable
fun LiveTvScreen(onOpenGuide: () -> Unit, viewModel: LiveTvViewModel = hiltViewModel()) {
    val context = LocalContext.current
    // Re-check on every return: the user may have just installed Médiaklikk from the store.
    var installed by remember { mutableStateOf(viewModel.installed()) }
    val hasBrowser = remember { viewModel.hasBrowser() }
    var showAntenna by remember { mutableStateOf(viewModel.showAntenna) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { installed = viewModel.installed() }
    val tuner = LiveTvPlanner.tunerApp(installed)?.takeIf { showAntenna }
    // Recomposes when add-ons are installed or removed (e.g. from the upload page).
    val installedAddons by viewModel.addons.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    // Read so tiles recompose when a guide arrives; now/next itself is computed per tile.
    val guides by viewModel.guides.collectAsStateWithLifecycle()
    // Minute clock for "now / next".
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    fun guideLines(addonId: String, channel: AddonChannel): Pair<String?, String?> {
        if (guides.isEmpty()) return null to null
        val (current, next) = viewModel.nowNext(addonId, channel, now)
        return current?.let { "Most: ${it.title}" } to next?.let { "${clock(it.start)} ${it.title}" }
    }
    val anyInApp = PublicChannels.all.any { viewModel.addonFor(it) != null }
    // Add-on channels that are not attached to a built-in tile get rows of their own.
    val builtinIds = PublicChannels.all.map { it.id }.toSet()
    val addonRows = installedAddons
        .flatMap { addon -> catalog[addon.id].orEmpty().filter { it.builtin !in builtinIds }.map { addon to it } }
        .groupBy { (addon, channel) -> channel.group ?: addon.name }
    val firstTile = remember { FocusRequester() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Élő TV", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (anyInApp) {
                        "A csatornák a beépített lejátszóban indulnak. Csatornaváltás: fel/le vagy a csatornagombok."
                    } else {
                        "A közmédia csatornái a hivatalos Médiaklikk alkalmazásban nyílnak meg, ha telepítve van, " +
                            "különben a mediaklikk.hu élő oldalán, a TV böngészőjében."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (guides.isNotEmpty()) {
                    OutlinedButton(onClick = onOpenGuide, modifier = Modifier.padding(top = 6.dp)) {
                        Icon(Icons.Outlined.CalendarViewDay, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Műsorújság")
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Közmédia", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 48.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    itemsIndexed(PublicChannels.all, key = { _, it -> it.id }) { index, channel ->
                        val viaAddon = remember(installedAddons) { viewModel.addonFor(channel) }
                        val (nowLine, nextLine) = viaAddon?.let { guideLines(it.first.id, it.second) } ?: (null to null)
                        Tile(
                            title = channel.name,
                            detail = nextLine,
                            caption = if (viaAddon != null) {
                                nowLine ?: "Lejátszás itt · ${viaAddon.first.name}"
                            } else {
                                when (LiveTvPlanner.mode(channel, installed, hasBrowser)) {
                                    LaunchMode.MEDIAKLIKK -> "Médiaklikk"
                                    LaunchMode.M4_SPORT_APP -> "M4 Sport alkalmazás"
                                    LaunchMode.WEBSITE -> "mediaklikk.hu – böngészőben"
                                    LaunchMode.INSTALL -> "Alkalmazás telepítése"
                                }
                            },
                            color = channel.color,
                            modifier = if (index == 0) Modifier.focusRequester(firstTile) else Modifier,
                            onClick = {
                                when {
                                    viaAddon != null -> context.startActivity(
                                        PlayerActivity.liveIntent(context, viaAddon.first.id, viaAddon.second.id),
                                    )
                                    !viewModel.open(channel) -> Toast.makeText(
                                        context,
                                        "Nem sikerült megnyitni: nincs Médiaklikk alkalmazás és böngésző sem.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            },
                        )
                    }
                }
            }
        }

        addonRows.forEach { (title, entries) ->
            item(key = "addon-row-$title") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 48.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        itemsIndexed(entries, key = { _, (a, c) -> a.id + "/" + c.id }) { _, (addon, channel) ->
                            val (nowLine, nextLine) = guideLines(addon.id, channel)
                            Tile(
                                title = channel.name,
                                logo = channel.logo,
                                caption = nowLine ?: "Lejátszás itt · ${addon.name}",
                                detail = nextLine,
                                color = channel.color?.let(::parseColor) ?: DEFAULT_TILE_COLOR,
                                onClick = { context.startActivity(PlayerActivity.liveIntent(context, addon.id, channel.id)) },
                            )
                        }
                    }
                }
            }
        }

        if (tuner != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Antenna", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 48.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp)) {
                        item {
                            Tile(
                                title = "TV-adás",
                                caption = "Antenna és behangolt csatornák kellenek hozzá",
                                color = 0xFF37474F,
                                onClick = {
                                    if (!viewModel.openTuner()) {
                                        Toast.makeText(context, "A TV tuner-alkalmazása nem indítható", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            ) {
                                Icon(Icons.Outlined.SettingsInputAntenna, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (LiveTvPlanner.tunerApp(installed) != null) {
                    OutlinedButton(onClick = {
                        showAntenna = !showAntenna
                        viewModel.showAntenna = showAntenna
                    }) {
                        Text(if (showAntenna) "Antennás TV-adás elrejtése" else "Antennás TV-adás megjelenítése")
                    }
                }
                if (!anyInApp) {
                    Text(
                        "Az adást az MTVA hivatalos alkalmazása vagy weboldala játssza le; ez az app csak megnyitja a csatornát.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { firstTile.requestFocus() } }
}

/** A plain coloured tile with the channel name: no broadcaster logos. */
@Composable
private fun Tile(
    title: String,
    caption: String,
    detail: String? = null,
    logo: String? = null,
    color: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    Column(modifier.width(230.dp)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = CardDefaults.shape(Shapes.Tile),
            border = CardDefaults.border(focusedBorder = FocusBorder),
            scale = CardDefaults.scale(focusedScale = 1.05f),
        ) {
            val base = Color(color)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.55f).compositeOverBlack()))),
                contentAlignment = Alignment.Center,
            ) {
                if (logo != null) {
                    AsyncImage(logo, contentDescription = title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(20.dp))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        icon?.invoke()
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun clock(millis: Long): String =
    java.text.SimpleDateFormat("HH:mm", com.kiroland.mediacenter.util.AppLocale.current).format(java.util.Date(millis))

private fun Color.compositeOverBlack(): Color = Color(red * alpha, green * alpha, blue * alpha, 1f)

private const val DEFAULT_TILE_COLOR = 0xFF455A64

/** "#RRGGBB" (validated at install time) as an opaque ARGB long. */
private fun parseColor(hex: String): Long? = hex.removePrefix("#").toLongOrNull(16)?.let { 0xFF000000 or it }
