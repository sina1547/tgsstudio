package ir.sina.tgsstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun IconBtn(ic: Ic, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, active: Boolean = false, size: Dp = 44.dp, iconSize: Dp = 22.dp, tint: Color? = null) {
    val color = tint ?: when { !enabled -> StudioColors.TextMuted.copy(alpha = 0.4f); active -> Color.White; else -> StudioColors.TextPrimary }
    Box(
        modifier.size(size).clip(RoundedCornerShape(12.dp)).background(if (active) StudioColors.Accent else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(ic, color, iconSize) }
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false, ic: Ic? = null) {
    Row(
        modifier.height(38.dp).clip(RoundedCornerShape(10.dp))
            .background(if (primary) StudioColors.Accent else StudioColors.PanelHigh)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (ic != null) Icon(ic, if (enabled) Color.White else StudioColors.TextMuted, 18.dp)
        Text(text, color = if (enabled) Color.White else StudioColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun SectionHeader(title: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = StudioColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(if (open) Ic.ChevronDown else Ic.Chevron, StudioColors.TextMuted, 18.dp)
    }
}

/** ASCII digits and '.', whatever digits the user's keyboard produced (Persian/Arabic-Indic included). */
fun normalizeNumber(s: String): String = buildString {
    for (ch in s.trim()) append(
        when (ch) {
            in '۰'..'۹' -> '0' + (ch - '۰'); in '٠'..'٩' -> '0' + (ch - '٠')
            '٫', '،', ',' -> '.'; '−', '–' -> '-'; else -> ch
        },
    )
}

fun fmt(v: Double, decimals: Int = 1): String = String.format(Locale.US, "%.${decimals}f", v).let { s -> if (decimals > 0) s.trimEnd('0').trimEnd('.') else s }.ifEmpty { "0" }

@Composable
fun NumberField(value: Double, onCommit: (Double) -> Unit, modifier: Modifier = Modifier, decimals: Int = 1, label: String? = null, suffix: String = "") {
    var text by remember(value) { mutableStateOf(fmt(value, decimals)) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun commit() {
        val n = normalizeNumber(text).toDoubleOrNull()
        if (n != null && n.isFinite()) { if (kotlin.math.abs(n - value) > 1e-9) onCommit(n) } else text = fmt(value, decimals)
    }
    Ltr {
        Row(
            modifier.height(38.dp).clip(RoundedCornerShape(10.dp)).background(StudioColors.PanelHigh).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (label != null) Text(label, color = StudioColors.TextMuted, fontSize = 12.sp, modifier = Modifier.width(18.dp))
            BasicTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                textStyle = TextStyle(color = StudioColors.TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(StudioColors.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit(); focus.clearFocus() }),
                modifier = Modifier.weight(1f).onFocusChanged { f -> if (focused && !f.isFocused) commit(); focused = f.isFocused },
            )
            if (suffix.isNotEmpty()) Text(suffix, color = StudioColors.TextMuted, fontSize = 12.sp)
        }
    }
}

/** Slider that reports one undo step per drag: [onStart] when it begins, [onChange] while moving, [onEnd] on release. */
@Composable
fun GestureSlider(value: Float, range: ClosedFloatingPointRange<Float>, onStart: () -> Unit, onChange: (Float) -> Unit, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember(value) { mutableStateOf(value) }
    Slider(
        value = if (dragging) local else value, valueRange = range,
        onValueChange = { v -> if (!dragging) { dragging = true; onStart() }; local = v; onChange(v) },
        onValueChangeFinished = { dragging = false; onEnd() },
        colors = SliderDefaults.colors(thumbColor = StudioColors.Accent, activeTrackColor = StudioColors.Accent, inactiveTrackColor = StudioColors.PanelHigh),
        modifier = modifier,
    )
}

@Composable
fun Swatch(color: Color?, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp)).border(1.dp, StudioColors.Divider, RoundedCornerShape(10.dp))
            .background(color ?: StudioColors.PanelHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (color == null) Icon(Ic.Plus, StudioColors.TextMuted, 18.dp) }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, color = StudioColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        content()
    }
}
