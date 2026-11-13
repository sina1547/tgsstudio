package ir.sina.tgsstudio.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Dark navy workspace with an electric-blue accent, following the design mockup. */
object StudioColors {
    val Background = Color(0xFF0A1222)
    val Panel = Color(0xFF111B30)
    val PanelRaised = Color(0xFF18253F)
    val PanelHigh = Color(0xFF21314F)
    val Accent = Color(0xFF1E7BFF)
    val AccentSoft = Color(0xFF123B7A)
    val Violet = Color(0xFF7C4DDB)
    val Green = Color(0xFF33A36B)
    val TextPrimary = Color(0xFFE8EEFA)
    val TextMuted = Color(0xFF8795B4)
    val Divider = Color(0xFF243452)
    val Danger = Color(0xFFFF6B7A)
    val Warning = Color(0xFFFFB84D)
    val CheckerA = Color(0xFF2A3550)
    val CheckerB = Color(0xFF212B42)
}

private val Scheme = darkColorScheme(
    primary = StudioColors.Accent,
    onPrimary = Color.White,
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
