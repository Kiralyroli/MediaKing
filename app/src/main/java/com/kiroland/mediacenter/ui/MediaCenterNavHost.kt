package com.kiroland.mediacenter.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kiroland.mediacenter.data.remote.RemoteControl
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kiroland.mediacenter.player.PlayerActivity
import com.kiroland.mediacenter.ui.browser.BrowserScreen
import com.kiroland.mediacenter.ui.home.HomeScreen
import com.kiroland.mediacenter.data.library.db.MediaKind
import com.kiroland.mediacenter.ui.library.FixMatchScreen
import com.kiroland.mediacenter.ui.library.MovieScreen
import com.kiroland.mediacenter.ui.library.SeriesScreen
import com.kiroland.mediacenter.ui.live.GuideScreen
import com.kiroland.mediacenter.ui.streaming.StreamingTitleScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class BrowserRoute(val rootPath: String, val rootName: String)

@Serializable
data class MovieRoute(val path: String)

@Serializable
data class SeriesRoute(val seriesKey: String)

@Serializable
data object GuideRoute

/** A film or series found by the streaming search (by TMDB id). */
@Serializable
data class StreamingTitleRoute(val isMovie: Boolean, val tmdbId: Int)

/** Pick the right TMDB entry for a metadata key; [kind] is a MediaKind name. */
@Serializable
data class FixMatchRoute(val key: String, val kind: String, val query: String)

@Composable
fun MediaCenterNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val play = { path: String, fromStart: Boolean ->
        context.startActivity(PlayerActivity.intent(context, path, fromStart))
    }

    // A search typed on the phone remote: back to the home screen, which opens the search.
    val remoteSearch by RemoteControl.searchRequests.collectAsState()
    LaunchedEffect(remoteSearch) {
        if (remoteSearch != null) navController.popBackStack(HomeRoute, inclusive = false)
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
                onOpenGuide = { navController.navigate(GuideRoute) },
                onOpenStreaming = { isMovie, id -> navController.navigate(StreamingTitleRoute(isMovie, id)) },
            )
        }
        composable<BrowserRoute> {
            BrowserScreen(
                onPlay = { entry -> play(entry.path, false) },
                onExit = { navController.popBackStack() },
            )
        }
        composable<MovieRoute> {
            MovieScreen(
                onPlay = play,
                onFixMatch = { key, query -> navController.navigate(FixMatchRoute(key, MediaKind.MOVIE.name, query)) },
            )
        }
        composable<SeriesRoute> {
            SeriesScreen(
                onPlay = { path -> play(path, false) },
                onFixMatch = { key, query -> navController.navigate(FixMatchRoute(key, MediaKind.EPISODE.name, query)) },
            )
        }
        composable<GuideRoute> { GuideScreen() }
        composable<StreamingTitleRoute> {
            StreamingTitleScreen(
                onOpenMovie = { path -> navController.navigate(MovieRoute(path)) },
                onOpenSeries = { key -> navController.navigate(SeriesRoute(key)) },
            )
        }
        composable<FixMatchRoute> {
            FixMatchScreen(onDone = { navController.popBackStack() })
        }
    }
}
