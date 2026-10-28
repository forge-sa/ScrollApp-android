package dev.wardar.scrollapp.ui

import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.wardar.scrollapp.R
import dev.wardar.scrollapp.feed.FeedController

@Composable
fun FeedScreen(feed: FeedController, onStats: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { feed.root.also { (it.parent as? ViewGroup)?.removeView(it) } },
                modifier = Modifier.fillMaxSize(),
            )
            if (feed.current == null) NoSources(onSettings)
            LikeBurst(feed.likeBurst)
            Notice(feed.notice)
        }
        BottomBar(feed, onStats, onSettings)
    }
}

@Composable
private fun BottomBar(feed: FeedController, onStats: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).background(Ink.Page).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourcePill(feed)
        Spacer(Modifier.weight(1f))
        val liked = feed.liked == true
        BarButton(
            icon = if (liked) R.drawable.ic_like else R.drawable.ic_like_outline,
            label = if (liked) "Unlike" else "Like",
            tint = if (liked) Ink.Heart else Ink.Primary,
            onClick = feed::like,
        )
        BarButton(R.drawable.ic_repost, "Repost or share", onClick = feed::repost)
        BarButton(
            icon = R.drawable.ic_autoscroll,
            label = if (feed.autoscroll) "Autoscroll is on" else "Autoscroll is off",
            tint = if (feed.autoscroll) Ink.Primary else Ink.Muted,
            highlighted = feed.autoscroll,
            onClick = feed::toggleAutoscroll,
        )
        BarButton(
            icon = if (feed.sound) R.drawable.ic_volume_on else R.drawable.ic_volume_off,
            label = if (feed.sound) "Mute" else "Unmute",
            onClick = feed::toggleSound,
        )
        BarButton(R.drawable.ic_stats, "Statistics", onClick = onStats)
        BarButton(R.drawable.ic_settings, "Settings and accounts", onClick = onSettings)
    }
}

/** Which source is on screen; tap it to reload that source. */
@Composable
private fun SourcePill(feed: FeedController) {
    val platform = feed.current ?: return
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Ink.Surface)
                .clickable { menu = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(platform.color))
            Spacer(Modifier.width(6.dp))
            Text(platform.shortName, color = Ink.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Reload ${platform.title}") },
                onClick = {
                    menu = false
                    feed.reload(platform)
                },
            )
        }
    }
}

@Composable
private fun BarButton(
    @DrawableRes icon: Int,
    label: String,
    tint: Color = Ink.Primary,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (highlighted) Ink.Wash else Color.Transparent)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun BoxScope.LikeBurst(count: Int) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(count) {
        if (count == 0) return@LaunchedEffect
        scale.snapTo(0.5f)
        scale.animateTo(1.15f, tween(180))
        scale.animateTo(0f, tween(320, delayMillis = 250))
    }
    if (scale.value > 0f) {
        Icon(
            painterResource(R.drawable.ic_like),
            contentDescription = null,
            tint = Ink.Heart,
            modifier = Modifier
                .align(Alignment.Center)
                .size(120.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    alpha = scale.value.coerceAtMost(1f)
                },
        )
    }
}

@Composable
private fun BoxScope.Notice(message: String?) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 16.dp, end = 16.dp),
    ) {
        Text(
            text = message.orEmpty(),
            color = Ink.Primary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xCC000000))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun BoxScope.NoSources(onSettings: () -> Unit) {
    Column(
        modifier = Modifier.align(Alignment.Center).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("No sources turned on", color = Ink.Primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Turn on YouTube Shorts, Instagram Reels or TikTok to fill your feed.",
            color = Ink.Secondary,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onSettings) { Text("Open settings") }
    }
}
