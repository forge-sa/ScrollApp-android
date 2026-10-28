package dev.wardar.scrollapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wardar.scrollapp.Platform
import dev.wardar.scrollapp.feed.FeedController
import dev.wardar.scrollapp.settings.MixMode
import dev.wardar.scrollapp.settings.SettingsStore

@Composable
fun SettingsScreen(
    feed: FeedController,
    settings: SettingsStore,
    onLogin: (Platform) -> Unit,
    onBack: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = Ink.Page) {
        Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState())) {
            ScreenHeader(if (settings.onboarded) "Settings" else "Welcome", onBack, action = {
                TextButton(onClick = onBack) { Text("Done") }
            })

            if (!settings.onboarded) {
                Card {
                    Text("One feed for Shorts, Reels and TikToks", color = Ink.Primary, fontWeight = FontWeight.SemiBold)
                    Gap(6)
                    Text(
                        "Log in to each app below so the feed shows your own recommendations. " +
                            "Instagram and TikTok usually need an account; YouTube Shorts works without one. " +
                            "Then tap Done and swipe up.",
                        color = Ink.Secondary,
                        fontSize = 14.sp,
                    )
                }
            }

            SectionLabel("Sources")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (platform in Platform.entries) SourceCard(platform, feed, settings, onLogin)
            }

            SectionLabel("Feed")
            Card {
                ToggleRow("Autoscroll", "Move to the next video when one ends", feed.autoscroll) {
                    feed.toggleAutoscroll()
                }
                HorizontalDivider(color = Ink.Grid, modifier = Modifier.padding(vertical = 8.dp))
                var skipAds by remember { mutableStateOf(settings.skipAds) }
                ToggleRow("Skip ads and filler", "Ads, LIVE streams, people suggestions and Threads posts", skipAds) {
                    skipAds = it
                    settings.skipAds = it
                }
                HorizontalDivider(color = Ink.Grid, modifier = Modifier.padding(vertical = 8.dp))
                Text("Mixing", color = Ink.Primary, fontSize = 16.sp)
                Gap(8)
                var mix by remember { mutableStateOf(settings.mixMode) }
                Segmented(MixMode.entries, mix, { it.label }) {
                    mix = it
                    settings.mixMode = it
                }
            }

            SectionLabel("How to use")
            Card {
                Help("Swipe up / down", "Next / previous video, across all sources")
                Help("Like", "Heart button below the video (double-tap also works on Reels and TikTok)")
                Help("Repost", "Uses the app's own repost when it has one, otherwise opens the share sheet")
                Help("Source pill", "Tap the colored name in the bottom bar to reload that source")
            }
            Gap(24)
        }
    }
}

@Composable
private fun SourceCard(platform: Platform, feed: FeedController, settings: SettingsStore, onLogin: (Platform) -> Unit) {
    var enabled by remember { mutableStateOf(settings.isEnabled(platform)) }
    var desktop by remember { mutableStateOf(settings.isDesktop(platform)) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(platform.color)
            Spacer(Modifier.width(10.dp))
            Text(platform.title, color = Ink.Primary, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                feed.setPlatformEnabled(platform, it)
            })
        }
        if (enabled) {
            Gap(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onLogin(platform) }, modifier = Modifier.weight(1f)) { Text("Log in") }
                OutlinedButton(onClick = { feed.reload(platform) }, modifier = Modifier.weight(1f)) { Text("Reload") }
            }
            Gap(4)
            ToggleRow("Desktop site", "Try this if the mobile site won't show the feed", desktop) {
                desktop = it
                feed.setDesktopMode(platform, it)
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Ink.Primary, fontSize = 16.sp)
            Text(subtitle, color = Ink.Secondary, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Help(title: String, body: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title, color = Ink.Primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(body, color = Ink.Secondary, fontSize = 14.sp)
    }
}
