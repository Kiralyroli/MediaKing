package com.kiroland.mediacenter.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kiroland.mediacenter.player.PlayerActivity
import com.kiroland.mediacenter.ui.browser.BrowserScreen
import com.kiroland.mediacenter.ui.home.HomeScreen
import com.kiroland.mediacenter.ui.library.MovieScreen
import com.kiroland.mediacenter.ui.library.SeriesScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class BrowserRoute(val rootPath: String, val rootName: String)

@Serializable
data class MovieRoute(val path: String)

@Serializable
data class SeriesRoute(val seriesKey: String)

@Composable
fun MediaCenterNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val play = { path: String, fromStart: Boolean ->
        context.startActivity(PlayerActivity.intent(context, path, fromStart))
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenVolume = { volume -> navController.navigate(BrowserRoute(volume.path, volume.name)) },
                onOpenMovie = { path -> navController.navigate(MovieRoute(path)) },
                onOpenSeries = { key -> navController.navigate(SeriesRoute(key)) },
                onPlay = { path -> play(path, false) },
            )
        }
        composable<BrowserRoute> {
            BrowserScreen(
                onPlay = { entry -> play(entry.path, false) },
                onExit = { navController.popBackStack() },
            )
        }
        composable<MovieRoute> {
            MovieScreen(onPlay = play)
        }
        composable<SeriesRoute> {
            SeriesScreen(onPlay = { path -> play(path, false) })
        }
    }
}
