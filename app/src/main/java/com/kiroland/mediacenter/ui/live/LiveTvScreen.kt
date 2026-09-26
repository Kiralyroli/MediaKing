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
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kiroland.mediacenter.data.live.LaunchMode
import com.kiroland.mediacenter.data.live.LiveChannel
import com.kiroland.mediacenter.data.live.LiveTvLauncher
import com.kiroland.mediacenter.data.live.LiveTvPlanner
import com.kiroland.mediacenter.data.live.PublicChannels
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LiveTvViewModel @Inject constructor(private val launcher: LiveTvLauncher) : ViewModel() {
    fun installed(): Set<String> = launcher.installed()
    fun hasBrowser(): Boolean = launcher.hasBrowser()
    fun open(channel: LiveChannel) = launcher.open(channel)
    fun openTuner() = launcher.openTuner()
}

@Composable
fun LiveTvScreen(viewModel: LiveTvViewModel = hiltViewModel()) {
    val context = LocalContext.current
    // Re-check on every return: the user may have just installed Médiaklikk from the store.
    var installed by remember { mutableStateOf(viewModel.installed()) }
    val hasBrowser = remember { viewModel.hasBrowser() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { installed = viewModel.installed() }
    val tuner = LiveTvPlanner.tunerApp(installed)
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
                    "A közmédia csatornái a hivatalos Médiaklikk alkalmazásban nyílnak meg, ha telepítve van, " +
                        "különben a mediaklikk.hu élő oldalán, a TV böngészőjében.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                                caption = "Beépített tuner (MinDig TV)",
                                color = 0xFF37474F,
                                modifier = Modifier.focusRequester(firstTile),
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Közmédia", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 48.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    itemsIndexed(PublicChannels.all, key = { _, it -> it.id }) { index, channel ->
                        Tile(
                            title = channel.name,
                            caption = when (LiveTvPlanner.mode(channel, installed, hasBrowser)) {
                                LaunchMode.MEDIAKLIKK -> "Médiaklikk"
                                LaunchMode.M4_SPORT_APP -> "M4 Sport alkalmazás"
                                LaunchMode.WEBSITE -> "mediaklikk.hu – böngészőben"
                                LaunchMode.INSTALL -> "Alkalmazás telepítése"
                            },
                            color = channel.color,
                            modifier = if (tuner == null && index == 0) Modifier.focusRequester(firstTile) else Modifier,
                            onClick = {
                                if (!viewModel.open(channel)) {
                                    Toast.makeText(
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

        item {
            Text(
                "Az adást az MTVA hivatalos alkalmazása vagy weboldala játssza le; ez az app csak megnyitja a csatornát.",
                modifier = Modifier.padding(horizontal = 48.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { firstTile.requestFocus() } }
}

/** A plain coloured tile with the channel name: no broadcaster logos. */
@Composable
private fun Tile(
    title: String,
    caption: String,
    color: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    Column(modifier.width(230.dp)) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            val base = Color(color)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.55f).compositeOverBlack()))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    icon?.invoke()
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Color.compositeOverBlack(): Color = Color(red * alpha, green * alpha, blue * alpha, 1f)
