package dev.wardar.scrollapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The app is dark-only; these are the surfaces and ink the stats chart was validated against. */
object Ink {
    val Page = Color(0xFF0D0D0D)
    val Surface = Color(0xFF1A1A19)
    val Primary = Color(0xFFFFFFFF)
    val Secondary = Color(0xFFC3C2B7)
    val Muted = Color(0xFF898781)
    val Grid = Color(0xFF2C2C2A)
    val Baseline = Color(0xFF383835)
    val Accent = Color(0xFF3987E5)
    val Heart = Color(0xFFFF4D67)
    val Wash = Color(0x14FFFFFF)
}

@Composable
fun ScrollAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ink.Accent,
            onPrimary = Ink.Primary,
            background = Ink.Page,
            onBackground = Ink.Primary,
            surface = Ink.Surface,
            onSurface = Ink.Primary,
            onSurfaceVariant = Ink.Secondary,
            surfaceContainer = Ink.Surface,
            outline = Ink.Baseline,
        ),
        content = content,
    )
}
