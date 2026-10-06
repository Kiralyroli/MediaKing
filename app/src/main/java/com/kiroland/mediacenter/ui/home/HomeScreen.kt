package com.kiroland.mediacenter.ui.home

import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.TaskAlt
import com.kiroland.mediacenter.ui.watched.WatchedScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kiroland.mediacenter.data.storage.StorageVolumeInfo
import com.kiroland.mediacenter.R
import com.kiroland.mediacenter.ui.addons.AddonsScreen
import com.kiroland.mediacenter.ui.diagnostics.DiagnosticsScreen
import com.kiroland.mediacenter.ui.library.LibraryHomeScreen
import com.kiroland.mediacenter.ui.live.LiveTvScreen
import com.kiroland.mediacenter.ui.search.SearchScreen
import com.kiroland.mediacenter.ui.settings.SettingsScreen
import com.kiroland.mediacenter.ui.library.MoviesScreen
import com.kiroland.mediacenter.ui.library.SeriesListScreen
import com.kiroland.mediacenter.ui.storage.StorageScreen
import com.kiroland.mediacenter.ui.transfer.TransferScreen
import com.kiroland.mediacenter.ui.theme.Background
import com.kiroland.mediacenter.ui.theme.Shapes
import com.kiroland.mediacenter.ui.theme.SidebarColor
import com.kiroland.mediacenter.ui.theme.TextMuted
import com.kiroland.mediacenter.ui.theme.TextPrimary
import com.kiroland.mediacenter.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HomeSection(@StringRes val label: Int, val icon: ImageVector, val inDrawer: Boolean = true) {
    Search(R.string.nav_search, Icons.Outlined.Search),
    Home(R.string.nav_home, Icons.Outlined.Home),
    Movies(R.string.nav_movies, Icons.Outlined.Movie),
    Series(R.string.nav_series, Icons.Outlined.Tv),
    Watched(R.string.nav_watched, Icons.Outlined.TaskAlt),
    LiveTv(R.string.nav_live_tv, Icons.Outlined.LiveTv),
    Upload(R.string.nav_upload, Icons.Outlined.CloudUpload),
    Addons(R.string.nav_addons, Icons.Outlined.Extension),
    Storage(R.string.nav_storage, Icons.Outlined.Storage, inDrawer = false),
    Settings(R.string.nav_settings, Icons.Outlined.Settings),
    // Reached from Settings, to keep the drawer short enough for a 1080p screen.
    Diagnostics(R.string.nav_diagnostics, Icons.Outlined.Info, inDrawer = false),
}

@Composable
fun HomeScreen(
    onOpenVolume: (StorageVolumeInfo) -> Unit,
    onOpenMovie: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onPlay: (String) -> Unit,
    onOpenGuide: () -> Unit,
    onOpenStreaming: (isMovie: Boolean, tmdbId: Int) -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(HomeSection.Home) }
    // Sections reached from Settings keep "Beállítások" highlighted in the drawer.
    val drawerSection = if (section.inDrawer) section else HomeSection.Settings
    // Entering the drawer lands on the current section, not on whichever item is nearest.
    val current = remember { FocusRequester() }
    Row(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(210.dp)
                .fillMaxHeight()
                .background(SidebarColor)
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .selectableGroup()
                .focusProperties { onEnter = { current.requestFocus() } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.mediaking_mark),
                    contentDescription = null,
                    modifier = Modifier.size(width = 30.dp, height = 25.dp),
                )
                Text(
                    buildAnnotatedString {
                        append("Media")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("King") }
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            HomeSection.entries.filter { it.inDrawer }.forEach { item ->
                NavPill(
                    item = item,
                    selected = item == drawerSection,
                    onClick = { section = item },
                    modifier = if (item == drawerSection) Modifier.focusRequester(current) else Modifier,
                )
            }
            Spacer(Modifier.weight(1f))
            Clock()
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (section) {
                HomeSection.Search -> SearchScreen(onOpenMovie = onOpenMovie, onOpenSeries = onOpenSeries, onOpenStreaming = onOpenStreaming)
                HomeSection.Home -> LibraryHomeScreen(
                    onOpenMovie = onOpenMovie,
                    onOpenSeries = onOpenSeries,
                    onPlay = onPlay,
                    onOpenLiveTv = { section = HomeSection.LiveTv },
                    onOpenStreaming = onOpenStreaming,
                )
                HomeSection.Movies -> MoviesScreen(onOpenMovie = onOpenMovie)
                HomeSection.Series -> SeriesListScreen(onOpenSeries = onOpenSeries)
                HomeSection.Watched -> WatchedScreen(onOpenStreaming = onOpenStreaming)
                HomeSection.LiveTv -> LiveTvScreen(onOpenGuide = onOpenGuide)
                HomeSection.Upload -> TransferScreen()
                HomeSection.Addons -> AddonsScreen()
                HomeSection.Storage -> StorageScreen(onOpenVolume = onOpenVolume)
                HomeSection.Settings -> SettingsScreen(
                    onOpenStorage = { section = HomeSection.Storage },
                    onOpenDiagnostics = { section = HomeSection.Diagnostics },
                )
                HomeSection.Diagnostics -> DiagnosticsScreen()
            }
        }
    }
}

/** A menu entry: coral when it is the open section, light when focused, plain otherwise. */
@Composable
private fun NavPill(item: HomeSection, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(39.dp),
        shape = ClickableSurfaceDefaults.shape(Shapes.Pill),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else TextSecondary,
            focusedContainerColor = TextPrimary,
            focusedContentColor = Background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(item.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(item.label), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

/** Time and date at the bottom of the menu, updated on the minute. */
@Composable
private fun Clock() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            now = System.currentTimeMillis()
        }
    }
    val locale = com.kiroland.mediacenter.util.AppLocale.current
    // "kedd, október 6." / "Tuesday, October 6" / "Dienstag, 6. Oktober"
    val datePattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd")
    Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(SimpleDateFormat("HH:mm", locale).format(Date(now)), style = MaterialTheme.typography.headlineMedium)
        Text(SimpleDateFormat(datePattern, locale).format(Date(now)), style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}
