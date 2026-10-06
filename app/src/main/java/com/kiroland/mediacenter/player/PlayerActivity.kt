package com.kiroland.mediacenter.player

import com.kiroland.mediacenter.util.AppLocale
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
import com.kiroland.mediacenter.R
import com.kiroland.mediacenter.data.library.LibraryRepository
import com.kiroland.mediacenter.data.library.db.MediaEntity
import com.kiroland.mediacenter.data.library.db.MediaKind
import com.kiroland.mediacenter.data.metadata.MetadataRepository
import com.kiroland.mediacenter.data.segments.Segment
import com.kiroland.mediacenter.data.segments.SegmentRepository
import com.kiroland.mediacenter.data.segments.SegmentType
import kotlinx.coroutines.flow.first
import android.widget.TextView
import com.kiroland.mediacenter.data.addons.AddonRepository
import com.kiroland.mediacenter.data.epg.EpgRepository
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
import com.kiroland.mediacenter.data.network.SmbClient
import com.kiroland.mediacenter.data.storage.MediaFiles
import javax.inject.Inject

/**
 * Full-screen playback of a local file. A plain View-based PlayerView gives D-pad controls for free.
 * Remembers the position per file and continues with the next episode of a series.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    // The app's own language (settings), not necessarily the TV's.
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.kiroland.mediacenter.util.AppLocale.wrap(newBase))
    }


    @Inject lateinit var library: LibraryRepository
    // Progress writes must survive the activity finishing.
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var addons: AddonRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var epg: EpgRepository
    @Inject lateinit var smb: SmbClient
    @Inject lateinit var mediaFiles: MediaFiles
    @Inject lateinit var metadata: MetadataRepository
    @Inject lateinit var segmentRepository: SegmentRepository

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
    /** The episode after the one playing, for the next-episode button. */
    private var nextUp: MediaEntity? = null
    private var nextTitle: String? = null
    /** Intro, recap and credits of the file playing (see [SegmentRepository]). */
    private var segments: List<Segment> = emptyList()
    private var segmentJob: Job? = null
    /** The next-episode card was closed with Back: it stays away for this episode. */
    private var nextCardDismissed = false
    private var countdownStartedAt: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        DetailedTrackNameProvider.bind(resources)
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
        // The controls are our own layout (res/layout/player_controls.xml) on Media3's controller.
        setContentView(R.layout.player_screen)
        playerView = findViewById(R.id.player_view)
        installControls()
        installControllerFade()
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
        // Any key keeps the controls up a while longer (and brings them back fully if they were fading).
        if (event.action == KeyEvent.ACTION_DOWN) scheduleControllerHide()
        if (handlePrompts(event)) return true
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
            // Matches the −10 / +30 buttons.
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
        // An add-on may need headers (Referer, User-Agent) on the stream requests themselves.
        live?.let { addons.find(it.addonId)?.stream?.headers }?.takeIf { it.isNotEmpty() }?.let { headers ->
            val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
            builder.setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
        }
        if (live == null) {
            // Library files may sit on a network share.
            builder.setMediaSourceFactory(DefaultMediaSourceFactory(LibraryDataSourceFactory(this, smb)))
        }
        val exoPlayer = builder.build().apply {
            // Subtitles are picked by SubtitleChooser once the audio track is known.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguages("hu", "en")
                .build()
            addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    if (settings.current.autoSubtitles) autoSelectSubtitles(this@apply, tracks)
                    showTracks(tracks)
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (live != null && recoverLive(this@apply)) return
                    Toast.makeText(this@PlayerActivity, getString(R.string.player_error, error.errorCodeName), Toast.LENGTH_LONG).show()
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
            startSegmentWatch(exoPlayer)
        }
    }

    private suspend fun loadLive(exoPlayer: ExoPlayer, ref: LiveRef) {
        val addon = addons.find(ref.addonId)
        val channel = addons.channels(ref.addonId).firstOrNull { it.id == ref.channelId }
        val result = runCatching { addons.resolve(ref.addonId, ref.channelId) }
        if (player !== exoPlayer || live != ref) return // Released or zapped meanwhile.
        val url = result.getOrElse { error ->
            Toast.makeText(this, getString(R.string.player_live_failed, channel?.name ?: ref.channelId, error.message.orEmpty()), Toast.LENGTH_LONG).show()
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
        val onNow = channel?.let { epg.nowNext(ref.addonId, it).first?.title }
        setHeader(getString(R.string.player_live_header, channel?.name ?: ref.channelId), onNow ?: channel?.name.orEmpty())
        playerView.findViewById<View>(R.id.player_next).visibility = View.GONE
        val label = listOfNotNull(channel?.name ?: ref.channelId, onNow?.let { getString(R.string.live_now, it) }).joinToString("\n")
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
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
        val subtitles = withContext(Dispatchers.IO) { SubtitleLoader.findSidecars(path, mediaFiles, cacheDir) }
        if (player !== exoPlayer) return
        val mediaItem = MediaItem.Builder()
            .setUri(playbackUri(path))
            .setSubtitleConfigurations(subtitles)
            .build()
        resumePositionMs = startMs
        showHeader(path)
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
            playEpisode(exoPlayer, next)
        }
    }

    /** The "next episode" button: keeps this one's position and moves on. */
    private fun skipToNext() {
        val exoPlayer = player ?: return
        val next = nextUp ?: return
        saveProgress(exoPlayer)
        lifecycleScope.launch { playEpisode(exoPlayer, next) }
    }

    private suspend fun playEpisode(exoPlayer: ExoPlayer, next: MediaEntity) {
        currentPath = next.path
        Toast.makeText(this, getString(R.string.player_up_next, episodeCode(next.season, next.episode, next.episodeEnd)), Toast.LENGTH_LONG).show()
        load(exoPlayer, next.path, savedStart(next.path))
        exoPlayer.playWhenReady = true
    }

    /** Series and episode (or film and year) over the controls; the next-episode button when there is one. */
    /** Series and episode (or film and year) over the controls; the next-episode button when there is one. */
    private fun showHeader(path: String) {
        segments = emptyList()
        nextCardDismissed = false
        countdownStartedAt = null
        lifecycleScope.launch {
            val item = library.media(path).first() ?: return@launch
            val media = item.media
            val tvId = item.metadata?.tmdbId
            suspend fun episodeName(season: Int?, episode: Int?) = tvId?.let { id ->
                metadata.episodes(id).first().firstOrNull { it.season == season && it.episode == episode }?.name
            }
            if (media.kind == MediaKind.EPISODE) {
                val code = episodeCode(media.season, media.episode, media.episodeEnd)
                setHeader("${item.displayTitle} · $code", episodeName(media.season, media.episode) ?: media.fileName)
                nextUp = library.nextEpisode(path)
                nextTitle = nextUp?.let { next ->
                    listOfNotNull(episodeCode(next.season, next.episode, next.episodeEnd), episodeName(next.season, next.episode))
                        .joinToString(" · ")
                }
            } else {
                setHeader(listOfNotNull(getString(R.string.kind_movie), item.displayYear?.toString()).joinToString(" · "), item.displayTitle)
                nextUp = null
                nextTitle = null
            }
            if (currentPath != path) return@launch
            playerView.findViewById<View>(R.id.player_next).visibility = if (nextUp != null) View.VISIBLE else View.GONE
            val found = segmentRepository.segmentsFor(item)
            if (currentPath == path) segments = found
        }
    }

    /**
     * Twice a second: offer to skip an intro or recap while one plays, and from the credits on (or the
     * last half minute when their start is unknown) show the next episode, counting down when
     * "next episode automatically" is on.
     */
    private fun startSegmentWatch(exoPlayer: ExoPlayer) {
        segmentJob?.cancel()
        segmentJob = lifecycleScope.launch {
            while (isActive) {
                delay(SEGMENT_TICK_MS)
                if (player !== exoPlayer || live != null) continue
                val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: continue
                val position = exoPlayer.currentPosition
                showSkip(skippable(position, duration))
                updateNextCard(exoPlayer, position, duration)
            }
        }
    }

    /** An intro, recap or preview playing now with more than a few seconds of it left. */
    private fun skippable(position: Long, duration: Long): Segment? = segments.firstOrNull {
        it.type != SegmentType.CREDITS && it.contains(position) && (it.endMs ?: duration) - position > SKIP_MIN_LEFT_MS
    }

    private fun showSkip(segment: Segment?) {
        val view = findViewById<TextView>(R.id.player_skip)
        if (segment == null) {
            view.visibility = View.GONE
            return
        }
        view.text = when (segment.type) {
            SegmentType.RECAP -> getString(R.string.player_skip_recap)
            SegmentType.PREVIEW -> getString(R.string.player_skip_preview)
            else -> getString(R.string.player_skip_intro)
        }
        view.visibility = View.VISIBLE
    }

    private fun updateNextCard(exoPlayer: ExoPlayer, position: Long, duration: Long) {
        val card = findViewById<View>(R.id.player_next_card)
        val creditsStart = segments.firstOrNull { it.type == SegmentType.CREDITS }?.startMs ?: (duration - NEXT_FALLBACK_MS)
        val show = nextUp != null && !nextCardDismissed && position >= creditsStart && duration - position > 1_000
        if (!show) {
            card.visibility = View.GONE
            countdownStartedAt = null
            return
        }
        card.visibility = View.VISIBLE
        findViewById<TextView>(R.id.player_next_title).text = nextTitle.orEmpty()
        val label = findViewById<TextView>(R.id.player_next_label)
        if (!settings.current.autoNextEpisode || !exoPlayer.isPlaying) {
            label.text = getString(R.string.player_next_episode)
            countdownStartedAt = null
            return
        }
        val started = countdownStartedAt ?: System.currentTimeMillis().also { countdownStartedAt = it }
        val left = ((NEXT_COUNTDOWN_MS - (System.currentTimeMillis() - started) + 999) / 1000).coerceAtLeast(0)
        label.text = getString(R.string.player_next_episode_in, left.toInt())
        if (left == 0L) playNextFromCredits(exoPlayer)
    }

    /** From the credits on the episode counts as watched. */
    private fun playNextFromCredits(exoPlayer: ExoPlayer) {
        val next = nextUp ?: return
        val finished = currentPath
        val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET }
        findViewById<View>(R.id.player_next_card).visibility = View.GONE
        nextUp = null
        lifecycleScope.launch {
            if (duration != null) library.saveProgress(finished, duration, duration)
            playEpisode(exoPlayer, next)
        }
    }

    /** While the controls are hidden: OK skips or starts the next episode, Back closes the card. */
    private fun handlePrompts(event: KeyEvent): Boolean {
        if (playerView.isControllerFullyVisible) return false
        val exoPlayer = player ?: return false
        val skip = findViewById<View>(R.id.player_skip).visibility == View.VISIBLE
        val card = findViewById<View>(R.id.player_next_card).visibility == View.VISIBLE
        val ok = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
        val back = event.keyCode == KeyEvent.KEYCODE_BACK
        if (!(ok && (skip || card)) && !(back && card)) return false
        if (event.action != KeyEvent.ACTION_UP) return true
        when {
            back -> {
                nextCardDismissed = true
                findViewById<View>(R.id.player_next_card).visibility = View.GONE
            }
            card -> playNextFromCredits(exoPlayer)
            else -> skippable(exoPlayer.currentPosition, exoPlayer.duration)?.let { segment ->
                exoPlayer.seekTo(segment.endMs ?: exoPlayer.duration)
                showSkip(null)
            }
        }
        return true
    }

    private fun setHeader(label: String, title: String) {
        playerView.findViewById<TextView>(R.id.player_label).text = label
        playerView.findViewById<TextView>(R.id.player_title).text = title
    }

    /** What the audio and subtitle pills show: the selected tracks, named like in the pickers. */
    private fun showTracks(tracks: Tracks) {
        fun selected(type: Int) = tracks.groups.filter { it.type == type }.firstNotNullOfOrNull { group ->
            (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) }
        }
        val hasText = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
        playerView.findViewById<TextView>(R.id.player_audio_value).text =
            selected(C.TRACK_TYPE_AUDIO)?.let { DetailedTrackNameProvider.getTrackName(it) } ?: "—"
        playerView.findViewById<TextView>(R.id.player_subtitle_value).text =
            selected(C.TRACK_TYPE_TEXT)?.let { DetailedTrackNameProvider.getTrackName(it) } ?: getString(R.string.player_off)
        playerView.findViewById<View>(R.id.player_subtitle).visibility = if (hasText) View.VISIBLE else View.GONE
    }

    private var hideJob: Job? = null

    /**
     * Media3's own hide animation takes the controls down in two steps, the time bar first. Instead,
     * its animation and timeout are off, and after [CONTROLLER_TIMEOUT_MS] without a key press the
     * whole controller fades out at once (only while playing, as Media3 does).
     */
    private fun installControllerFade() {
        playerView.setControllerAnimationEnabled(false)
        playerView.controllerShowTimeoutMs = 0
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            if (visibility == View.VISIBLE) {
                controllerView()?.apply { animate().cancel(); alpha = 1f }
                scheduleControllerHide()
            } else {
                hideJob?.cancel()
            }
        })
    }

    private fun controllerView(): View? = playerView.findViewById(androidx.media3.ui.R.id.exo_controller)

    private fun scheduleControllerHide() {
        hideJob?.cancel()
        controllerView()?.apply { animate().cancel(); alpha = 1f }
        hideJob = lifecycleScope.launch {
            delay(CONTROLLER_TIMEOUT_MS)
            while (player?.isPlaying != true) delay(500) // Paused: stay until playback goes on.
            val view = controllerView() ?: return@launch
            if (!playerView.isControllerFullyVisible) return@launch
            view.animate().alpha(0f).setDuration(CONTROLLER_FADE_MS).withEndAction {
                playerView.hideController()
                view.alpha = 1f
            }.start()
        }
    }

    private fun installControls() {
        playerView.findViewById<View>(R.id.player_audio).setOnClickListener { showTrackDialog(C.TRACK_TYPE_AUDIO) }
        playerView.findViewById<View>(R.id.player_subtitle).setOnClickListener { showTrackDialog(C.TRACK_TYPE_TEXT) }
        playerView.findViewById<View>(R.id.player_settings).setOnClickListener { showSettingsMenu() }
        playerView.findViewById<View>(R.id.player_next).setOnClickListener { skipToNext() }
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
            .setTitle(R.string.player_settings)
            .setItems(arrayOf(getString(R.string.player_audio), getString(R.string.player_subtitles), getString(R.string.player_speed))) { _, which ->
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
            Toast.makeText(this, getString(if (isText) R.string.player_no_subtitles else R.string.player_no_audio), Toast.LENGTH_SHORT).show()
            return
        }
        TrackSelectionDialogBuilder(this, getString(if (isText) R.string.player_subtitles else R.string.player_audio), player, trackType)
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
        val labels = speeds.map { if (it == 1f) getString(R.string.player_speed_normal) else String.format(AppLocale.current, "%s×", java.text.NumberFormat.getInstance(AppLocale.current).format(it)) }.toTypedArray()
        val current = speeds.indexOfFirst { it == player.playbackParameters.speed }
        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(R.string.player_speed)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                player.setPlaybackSpeed(speeds[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun releasePlayer() {
        progressJob?.cancel()
        segmentJob?.cancel()
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
        private const val SEEK_BACK_MS = 10_000L
        private const val SEGMENT_TICK_MS = 500L
        private const val CONTROLLER_TIMEOUT_MS = 4_000L
        private const val CONTROLLER_FADE_MS = 300L
        private const val SKIP_MIN_LEFT_MS = 3_000L
        private const val NEXT_FALLBACK_MS = 30_000L
        private const val NEXT_COUNTDOWN_MS = 10_000L
        private const val SEEK_FORWARD_MS = 30_000L
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
