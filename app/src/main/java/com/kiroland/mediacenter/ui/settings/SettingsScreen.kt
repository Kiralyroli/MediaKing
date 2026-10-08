package com.kiroland.mediacenter.ui.settings

import com.kiroland.mediacenter.util.AppLocale
import com.kiroland.mediacenter.util.AppLanguage
import com.kiroland.mediacenter.ui.library.PillButton
import com.kiroland.mediacenter.R
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
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
import com.kiroland.mediacenter.data.streaming.Regions
import com.kiroland.mediacenter.data.streaming.StreamingProviders
import com.kiroland.mediacenter.data.streaming.Subscribable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.tv.material3.Icon
import com.kiroland.mediacenter.ui.theme.Background
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

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

    /** No app known for the service: "installed" or not means nothing then. */
    fun hasKnownApp(providerId: Int): Boolean = providerId in StreamingProviders.apps

    /** The services offered in the chosen country, reloaded when the country changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val choices: StateFlow<List<Subscribable>> = repository.settings
        .map { repository.region }
        .distinctUntilChanged()
        .mapLatest { streaming.subscriptionChoices() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The chosen country, or null when it follows the TV. */
    fun chosenRegion(): String? = repository.current.region

    fun setRegion(code: String?) = repository.update { it.copy(region = code) }

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

    fun language(): AppLanguage = AppLocale.language(context)

    /** Saves the language, then [restart]s the screen in it; film data follows in the background. */
    fun setLanguage(language: AppLanguage, restart: () -> Unit) {
        AppLocale.setLanguage(context, language)
        appScope.launch { metadata.refreshLanguage() }
        restart()
    }

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
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    var pickingRegion by remember { mutableStateOf(false) }
    var pickerUsed by remember { mutableStateOf(false) }
    val regionRow = remember { FocusRequester() }
    // Outlives the list while the country picker is shown, so the list comes back where it was.
    val listState = rememberLazyListState()
    val locale = AppLocale.current

    if (pickingRegion) {
        RegionPicker(
            chosen = settings.region,
            locale = locale,
            onPick = { code ->
                viewModel.setRegion(code)
                pickingRegion = false
            },
            onClose = { pickingRegion = false },
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 12.dp)) }

        section(R.string.settings_language)
        item {
            Text(
                stringResource(R.string.settings_language_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        item {
            val chosen = remember { viewModel.language() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                AppLanguage.entries.forEach { lang ->
                    PillButton(
                        // Each language in its own name, so it can be found whatever is shown now.
                        text = when (lang) {
                            AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
                            AppLanguage.HUNGARIAN -> "Magyar"
                            AppLanguage.ENGLISH -> "English"
                            AppLanguage.GERMAN -> "Deutsch"
                        },
                        selected = lang == chosen,
                        // Focus starts here: after a language change the screen comes back on the same pill.
                        modifier = if (lang == chosen) Modifier.focusRequester(first) else Modifier,
                        onClick = { if (lang != chosen) viewModel.setLanguage(lang) { context.findActivity()?.recreate() } },
                    )
                }
            }
        }

        section(R.string.settings_playback)
        item {
            Toggle(
                title = stringResource(R.string.settings_auto_next),
                description = stringResource(R.string.settings_auto_next_hint),
                checked = settings.autoNextEpisode,
            ) { value -> viewModel.update { it.copy(autoNextEpisode = value) } }
        }
        item {
            Toggle(
                title = stringResource(R.string.settings_auto_subtitles),
                description = stringResource(R.string.settings_auto_subtitles_hint),
                checked = settings.autoSubtitles,
            ) { value -> viewModel.update { it.copy(autoSubtitles = value) } }
        }

        section(R.string.settings_subscriptions)
        item {
            Text(
                stringResource(R.string.settings_subscriptions_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        item {
            val chosen = settings.region
            Action(
                title = stringResource(R.string.settings_region),
                description = if (chosen == null) {
                    stringResource(R.string.settings_region_auto, Regions.displayName(Regions.effective(null), locale))
                } else {
                    Regions.displayName(chosen, locale)
                },
                modifier = Modifier.focusRequester(regionRow),
                onClick = {
                    pickingRegion = true
                    pickerUsed = true
                },
            )
        }
        items(choices, key = { "sub-" + it.providerId }) { choice ->
            Toggle(
                title = choice.name,
                description = when {
                    !viewModel.hasKnownApp(choice.providerId) -> ""
                    viewModel.isAppInstalled(choice.providerId) -> stringResource(R.string.settings_app_installed)
                    else -> stringResource(R.string.settings_app_not_installed)
                },
                checked = choice.providerId in subscriptions,
            ) { value -> viewModel.setSubscribed(choice.providerId, value) }
        }

        section(R.string.settings_live_tv)
        item {
            Toggle(
                title = stringResource(R.string.settings_antenna),
                description = stringResource(R.string.settings_antenna_hint),
                checked = settings.showAntenna,
            ) { value -> viewModel.update { it.copy(showAntenna = value) } }
        }

        section(R.string.settings_upload)
        item {
            Toggle(
                title = stringResource(R.string.settings_upload_autostart),
                description = stringResource(R.string.settings_upload_autostart_hint),
                checked = settings.uploadAutoStart,
            ) { value -> viewModel.setUploadAutoStart(value) }
        }

        section(R.string.settings_library)
        item { Action(stringResource(R.string.settings_storage), stringResource(R.string.settings_storage_hint), onClick = onOpenStorage) }
        item {
            Action(stringResource(R.string.settings_rescan), stringResource(R.string.settings_rescan_hint)) {
                viewModel.rescan()
                Toast.makeText(context, context.getString(R.string.settings_rescan_started), Toast.LENGTH_SHORT).show()
            }
        }
        item {
            Action(
                title = stringResource(R.string.settings_reload),
                description = if (reloadArmed) {
                    stringResource(R.string.settings_reload_armed)
                } else {
                    stringResource(R.string.settings_reload_hint)
                },
                warning = reloadArmed,
            ) {
                if (reloadArmed) {
                    viewModel.reloadMetadata()
                    Toast.makeText(context, context.getString(R.string.settings_reload_started), Toast.LENGTH_SHORT).show()
                }
                reloadArmed = !reloadArmed
            }
        }

        section(R.string.settings_system)
        item { Action(stringResource(R.string.settings_diagnostics), stringResource(R.string.settings_diagnostics_hint), onClick = onOpenDiagnostics) }
        item {
            Action(
                "MediaKing ${BuildConfig.VERSION_NAME}",
                stringResource(if (viewModel.tmdbConfigured) R.string.settings_tmdb_set else R.string.settings_tmdb_missing),
            ) {}
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    // Back on the list after choosing: where the user left it.
    LaunchedEffect(pickingRegion) { if (!pickingRegion && pickerUsed) runCatching { regionRow.requestFocus() } }
}

/** Every country, the TV's own first; covers the settings while open. */
@Composable
private fun RegionPicker(chosen: String?, locale: java.util.Locale, onPick: (String?) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val countries = remember(locale) { Regions.all(locale) }
    val current = remember { FocusRequester() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item { Text(stringResource(R.string.settings_region), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 12.dp)) }
        item {
            RegionRow(
                stringResource(R.string.settings_region_auto, Regions.displayName(Regions.effective(null), locale)),
                selected = chosen == null,
                modifier = if (chosen == null) Modifier.focusRequester(current) else Modifier,
            ) { onPick(null) }
        }
        items(countries, key = { it }) { code ->
            RegionRow(
                Regions.displayName(code, locale),
                selected = code == chosen,
                modifier = if (code == chosen) Modifier.focusRequester(current) else Modifier,
            ) { onPick(code) }
        }
    }
    LaunchedEffect(Unit) { runCatching { current.requestFocus() } }
}

@Composable
private fun RegionRow(name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        headlineContent = { Text(name) },
        trailingContent = if (selected) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
    )
}

private fun LazyListScope.section(@StringRes title: Int) {
    item {
        Text(
            stringResource(title),
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun Context.findActivity(): android.app.Activity? {
    var c: Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
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
        supportingContent = if (description.isEmpty()) null else ({ Text(description) }),
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun Action(title: String, description: String, warning: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier,
        headlineContent = { Text(title) },
        supportingContent = { Text(description, color = if (warning) Color(0xFFFFC857) else MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}
