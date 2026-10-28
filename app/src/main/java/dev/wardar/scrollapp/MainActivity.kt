package dev.wardar.scrollapp

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.wardar.scrollapp.feed.FeedController
import dev.wardar.scrollapp.settings.SettingsStore
import dev.wardar.scrollapp.stats.StatsRepository
import dev.wardar.scrollapp.ui.AppRoot
import dev.wardar.scrollapp.ui.ScrollAppTheme

class MainActivity : ComponentActivity() {
    private lateinit var feed: FeedController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Lets chrome://inspect attach to the feeds in debug builds, to fix selectors when sites change.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
        // Autoscroll keeps the feed going hands-free, so the screen must not time out mid-video.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val settings = SettingsStore(this)
        val stats = StatsRepository(this)
        feed = FeedController(this, settings, stats)
        setContent {
            ScrollAppTheme {
                AppRoot(feed, settings, stats)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        feed.setForeground(true)
    }

    override fun onPause() {
        feed.setForeground(false)
        super.onPause()
    }

    override fun onDestroy() {
        feed.destroy()
        super.onDestroy()
    }
}
