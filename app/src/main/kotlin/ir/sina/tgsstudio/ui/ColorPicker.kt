package ir.sina.tgsstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.sina.tgsstudio.renderer.Rgba

private val PALETTE = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFF3B5C, 0xFFFF8A3D, 0xFFFFD23F, 0xFF3DDC84, 0xFF1ED6C4, 0xFF1E7BFF, 0xFF7C4DDB, 0xFFFF6FB5, 0xFF8795B4, 0xFF5A3A22,
).map { Color(it) }

private fun Color.toHsv(): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()), it) }

fun Rgba.toColor() = Color(r.toFloat().coerceIn(0f, 1f), g.toFloat().coerceIn(0f, 1f), b.toFloat().coerceIn(0f, 1f), a.toFloat().coerceIn(0f, 1f))

/** HSV color picker with a hex field and a small palette. Calls [onPick] live while the user changes the color. */
@Composable
fun ColorPickerDialog(initial: Rgba, onPick: (Rgba) -> Unit, onDone: () -> Unit) {
    val init = remember { initial.toColor().toHsv() }
    var h by remember { mutableFloatStateOf(init[0]) }
    var s by remember { mutableFloatStateOf(init[1]) }
    var v by remember { mutableFloatStateOf(init[2]) }
    var hex by remember { mutableStateOf("") }
    fun current() = Color.hsv(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
    fun push() { val c = current(); hex = "%02X%02X%02X".format((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()); onPick(Rgba(c.red.toDouble(), c.green.toDouble(), c.blue.toDouble(), 1.0)) }
    fun setFrom(c: Color) { val hsv = c.toHsv(); h = hsv[0]; s = hsv[1]; v = hsv[2]; push() }
    val colors = SliderDefaults.colors(thumbColor = StudioColors.Accent, activeTrackColor = StudioColors.Accent, inactiveTrackColor = StudioColors.PanelHigh)

    AlertDialog(
        onDismissRequest = onDone,
        containerColor = StudioColors.Panel,
        title = { Text(tr("Color", "رنگ"), color = StudioColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(current()))
                Text(tr("Hue", "رنگ‌مایه"), color = StudioColors.TextMuted, fontSize = 12.sp)
                Slider(h, { h = it; push() }, valueRange = 0f..360f, colors = colors)
                Text(tr("Saturation", "اشباع"), color = StudioColors.TextMuted, fontSize = 12.sp)
                Slider(s, { s = it; push() }, valueRange = 0f..1f, colors = colors)
                Text(tr("Brightness", "روشنایی"), color = StudioColors.TextMuted, fontSize = 12.sp)
                Slider(v, { v = it; push() }, valueRange = 0f..1f, colors = colors)
                OutlinedTextField(
                    value = hex, onValueChange = { t ->
                        hex = t.filter { it.isLetterOrDigit() }.take(6)
                        if (hex.length == 6) hex.toLongOrNull(16)?.let { setFromRgb(it) { c -> val hsv = c.toHsv(); h = hsv[0]; s = hsv[1]; v = hsv[2]; onPick(Rgba(c.red.toDouble(), c.green.toDouble(), c.blue.toDouble(), 1.0)) } }
                    },
                    label = { Text("HEX") }, singleLine = true,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PALETTE.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { c -> Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(c).clickable { setFrom(c) }) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDone) { Text(tr("Done", "تأیید"), color = StudioColors.Accent) } },
    )
}

private fun setFromRgb(rgb: Long, apply: (Color) -> Unit) = apply(Color(0xFF000000 or rgb))
