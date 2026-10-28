package dev.wardar.scrollapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.webkit.CookieManager
import dev.wardar.scrollapp.Platform
import dev.wardar.scrollapp.feed.FeedController
import dev.wardar.scrollapp.settings.SettingsStore
import dev.wardar.scrollapp.stats.StatsRepository

private enum class Screen { Feed, Stats, Settings }

/**
 * The feed is always composed underneath (its WebViews must stay attached); the other screens
 * cover it, and the feed pauses while covered.
 */
@Composable
fun AppRoot(feed: FeedController, settings: SettingsStore, stats: StatsRepository) {
    var screen by rememberSaveable { mutableStateOf(if (settings.onboarded) Screen.Feed else Screen.Settings) }
    var login by remember { mutableStateOf<Platform?>(null) }

    // Registered first so the screens' own back handlers (composed later) take priority.
    BackHandler(enabled = screen != Screen.Feed) {
        settings.onboarded = true
        screen = Screen.Feed
    }
    LaunchedEffect(screen, login) { feed.setCovered(screen != Screen.Feed || login != null) }

    Box(Modifier.fillMaxSize().background(Ink.Page)) {
        FeedScreen(
            feed = feed,
            onStats = { screen = Screen.Stats },
            onSettings = { screen = Screen.Settings },
        )
        when (screen) {
            Screen.Stats -> StatsScreen(stats, onBack = { screen = Screen.Feed })
            Screen.Settings -> SettingsScreen(
                feed = feed,
                settings = settings,
                onLogin = { login = it },
                onBack = {
                    settings.onboarded = true
                    screen = Screen.Feed
                },
            )
            Screen.Feed -> Unit
        }
        login?.let { platform ->
            LoginScreen(platform, onDone = {
                CookieManager.getInstance().flush()
                login = null
                feed.reload(platform)
            })
        }
    }
}
