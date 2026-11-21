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
import androidx.compose.runtime.mutableIntStateOf
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
    0xFFFFFFFF, 0xFF000000, 0xFFFF3B5C, 0xFFFF8A3D, 0xFFFFD23F, 0xFF3DDC84,
    0xFF1ED6C4, 0xFF1E7BFF, 0xFF7C4DDB, 0xFFFF6FB5, 0xFF8795B4, 0xFF5A3A22,
).map { Color(it) }

private fun Color.toHsv(): FloatArray = FloatArray(3).also {
    android.graphics.Color.colorToHSV(
        android.graphics.Color.rgb((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()), it,
    )
}

fun Rgba.toColor() = Color(
    r.toFloat().coerceIn(0f, 1f),
    g.toFloat().coerceIn(0f, 1f),
    b.toFloat().coerceIn(0f, 1f),
    a.toFloat().coerceIn(0f, 1f),
)

/** HSV picker with synchronized HEX/RGBA inputs and editable opacity. */
@Composable
fun ColorPickerDialog(initial: Rgba, onPick: (Rgba) -> Unit, onDone: () -> Unit) {
    val initialColor = remember { initial.toColor() }
    val init = remember { initialColor.toHsv() }
    var h by remember { mutableFloatStateOf(init[0]) }
    var s by remember { mutableFloatStateOf(init[1]) }
    var v by remember { mutableFloatStateOf(init[2]) }
    var alpha by remember { mutableFloatStateOf(initialColor.alpha) }
    var red by remember { mutableIntStateOf((initialColor.red * 255).toInt()) }
    var green by remember { mutableIntStateOf((initialColor.green * 255).toInt()) }
    var blue by remember { mutableIntStateOf((initialColor.blue * 255).toInt()) }
    var hex by remember { mutableStateOf("%02X%02X%02X%02X".format(red, green, blue, (alpha * 255).toInt())) }

    fun publish(color: Color) {
        val c = color.copy(alpha = alpha.coerceIn(0f, 1f))
        red = (c.red * 255).toInt().coerceIn(0, 255)
        green = (c.green * 255).toInt().coerceIn(0, 255)
        blue = (c.blue * 255).toInt().coerceIn(0, 255)
        hex = "%02X%02X%02X%02X".format(red, green, blue, (c.alpha * 255).toInt())
        onPick(Rgba(c.red.toDouble(), c.green.toDouble(), c.blue.toDouble(), c.alpha.toDouble()))
    }
    fun current() = Color.hsv(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f), alpha.coerceIn(0f, 1f))
    fun updateHsv(color: Color) {
        val hsv = color.toHsv()
        h = hsv[0]; s = hsv[1]; v = hsv[2]
        alpha = color.alpha
        publish(color)
    }
    fun updateRgb(r: Int = red, g: Int = green, b: Int = blue, a: Int = (alpha * 255).toInt()) {
        red = r.coerceIn(0, 255); green = g.coerceIn(0, 255); blue = b.coerceIn(0, 255)
        alpha = a.coerceIn(0, 255) / 255f
        val color = Color(red, green, blue, (alpha * 255).toInt())
        val hsv = color.toHsv()
        h = hsv[0]; s = hsv[1]; v = hsv[2]
        publish(color)
    }
    val sliderColors = SliderDefaults.colors(
        thumbColor = StudioColors.Accent,
        activeTrackColor = StudioColors.Accent,
        inactiveTrackColor = StudioColors.PanelHigh,
    )

    AlertDialog(
        onDismissRequest = onDone,
        containerColor = StudioColors.Panel,
        title = { Text(tr("Color", "رنگ"), color = StudioColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(current()))
                Text(tr("Hue", "رنگ‌مایه"), color = StudioColors.TextMuted, fontSize = 12.sp)
                Slider(h, { h = it; publish(current()) }, valueRange = 0f..360f, colors = sliderColors)
                Text(tr("Saturation", "اشباع"), color = StudioColors.TextMuted, fontSize = 12.sp)
                Slider(s, { s = it; publish(current()) }, valueRange = 0f..1f, colors = sliderColors)
                Text(tr("Brightness", "روشنایی"), color = StudioColors.TextMuted, fontSize = 12.sp)
                Slider(v, { v = it; publish(current()) }, valueRange = 0f..1f, colors = sliderColors)
                Text(tr("Opacity", "شفافیت"), color = StudioColors.TextMuted, fontSize = 12.sp)
                Slider(alpha, { alpha = it; publish(current()) }, valueRange = 0f..1f, colors = sliderColors)

                OutlinedTextField(
                    value = hex,
                    onValueChange = { input ->
                        val clean = input.removePrefix("#").filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }.take(8).uppercase()
                        hex = clean
                        if (clean.length == 6 || clean.length == 8) {
                            val rgb = clean.take(6).toLongOrNull(16)
                            val a = if (clean.length == 8) clean.substring(6, 8).toIntOrNull(16) else 255
                            if (rgb != null && a != null) updateRgb(
                                r = ((rgb shr 16) and 255).toInt(),
                                g = ((rgb shr 8) and 255).toInt(),
                                b = (rgb and 255).toInt(),
                                a = a,
                            )
                        }
                    },
                    label = { Text("HEX / RGBA") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "R" to red, "G" to green, "B" to blue, "A" to (alpha * 100).toInt(),
                    ).forEach { (label, value) ->
                        OutlinedTextField(
                            value = value.toString(),
                            onValueChange = { input ->
                                val parsed = input.filter(Char::isDigit).take(3).toIntOrNull() ?: return@OutlinedTextField
                                when (label) {
                                    "R" -> updateRgb(r = parsed)
                                    "G" -> updateRgb(g = parsed)
                                    "B" -> updateRgb(b = parsed)
                                    "A" -> updateRgb(a = (parsed.coerceIn(0, 100) * 255 / 100))
                                }
                            },
                            label = { Text(label) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PALETTE.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { color ->
                                Box(
                                    Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(color)
                                        .clickable { updateHsv(color) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onDone) { Text(tr("Done", "تأیید"), color = StudioColors.Accent) }
        },
    )
}
