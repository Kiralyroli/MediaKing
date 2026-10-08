package com.kiroland.mediacenter.ui.diagnostics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** A library the app ships, and the license file (in assets/licenses) it comes under. */
data class OpenSourceComponent(val name: String, val license: String, val file: String)

const val SOURCE_URL = "https://github.com/Kiralyroli/MediaKing"

/** The app itself is GPL-3.0 (the FFmpeg decoder it bundles is), everything else is listed with its own license. */
val OPEN_SOURCE_COMPONENTS = listOf(
    OpenSourceComponent("MediaKing", "GPL-3.0", "GPL-3.0.txt"),
    OpenSourceComponent("Jellyfin media3-ffmpeg-decoder (FFmpeg)", "GPL-3.0", "GPL-3.0.txt"),
    OpenSourceComponent("AndroidX, Jetpack Compose, Media3, Room", "Apache-2.0", "Apache-2.0.txt"),
    OpenSourceComponent("Kotlin, kotlinx.coroutines, kotlinx.serialization, Ktor", "Apache-2.0", "Apache-2.0.txt"),
    OpenSourceComponent("Dagger Hilt, Guava, Accompanist, ZXing", "Apache-2.0", "Apache-2.0.txt"),
    OpenSourceComponent("OkHttp, Retrofit, Coil", "Apache-2.0", "Apache-2.0.txt"),
    OpenSourceComponent("SMBJ, ASN-One, Typesafe Config, Jansi, JSpecify, JSR-305, javax/jakarta.inject", "Apache-2.0", "Apache-2.0.txt"),
    OpenSourceComponent("SLF4J, Bouncy Castle, MBassador", "MIT", "MIT.txt"),
    OpenSourceComponent("Space Grotesk", "OFL-1.1", "SpaceGrotesk-OFL.txt"),
    OpenSourceComponent("Manrope", "OFL-1.1", "Manrope-OFL.txt"),
)

/** A license's full text; every paragraph takes focus so the D-pad scrolls through it. */
@Composable
fun LicenseText(component: OpenSourceComponent, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val paragraphs = remember(component.file) {
        context.assets.open("licenses/${component.file}").bufferedReader().use { it.readText() }
            .split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp),
        contentPadding = PaddingValues(vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("${component.name} · ${component.license}", style = MaterialTheme.typography.headlineSmall) }
        itemsIndexed(paragraphs) { index, paragraph ->
            Text(
                paragraph,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.then(if (index == 0) Modifier.focusRequester(first) else Modifier).focusable(),
            )
        }
    }
}
