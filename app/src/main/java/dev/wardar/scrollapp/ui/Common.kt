package dev.wardar.scrollapp.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wardar.scrollapp.R

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(R.drawable.ic_back, "Back", onBack)
        Text(
            title,
            color = Ink.Primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        action()
    }
}

@Composable
fun IconAction(@DrawableRes icon: Int, label: String, onClick: () -> Unit, tint: Color = Ink.Primary) {
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.Surface)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Ink.Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** A row of mutually exclusive options. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ink.Page).padding(3.dp),
    ) {
        for (option in options) {
            val on = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Ink.Baseline else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    color = if (on) Ink.Primary else Ink.Secondary,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun Dot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun Gap(height: Int) = Spacer(Modifier.height(height.dp))
