package com.kiroland.mediacenter.data.remote

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.media3.common.Player
import com.kiroland.mediacenter.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * The phone remote (on the upload page): key presses go to whichever MediaKing screen is in front,
 * typed searches open the search. Android does not let an app press keys in other apps, nor open
 * itself from the background, so everything works while MediaKing is on screen.
 */
object RemoteControl {

    enum class Key(val code: Int) {
        UP(KeyEvent.KEYCODE_DPAD_UP),
        DOWN(KeyEvent.KEYCODE_DPAD_DOWN),
        LEFT(KeyEvent.KEYCODE_DPAD_LEFT),
        RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT),
        OK(KeyEvent.KEYCODE_DPAD_CENTER),
        BACK(KeyEvent.KEYCODE_BACK),
        PLAY_PAUSE(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
        REWIND(KeyEvent.KEYCODE_MEDIA_REWIND),
        FORWARD(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD),
        SUBTITLES(KeyEvent.KEYCODE_CAPTIONS),
        MENU(KeyEvent.KEYCODE_MENU),
    }

    /** What the player shows, for the remote's "now playing" line. */
    data class NowPlaying(val title: String, val subtitle: String?, val player: WeakReference<Player>)

    data class Status(val inFront: Boolean, val title: String?, val subtitle: String?, val positionMs: Long?, val durationMs: Long?, val playing: Boolean?)

    @Volatile
    private var front: WeakReference<Activity>? = null

    @Volatile
    var nowPlaying: NowPlaying? = null

    private val _searchRequests = MutableStateFlow<String?>(null)

    /** A search typed on the phone, until the search screen takes it. */
    val searchRequests: StateFlow<String?> = _searchRequests.asStateFlow()

    fun attach(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { front = WeakReference(activity) }
            override fun onActivityPaused(activity: Activity) { if (front?.get() === activity) front = null }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** false when MediaKing is not on screen. */
    fun press(key: Key): Boolean {
        val activity = front?.get() ?: return false
        activity.runOnUiThread {
            val now = SystemClock.uptimeMillis()
            activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key.code, 0))
            activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, key.code, 0))
        }
        return true
    }

    /** Opens the search with [text] (leaving the player if it plays); false when MediaKing is not on screen. */
    fun search(text: String): Boolean {
        val activity = front?.get() ?: return false
        _searchRequests.value = text.trim().take(200)
        if (activity !is MainActivity) {
            activity.runOnUiThread {
                activity.startActivity(
                    Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
        }
        return true
    }

    fun takeSearch(): String? = _searchRequests.value?.also { _searchRequests.value = null }

    suspend fun status(): Status = withContext(Dispatchers.Main) {
        val playing = nowPlaying
        val player = playing?.player?.get()
        Status(
            inFront = front?.get() != null,
            title = playing?.title,
            subtitle = playing?.subtitle,
            positionMs = player?.currentPosition,
            durationMs = player?.duration?.takeIf { it > 0 },
            playing = player?.isPlaying,
        )
    }
}
