package com.kiroland.mediacenter.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.kiroland.mediacenter.BuildConfig
import com.kiroland.mediacenter.data.library.LibraryScanner
import com.kiroland.mediacenter.data.metadata.MetadataRepository
import com.kiroland.mediacenter.data.settings.Settings
import com.kiroland.mediacenter.data.settings.SettingsRepository
import com.kiroland.mediacenter.data.transfer.TransferService
import com.kiroland.mediacenter.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.foundation.lazy.items
import com.kiroland.mediacenter.data.streaming.StreamingRepository
import com.kiroland.mediacenter.data.streaming.Subscriptions

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: SettingsRepository,
    private val scanner: LibraryScanner,
    private val metadata: MetadataRepository,
    private val streaming: StreamingRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    /** Checked state of each subscription choice (the installed apps until the user picks). */
    fun subscriptions(): Set<Int> = streaming.mySubscriptions()

    fun isAppInstalled(providerId: Int): Boolean = streaming.isAppInstalled(providerId)

    fun setSubscribed(providerId: Int, subscribed: Boolean) {
        val now = streaming.mySubscriptions()
        repository.update { it.copy(subscriptions = if (subscribed) now + providerId else now - providerId) }
    }

    val settings: StateFlow<Settings> = repository.settings
    val tmdbConfigured: Boolean get() = metadata.isConfigured

    fun update(transform: (Settings) -> Settings) = repository.update(transform)

    fun setUploadAutoStart(enabled: Boolean) {
        repository.update { it.copy(uploadAutoStart = enabled) }
        // Switching it on also starts the server now, so the setting has a visible effect.
        if (enabled) TransferService.start(context)
    }

    fun rescan() = scanner.scanAll()

    /** Runs in the app scope: a full reload outlives this screen. */
    fun reloadMetadata() {
        appScope.launch { metadata.reloadAll() }
    }
}

@Composable
fun SettingsScreen(onOpenStorage: () -> Unit, onOpenDiagnostics: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // Recomputed whenever the settings change (a toggle was flipped).
    val subscriptions = remember(settings) { viewModel.subscriptions() }
    val context = LocalContext.current
    // Destructive action: first press arms, second runs (as elsewhere in the app).
    var reloadArmed by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Text("Beállítások", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 12.dp)) }

        section("Lejátszás")
        item {
            Toggle(
                title = "Következő rész automatikusan",
                description = "Egy sorozatrész végén rögtön indul a következő.",
                checked = settings.autoNextEpisode,
                modifier = Modifier.focusRequester(first),
            ) { value -> viewModel.update { it.copy(autoNextEpisode = value) } }
        }
        item {
            Toggle(
                title = "Automatikus feliratválasztás",
                description = "Magyar hangnál a kényszerített, idegen nyelvű hangnál a teljes magyar felirat.",
                checked = settings.autoSubtitles,
            ) { value -> viewModel.update { it.copy(autoSubtitles = value) } }
        }

        section("Előfizetéseim")
        item {
            Text(
                "A „Hol nézheted?” részben ezeket teszi előre és jelöli zölddel. Amíg nem választasz, a TV-re telepített appok számítanak.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        items(Subscriptions.choices, key = { "sub-" + it.providerId }) { choice ->
            Toggle(
                title = choice.name,
                description = if (viewModel.isAppInstalled(choice.providerId)) "Az app telepítve van." else "Az app nincs telepítve.",
                checked = choice.providerId in subscriptions,
            ) { value -> viewModel.setSubscribed(choice.providerId, value) }
        }

        section("Élő TV")
        item {
            Toggle(
                title = "Antennás TV-adás",
                description = "A TV beépített tunere (MinDig TV). Antenna és csatornakeresés kell hozzá.",
                checked = settings.showAntenna,
            ) { value -> viewModel.update { it.copy(showAntenna = value) } }
        }

        section("Wi-Fi feltöltés")
        item {
            Toggle(
                title = "Indítás az alkalmazással",
                description = "A feltöltő szerver magától elindul, amikor megnyitod az appot.",
                checked = settings.uploadAutoStart,
            ) { value -> viewModel.setUploadAutoStart(value) }
        }

        section("Médiatár")
        item { Action("Tárhelyek és mappák", "Meghajtók böngészése, mappák felvétele a médiatárba.", onClick = onOpenStorage) }
        item {
            Action("Átfésülés most", "Új, törölt vagy átnevezett fájlok keresése a médiatár-mappákban.") {
                viewModel.rescan()
                Toast.makeText(context, "Médiatár frissítése elindult", Toast.LENGTH_SHORT).show()
            }
        }
        item {
            Action(
                title = "Metaadatok újratöltése",
                description = if (reloadArmed) {
                    "Nyomd meg újra: minden TMDB-adat (a kézi javítások is) törlődik és újra letöltődik."
                } else {
                    "Poszterek, leírások, epizódcímek újra lekérése a TMDB-ről."
                },
                warning = reloadArmed,
            ) {
                if (reloadArmed) {
                    viewModel.reloadMetadata()
                    Toast.makeText(context, "Metaadatok újratöltése elindult", Toast.LENGTH_SHORT).show()
                }
                reloadArmed = !reloadArmed
            }
        }

        section("Rendszer")
        item { Action("Diagnosztika", "Készülék, kodekek, tárhelyek írástesztje.", onClick = onOpenDiagnostics) }
        item {
            Action(
                "MediaKing ${BuildConfig.VERSION_NAME}",
                "TMDB: " + if (viewModel.tmdbConfigured) "beállítva" else "nincs token (local.properties: tmdb.token)",
            ) {}
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

private fun LazyListScope.section(title: String) {
    item {
        Text(
            title,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun Toggle(
    title: String,
    description: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        selected = false,
        onClick = { onChange(!checked) },
        modifier = modifier,
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun Action(title: String, description: String, warning: Boolean = false, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(title) },
        supportingContent = { Text(description, color = if (warning) Color(0xFFFFC857) else MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}
