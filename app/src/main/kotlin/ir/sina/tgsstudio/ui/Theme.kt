package ir.sina.tgsstudio.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private data class StudioPalette(
    val background: Color, val panel: Color, val panelRaised: Color, val panelHigh: Color,
    val accent: Color, val accentSoft: Color, val violet: Color, val green: Color,
    val textPrimary: Color, val textMuted: Color, val divider: Color, val danger: Color,
    val warning: Color, val checkerA: Color, val checkerB: Color,
)

private fun palette(
    background: Long, panel: Long, raised: Long, high: Long, accent: Long, accentSoft: Long,
    violet: Long, green: Long, primary: Long, muted: Long, divider: Long, danger: Long,
    warning: Long, checkerA: Long, checkerB: Long,
) = StudioPalette(
    Color(background), Color(panel), Color(raised), Color(high), Color(accent), Color(accentSoft),
    Color(violet), Color(green), Color(primary), Color(muted), Color(divider), Color(danger),
    Color(warning), Color(checkerA), Color(checkerB),
)

/** Theme tokens are read by every existing screen, so theme changes apply app-wide. */
object StudioColors {
    private val dark = palette(0xFF101010,0xFF1A1A1A,0xFF242424,0xFF303030,0xFF00C878,0xFF123A2B,0xFF9A78F2,0xFF43C59A,0xFFF2F2F2,0xFFA0A0A0,0xFF353535,0xFFFF7185,0xFFFFC46B,0xFF303030,0xFF242424)
    private val light = palette(0xFFF1F4F9,0xFFFFFFFF,0xFFE8EDF5,0xFFDCE4EF,0xFF246FE5,0xFFDCEAFF,0xFF7046C8,0xFF168653,0xFF172338,0xFF66758C,0xFFD4DDE9,0xFFD83D55,0xFFAA6900,0xFFE1E6EF,0xFFF0F3F8)
    private val neon = palette(0xFF080812,0xFF111122,0xFF1B1B35,0xFF29294A,0xFF00E5FF,0xFF123C4A,0xFFD84DFF,0xFF00D68F,0xFFF1F0FF,0xFF9A9AB8,0xFF343451,0xFFFF5577,0xFFFFC857,0xFF292943,0xFF1C1C32)
    private var active = dark

    fun configure(theme: String, accentHex: String) {
        val base = when (theme) { "light" -> light; "neon" -> neon; "custom" -> dark; else -> dark }
        val customAccent = if (theme == "custom") runCatching {
            Color(android.graphics.Color.parseColor(if (accentHex.startsWith("#")) accentHex else "#$accentHex"))
        }.getOrDefault(base.accent) else base.accent
        active = if (theme == "custom") base.copy(accent = customAccent, accentSoft = customAccent.copy(alpha = 0.22f)) else base
    }

    val Background get() = active.background
    val Panel get() = active.panel
    val PanelRaised get() = active.panelRaised
    val PanelHigh get() = active.panelHigh
    val Accent get() = active.accent
    val AccentSoft get() = active.accentSoft
    val Violet get() = active.violet
    val Green get() = active.green
    val TextPrimary get() = active.textPrimary
    val TextMuted get() = active.textMuted
    val Divider get() = active.divider
    val Danger get() = active.danger
    val Warning get() = active.warning
    val CheckerA get() = active.checkerA
    val CheckerB get() = active.checkerB
}

@Composable
fun StudioTheme(theme: String = "dark", accentHex: String = "1E7BFF", content: @Composable () -> Unit) {
    StudioColors.configure(theme, accentHex)
    val c = if (theme == "light") lightColorScheme(
        primary = StudioColors.Accent, onPrimary = Color.White, background = StudioColors.Background,
        onBackground = StudioColors.TextPrimary, surface = StudioColors.Panel, onSurface = StudioColors.TextPrimary,
        surfaceVariant = StudioColors.PanelRaised, onSurfaceVariant = StudioColors.TextMuted, error = StudioColors.Danger,
    ) else darkColorScheme(
        primary = StudioColors.Accent, onPrimary = Color.White, background = StudioColors.Background,
        onBackground = StudioColors.TextPrimary, surface = StudioColors.Panel, onSurface = StudioColors.TextPrimary,
        surfaceVariant = StudioColors.PanelRaised, onSurfaceVariant = StudioColors.TextMuted, error = StudioColors.Danger,
    )
    MaterialTheme(colorScheme = c, content = content)
}
