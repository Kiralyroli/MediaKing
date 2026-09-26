package com.kiroland.mediacenter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kiroland.mediacenter.ui.MediaCenterNavHost
import com.kiroland.mediacenter.ui.theme.MediaCenterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediaCenterTheme {
                MediaCenterNavHost()
            }
        }
    }
}
