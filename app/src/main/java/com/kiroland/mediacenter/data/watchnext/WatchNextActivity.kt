package com.kiroland.mediacenter.data.watchnext

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.kiroland.mediacenter.data.library.db.LibraryDao
import com.kiroland.mediacenter.player.PlayerActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Entry point for Watch Next items on the TV home screen (mymedia://play?path=…). Exported, so it only
 * starts playback of files that are in the library; anything else just opens the app.
 */
@AndroidEntryPoint
class WatchNextActivity : ComponentActivity() {
    // The app's own language (settings), not necessarily the TV's.
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.kiroland.mediacenter.util.AppLocale.wrap(newBase))
    }


    @Inject lateinit var dao: LibraryDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent?.data?.getQueryParameter("path")
        lifecycleScope.launch {
            val known = path != null && dao.media(path) != null
            startActivity(
                if (known) PlayerActivity.intent(this@WatchNextActivity, path!!)
                else packageManager.getLeanbackLaunchIntentForPackage(packageName) ?: Intent(),
            )
            finish()
        }
    }

    companion object {
        fun uriFor(path: String): Uri =
            Uri.Builder().scheme("mymedia").authority("play").appendQueryParameter("path", path).build()
    }
}
