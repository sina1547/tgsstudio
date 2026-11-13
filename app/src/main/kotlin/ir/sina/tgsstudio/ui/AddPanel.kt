package ir.sina.tgsstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.sina.tgsstudio.editor.Creator
import ir.sina.tgsstudio.editor.Skeleton
import ir.sina.tgsstudio.renderer.Rgba

@Composable
private fun AddTile(ic: Ic, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.height(78.dp).clip(RoundedCornerShape(14.dp)).background(StudioColors.PanelRaised).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(ic, StudioColors.TextPrimary, 26.dp)
        Text(label, color = StudioColors.TextMuted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun AddPanel(state: EditorState, onPickSvg: () -> Unit, onText: () -> Unit) {
    val w = state.session.doc.width / 2.0; val h = state.session.doc.height / 2.0
    fun add(block: ir.sina.tgsstudio.editor.EditorSession.() -> Unit) { state.act(block); state.tool = Tool.SELECT; state.updatePanel(PanelTab.PROPERTIES) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Add, StudioColors.Accent, 20.dp)
            Text(tr("Add", "افزودن"), color = StudioColors.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp))
        }
        Text(tr("Tap to place at the center, or pick a tool in the toolbar and drag on the canvas.", "برای قرار دادن در مرکز بزنید، یا از نوار ابزار یک ابزار انتخاب کنید و روی بوم بکشید."), color = StudioColors.TextMuted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AddTile(Ic.Rect, tr("Rectangle", "مستطیل"), { add { addRect(w, h, 220.0, 160.0) } }, Modifier.weight(1f))
            AddTile(Ic.Ellipse, tr("Ellipse", "بیضی"), { add { addEllipse(w, h, 200.0, 200.0) } }, Modifier.weight(1f))
            AddTile(Ic.Star, tr("Star", "ستاره"), { add { addStar(w, h, 110.0) } }, Modifier.weight(1f))
            AddTile(Ic.Polygon, tr("Polygon", "چندضلعی"), { add { addPolygon(w, h, 110.0) } }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AddTile(Ic.Pen, tr("Pen path", "مسیر آزاد"), { state.tool = Tool.PEN; state.updatePanel(null); state.changed() }, Modifier.weight(1f))
            AddTile(Ic.Text, tr("Text", "متن"), onText, Modifier.weight(1f))
            AddTile(Ic.Svg, "SVG", onPickSvg, Modifier.weight(1f))
            AddTile(Ic.NullLayer, tr("Null", "لایهٔ خالی"), {
                state.act { edit("Add null") { val l = Creator.nullLayer(doc, doc.root.arr("layers"), "Null"); Creator.insertLayer(doc, null, l) } }
            }, Modifier.weight(1f))
        }
    }
}

/** Text becomes vector strokes: the glyphs are rasterised, reduced to a centerline and added as stroked paths. */
@Composable
fun TextDialog(state: EditorState, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var bold by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf(Rgba(1.0, 1.0, 1.0, 1.0)) }
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val noText = tr("Couldn't turn this text into strokes.", "نتوانستم این متن را به خط تبدیل کنم.")
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = StudioColors.Panel,
        title = { Text(tr("Add text", "افزودن متن"), color = StudioColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { text = it; error = null }, singleLine = true, label = { Text(tr("Text", "متن")) })
                Text(tr("Added as a single hand-drawn style stroke (centerline of the letters), not a filled outline. TGS stickers can't contain live text.", "به‌صورت یک خط دست‌نویس (خط مرکزی حروف) اضافه می‌شود، نه تو‌پر. استیکر TGS متن زنده ندارد."), color = StudioColors.TextMuted, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Swatch(color.toColor(), { picking = true })
                    SmallButton(tr("Bold", "ضخیم"), { bold = !bold }, primary = bold)
                }
                error?.let { Text(it, color = StudioColors.Danger, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton({
                val mask = TextRaster.mask(text, 220f, bold)
                val lines = mask?.let { Skeleton.centerline(it) }
                if (mask == null || lines == null || lines.paths.isEmpty()) { error = noText }
                else {
                    val scale = minOf(380.0 / mask.width, 200.0 / mask.height).coerceAtMost(1.0)
                    state.act { addCenterlineText(lines, scale, color, text.take(24)) }
                    state.updatePanel(PanelTab.PROPERTIES); onDismiss()
                }
            }) { Text(tr("Add", "افزودن"), color = StudioColors.Accent) }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "انصراف"), color = StudioColors.TextMuted) } },
    )
    if (picking) ColorPickerDialog(color, { color = it }, { picking = false })
}
