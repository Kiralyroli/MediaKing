package com.kiroland.mediacenter.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import com.kiroland.mediacenter.data.storage.StorageVolumeInfo
import com.kiroland.mediacenter.ui.addons.AddonsScreen
import com.kiroland.mediacenter.ui.diagnostics.DiagnosticsScreen
import com.kiroland.mediacenter.ui.library.LibraryHomeScreen
import com.kiroland.mediacenter.ui.live.LiveTvScreen
import com.kiroland.mediacenter.ui.library.MoviesScreen
import com.kiroland.mediacenter.ui.library.SeriesListScreen
import com.kiroland.mediacenter.ui.storage.StorageScreen
import com.kiroland.mediacenter.ui.transfer.TransferScreen

private enum class HomeSection(val label: String, val icon: ImageVector) {
    Home("Kezdőlap", Icons.Outlined.Home),
    Movies("Filmek", Icons.Outlined.Movie),
    Series("Sorozatok", Icons.Outlined.Tv),
    LiveTv("Élő TV", Icons.Outlined.LiveTv),
    Upload("Feltöltés", Icons.Outlined.CloudUpload),
    Addons("Kiegészítők", Icons.Outlined.Extension),
    Storage("Tárhelyek", Icons.Outlined.Storage),
    Diagnostics("Diagnosztika", Icons.Outlined.Info),
}

@Composable
fun HomeScreen(
    onOpenVolume: (StorageVolumeInfo) -> Unit,
    onOpenMovie: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onPlay: (String) -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(HomeSection.Home) }
    NavigationDrawer(
        drawerContent = {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(12.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                HomeSection.entries.forEach { item ->
                    NavigationDrawerItem(
                        selected = item == section,
                        onClick = { section = item },
                        leadingContent = { Icon(item.icon, contentDescription = null) },
                    ) {
                        Text(item.label)
                    }
                }
            }
        },
    ) {
        when (section) {
            HomeSection.Home -> LibraryHomeScreen(onOpenMovie = onOpenMovie, onOpenSeries = onOpenSeries, onPlay = onPlay)
            HomeSection.Movies -> MoviesScreen(onOpenMovie = onOpenMovie)
            HomeSection.Series -> SeriesListScreen(onOpenSeries = onOpenSeries)
            HomeSection.LiveTv -> LiveTvScreen()
            HomeSection.Upload -> TransferScreen()
            HomeSection.Addons -> AddonsScreen()
            HomeSection.Storage -> StorageScreen(onOpenVolume = onOpenVolume)
            HomeSection.Diagnostics -> DiagnosticsScreen()
        }
    }
}
