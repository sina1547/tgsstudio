package ir.sina.tgsstudio.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object StudioColors {
    val Background = Color(0xFF0B1220)   // deep navy
    val Panel = Color(0xFF141C2E)
    val PanelRaised = Color(0xFF1C2740)
    val Accent = Color(0xFF2DE2D0)       // electric teal
    val TextPrimary = Color(0xFFE6ECF8)
    val TextMuted = Color(0xFF8C9AB8)
    val Danger = Color(0xFFFF6B7A)
}

private val Scheme = darkColorScheme(
    primary = StudioColors.Accent,
    onPrimary = StudioColors.Background,
    background = StudioColors.Background,
    onBackground = StudioColors.TextPrimary,
    surface = StudioColors.Panel,
    onSurface = StudioColors.TextPrimary,
    surfaceVariant = StudioColors.PanelRaised,
    onSurfaceVariant = StudioColors.TextMuted,
    error = StudioColors.Danger,
)

@Composable
fun StudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
