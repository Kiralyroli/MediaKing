package com.kiroland.mediacenter.player

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import com.kiroland.mediacenter.data.library.LibraryRepository
import com.kiroland.mediacenter.data.addons.AddonRepository
import com.kiroland.mediacenter.data.settings.SettingsRepository
import com.kiroland.mediacenter.di.ApplicationScope
import com.kiroland.mediacenter.ui.library.episodeCode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Full-screen playback of a local file. A plain View-based PlayerView gives D-pad controls for free.
 * Remembers the position per file and continues with the next episode of a series.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    @Inject lateinit var library: LibraryRepository
    // Progress writes must survive the activity finishing.
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var addons: AddonRepository
    @Inject lateinit var settings: SettingsRepository

    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private var progressJob: Job? = null

    private lateinit var currentPath: String
    /** null until the start position has been decided (saved progress, or 0 for "from start"). */
    private var resumePositionMs: Long? = null
    private var playWhenReady = true
    private var subtitlesChosenFor: String? = null

    /** An add-on channel being played live: no progress, no next episode, channel zapping. */
    private data class LiveRef(val addonId: String, val channelId: String)

    private var live: LiveRef? = null
    private var lastLiveRecoveryMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val addonId = savedInstanceState?.getString(STATE_ADDON) ?: intent.getStringExtra(EXTRA_ADDON)
        val channelId = savedInstanceState?.getString(STATE_CHANNEL) ?: intent.getStringExtra(EXTRA_CHANNEL)
        live = if (addonId != null && channelId != null) LiveRef(addonId, channelId) else null
        currentPath = live?.let { liveKey(it) }
            ?: savedInstanceState?.getString(STATE_PATH)
            ?: requireNotNull(intent.getStringExtra(EXTRA_PATH))
        if (savedInstanceState != null) {
            resumePositionMs = savedInstanceState.getLong(STATE_POSITION)
            playWhenReady = savedInstanceState.getBoolean(STATE_PLAY_WHEN_READY, true)
        } else if (intent.getBooleanExtra(EXTRA_FROM_START, false)) {
            resumePositionMs = 0L
        }
        playerView = PlayerView(this).apply {
            keepScreenOn = true
            setShowSubtitleButton(true)
            setShowNextButton(false)
            setShowPreviousButton(false)
            controllerShowTimeoutMs = 4_000
        }
        setContentView(playerView)
        installTrackMenus()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        releasePlayer()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PATH, currentPath)
        live?.let {
            outState.putString(STATE_ADDON, it.addonId)
            outState.putString(STATE_CHANNEL, it.channelId)
        }
        outState.putLong(STATE_POSITION, player?.currentPosition ?: resumePositionMs ?: 0L)
        outState.putBoolean(STATE_PLAY_WHEN_READY, player?.playWhenReady ?: playWhenReady)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handleShortcut(event)) return true
        if (handleZapping(event)) return true
        // Media keys and D-pad go to the player UI first (shows the controller, seeks, toggles).
        if (event.keyCode != KeyEvent.KEYCODE_BACK && playerView.dispatchKeyEvent(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && playerView.isControllerFullyVisible) {
            playerView.hideController()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun initializePlayer() {
        // Platform decoders first, FFmpeg for what the TV cannot decode itself (AC3, DTS, TrueHD...).
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        val builder = ExoPlayer.Builder(this, renderersFactory)
        // An add-on may need headers (Referer, User-Agent) on the stream requests themselves.
        live?.let { addons.find(it.addonId)?.stream?.headers }?.takeIf { it.isNotEmpty() }?.let { headers ->
            val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
            builder.setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
        }
        val exoPlayer = builder.build().apply {
            // Subtitles are picked by SubtitleChooser once the audio track is known.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguages("hu", "en")
                .build()
            addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    if (settings.current.autoSubtitles) autoSelectSubtitles(this@apply, tracks)
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (live != null && recoverLive(this@apply)) return
                    Toast.makeText(this@PlayerActivity, "Lejátszási hiba: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_ENDED) return
                    if (live != null) recoverLive(this@apply) else onEnded()
                }
            })
        }
        player = exoPlayer
        playerView.player = exoPlayer
        subtitlesChosenFor = null

        live?.let { ref ->
            lifecycleScope.launch { loadLive(exoPlayer, ref) }
            return
        }
        lifecycleScope.launch {
            val start = resumePositionMs ?: savedStart(currentPath)
            if (player !== exoPlayer) return@launch // Released while loading.
            load(exoPlayer, currentPath, start)
            exoPlayer.playWhenReady = playWhenReady
            startProgressUpdates(exoPlayer)
        }
    }

    private suspend fun loadLive(exoPlayer: ExoPlayer, ref: LiveRef) {
        val addon = addons.find(ref.addonId)
        val channel = addons.channels(ref.addonId).firstOrNull { it.id == ref.channelId }
        val result = runCatching { addons.resolve(ref.addonId, ref.channelId) }
        if (player !== exoPlayer || live != ref) return // Released or zapped meanwhile.
        val url = result.getOrElse { error ->
            Toast.makeText(this, "Nem sikerült elindítani: ${channel?.name ?: ref.channelId}\n${error.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val item = MediaItem.Builder()
            .setUri(url)
            .apply { addon?.stream?.mimeType?.let(::setMimeType) }
            .setMediaMetadata(MediaMetadata.Builder().setTitle(channel?.name).build())
            .build()
        exoPlayer.setMediaItem(item)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        Toast.makeText(this, channel?.name ?: ref.channelId, Toast.LENGTH_SHORT).show()
    }

    /**
     * Live stream addresses carry a short-lived token: when the stream stops (expired token, fell behind
     * the live window), resolve a fresh address. At most every 20 s, so a real outage does not loop.
     */
    private fun recoverLive(exoPlayer: ExoPlayer): Boolean {
        val ref = live ?: return false
        val now = System.currentTimeMillis()
        if (now - lastLiveRecoveryMs < LIVE_RECOVERY_INTERVAL_MS) return false
        lastLiveRecoveryMs = now
        lifecycleScope.launch { loadLive(exoPlayer, ref) }
        return true
    }

    /** Up/down (with the controls hidden) and the remote's channel keys switch channels while live. */
    private fun handleZapping(event: KeyEvent): Boolean {
        val current = live ?: return false
        val step = when (event.keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP -> 1
            KeyEvent.KEYCODE_CHANNEL_DOWN -> -1
            KeyEvent.KEYCODE_DPAD_UP -> if (playerView.isControllerFullyVisible) return false else 1
            KeyEvent.KEYCODE_DPAD_DOWN -> if (playerView.isControllerFullyVisible) return false else -1
            else -> return false
        }
        if (event.action != KeyEvent.ACTION_UP) return true
        // Zapping stays within the add-on the channel came from, in its own order.
        val channels = addons.channels(current.addonId).takeIf { it.isNotEmpty() } ?: return true
        val index = channels.indexOfFirst { it.id == current.channelId }.coerceAtLeast(0)
        val next = LiveRef(current.addonId, channels[(index + step).mod(channels.size)].id)
        val exoPlayer = player ?: return true
        live = next
        currentPath = liveKey(next)
        subtitlesChosenFor = null
        lastLiveRecoveryMs = 0L
        exoPlayer.stop()
        lifecycleScope.launch { loadLive(exoPlayer, next) }
        return true
    }

    private fun liveKey(ref: LiveRef) = "live:${ref.addonId}/${ref.channelId}"

    /** Runs once per loaded file; later track changes are the user's own choices. */
    private fun autoSelectSubtitles(exoPlayer: ExoPlayer, tracks: Tracks) {
        if (subtitlesChosenFor == currentPath) return
        val audio = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected } ?: return
        subtitlesChosenFor = currentPath

        val audioLanguage = (0 until audio.length).firstOrNull { audio.isTrackSelected(it) }
            ?.let { audio.getTrackFormat(it).language }
        val options = tracks.groups.withIndex()
            .filter { (_, group) -> group.type == C.TRACK_TYPE_TEXT }
            .flatMap { (groupIndex, group) ->
                (0 until group.length).filter { group.isTrackSupported(it) }.map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    TextTrackOption(
                        groupIndex = groupIndex,
                        trackIndex = trackIndex,
                        language = format.language,
                        forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0 ||
                            format.label?.contains("forced", ignoreCase = true) == true,
                    )
                }
            }

        val params = exoPlayer.trackSelectionParameters.buildUpon()
        when (val choice = SubtitleChooser.choose(audioLanguage, options)) {
            is SubtitleChoice.Select -> {
                val group = tracks.groups[choice.option.groupIndex].mediaTrackGroup
                params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group, choice.option.trackIndex))
            }
            SubtitleChoice.Off -> params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            SubtitleChoice.Keep -> return
        }
        exoPlayer.trackSelectionParameters = params.build()
    }

    /** Where to continue a file: the saved position, unless it was finished or barely started. */
    private suspend fun savedStart(path: String): Long {
        val progress = library.progress(path) ?: return 0L
        return if (progress.finished || progress.positionMs < 10_000) 0L else progress.positionMs
    }

    private suspend fun load(exoPlayer: ExoPlayer, path: String, startMs: Long) {
        val file = File(path)
        val subtitles = withContext(Dispatchers.IO) { SubtitleLoader.findSidecars(file, cacheDir) }
        if (player !== exoPlayer) return
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setSubtitleConfigurations(subtitles)
            .build()
        resumePositionMs = startMs
        exoPlayer.setMediaItem(mediaItem, startMs)
        exoPlayer.prepare()
    }

    private fun startProgressUpdates(exoPlayer: ExoPlayer) {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                if (exoPlayer.isPlaying) saveProgress(exoPlayer)
            }
        }
    }

    private fun saveProgress(exoPlayer: ExoPlayer) {
        if (live != null) return // Nothing to resume in a live broadcast.
        val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: return
        val path = currentPath
        val position = exoPlayer.currentPosition
        appScope.launch { library.saveProgress(path, position, duration) }
    }

    private fun onEnded() {
        val exoPlayer = player ?: return
        val finishedPath = currentPath
        val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET }
        lifecycleScope.launch {
            if (duration != null) library.saveProgress(finishedPath, duration, duration)
            val next = if (settings.current.autoNextEpisode) library.nextEpisode(finishedPath) else null
            if (next == null || player !== exoPlayer) {
                finish()
                return@launch
            }
            currentPath = next.path
            Toast.makeText(
                this@PlayerActivity,
                "Következik: ${episodeCode(next.season, next.episode, next.episodeEnd)}",
                Toast.LENGTH_LONG,
            ).show()
            load(exoPlayer, next.path, savedStart(next.path))
            exoPlayer.playWhenReady = true
        }
    }

    /**
     * The built-in subtitle/settings popups name tracks with DefaultTrackNameProvider (language only) and
     * offer no way to swap it, so both buttons open dialogs that use [DetailedTrackNameProvider] instead.
     */
    private fun installTrackMenus() {
        fun view(id: Int): View? = playerView.findViewById(id)
        val subtitle = view(androidx.media3.ui.R.id.exo_subtitle)
        val settings = view(androidx.media3.ui.R.id.exo_settings)
        subtitle?.setOnClickListener { showTrackDialog(C.TRACK_TYPE_TEXT) }
        settings?.setOnClickListener { showSettingsMenu() }

        // The stock layout gives the D-pad no way from the centre buttons down to the bottom bar
        // (it stops at the time bar), so wire the route explicitly: down to CC, up back to play/pause.
        val playPause = androidx.media3.ui.R.id.exo_play_pause
        val centre = listOf(
            playPause,
            androidx.media3.ui.R.id.exo_rew_with_amount,
            androidx.media3.ui.R.id.exo_ffwd_with_amount,
            androidx.media3.ui.R.id.exo_progress,
        )
        val down = subtitle?.id ?: settings?.id ?: return
        centre.forEach { view(it)?.nextFocusDownId = down }
        subtitle?.nextFocusUpId = playPause
        settings?.nextFocusUpId = playPause
    }

    /** Remote shortcuts: Menu opens the options, Captions/Audio open their pickers directly. */
    private fun handleShortcut(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) {
            return event.keyCode in SHORTCUT_KEYS
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> showSettingsMenu()
            KeyEvent.KEYCODE_CAPTIONS -> showTrackDialog(C.TRACK_TYPE_TEXT)
            KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> showTrackDialog(C.TRACK_TYPE_AUDIO)
            else -> return false
        }
        return true
    }

    private fun showSettingsMenu() {
        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle("Beállítások")
            .setItems(arrayOf("Hangsáv", "Felirat", "Lejátszási sebesség")) { _, which ->
                when (which) {
                    0 -> showTrackDialog(C.TRACK_TYPE_AUDIO)
                    1 -> showTrackDialog(C.TRACK_TYPE_TEXT)
                    2 -> showSpeedDialog()
                }
            }
            .show()
    }

    private fun showTrackDialog(trackType: Int) {
        val player = player ?: return
        val isText = trackType == C.TRACK_TYPE_TEXT
        if (player.currentTracks.groups.none { it.type == trackType }) {
            Toast.makeText(this, if (isText) "Nincs felirat" else "Nincs választható hangsáv", Toast.LENGTH_SHORT).show()
            return
        }
        TrackSelectionDialogBuilder(this, if (isText) "Felirat" else "Hangsáv", player, trackType)
            .setTheme(DIALOG_THEME)
            .setTrackNameProvider(DetailedTrackNameProvider)
            .setShowDisableOption(isText)
            .setAllowAdaptiveSelections(false)
            .build()
            .show()
    }

    private fun showSpeedDialog() {
        val player = player ?: return
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val labels = speeds.map { if (it == 1f) "Normál" else "${it}×".replace('.', ',') }.toTypedArray()
        val current = speeds.indexOfFirst { it == player.playbackParameters.speed }
        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle("Lejátszási sebesség")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                player.setPlaybackSpeed(speeds[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun releasePlayer() {
        progressJob?.cancel()
        player?.let {
            if (it.playbackState != Player.STATE_IDLE && it.playbackState != Player.STATE_ENDED) saveProgress(it)
            resumePositionMs = it.currentPosition
            playWhenReady = it.playWhenReady
            it.release()
        }
        player = null
        playerView.player = null
    }

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_ADDON = "addon"
        private const val EXTRA_CHANNEL = "channel"
        private const val STATE_ADDON = "addon"
        private const val STATE_CHANNEL = "channel"
        private const val LIVE_RECOVERY_INTERVAL_MS = 20_000L
        private const val EXTRA_FROM_START = "fromStart"
        private const val STATE_PATH = "path"
        private const val STATE_POSITION = "position"
        private const val STATE_PLAY_WHEN_READY = "playWhenReady"
        private const val PROGRESS_INTERVAL_MS = 10_000L
        private val DIALOG_THEME = android.R.style.Theme_Material_Dialog_Alert
        private val SHORTCUT_KEYS = setOf(
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS,
            KeyEvent.KEYCODE_CAPTIONS,
            KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK,
        )

        /** Plays an add-on's live channel. */
        fun liveIntent(context: Context, addonId: String, channelId: String): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_ADDON, addonId)
                .putExtra(EXTRA_CHANNEL, channelId)

        fun intent(context: Context, path: String, fromStart: Boolean = false): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_PATH, path)
                .putExtra(EXTRA_FROM_START, fromStart)
    }
}
