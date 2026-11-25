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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    val selectLayersHint = tr("Select one or more layers first.", "ابتدا یک یا چند لایه را انتخاب کنید.")
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
        Text(tr("Objects & groups", "آبجکت‌ها و گروه‌ها"), color = StudioColors.TextMuted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AddTile(Ic.Link, tr("Linked Null", "Null متصل"), {
                val children = state.session.selection.map { it.layer }.distinct()
                if (children.isNotEmpty()) state.act { createNullParent(children) }
                else state.message = selectLayersHint
            }, Modifier.weight(1f))
            AddTile(Ic.Group, tr("Empty Group", "گروه خالی"), {
                state.act { addEmptyGroup(w, h) }; state.updatePanel(PanelTab.PROPERTIES)
            }, Modifier.weight(1f))
            AddTile(Ic.NullLayer, tr("Null", "لایهٔ Null"), {
                state.act { edit("Add null") { val l = Creator.nullLayer(doc, doc.root.arr("layers"), "Null"); Creator.insertLayer(doc, null, l) } }
            }, Modifier.weight(1f))
        }
    }
}

/** Text is shaped with the selected imported font, then converted to editable TGS vector paths. */
@Composable
fun TextDialog(state: EditorState, onImportFont: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var bold by remember { mutableStateOf(false) }
    var italic by remember { mutableStateOf(false) }
    var filledOutline by remember { mutableStateOf(true) }
    var lineSpacing by remember { mutableStateOf("1.0") }
    var fontSize by remember { mutableStateOf("220") }
    var color by remember { mutableStateOf(Rgba(1.0, 1.0, 1.0, 1.0)) }
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val noText = tr("Couldn't turn this text into vectors.", "نتوانستم متن را به مسیر برداری تبدیل کنم.")
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = StudioColors.Panel,
        title = { Text(tr("Add text", "افزودن متن"), color = StudioColors.TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { text = it; error = null }, minLines = 2, maxLines = 4, label = { Text(tr("Text", "متن")) }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(tr("Import TTF / OTF", "واردکردن فونت"), onImportFont)
                    Text(state.fontPath?.substringAfterLast('/') ?: tr("System font", "فونت سیستم"), color = StudioColors.TextMuted, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(fontSize, { fontSize = it.filter { ch -> ch.isDigit() || ch == '.' } }, singleLine = true, label = { Text(tr("Size (px)", "اندازه (پیکسل)")) }, modifier = Modifier.weight(1f))
                    OutlinedTextField(lineSpacing, { lineSpacing = it.filter { ch -> ch.isDigit() || ch == '.' } }, singleLine = true, label = { Text(tr("Leading", "فاصله خطوط")) }, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Swatch(color.toColor(), { picking = true })
                    SmallButton(tr("Bold", "ضخیم"), { bold = !bold }, primary = bold)
                    SmallButton(tr("Italic", "مورب"), { italic = !italic }, primary = italic)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(tr("Filled outline", "حروف توپر"), { filledOutline = true }, primary = filledOutline)
                    SmallButton(tr("Centerline", "خط مرکزی"), { filledOutline = false }, primary = !filledOutline)
                }
                Text(
                    tr("The selected font is remembered between sessions. TGS has no live-text layer; text is converted to editable vector paths.", "فونت انتخاب‌شده بین اجراها حفظ می‌شود. TGS لایه متن زنده ندارد و متن به مسیر برداری قابل ویرایش تبدیل می‌شود."),
                    color = StudioColors.TextMuted, fontSize = 12.sp,
                )
                error?.let { Text(it, color = StudioColors.Danger, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton({
                val px = fontSize.toFloatOrNull()?.coerceIn(12f, 600f) ?: 220f
                val leading = lineSpacing.toFloatOrNull()?.coerceIn(0.8f, 2.5f) ?: 1f
                val mask = TextRaster.mask(text, px, bold, state.fontPath, leading, italic)
                if (mask == null) {
                    error = noText
                } else {
                    val scale = minOf(380.0 / mask.width, 200.0 / mask.height).coerceAtMost(1.0)
                    val added = if (filledOutline) {
                        val contours = TextRaster.outlines(mask)
                        if (contours.isEmpty()) null else state.act { addOutlinedText(contours, scale, color, text.lineSequence().first().take(24)) }
                    } else {
                        val lines = Skeleton.centerline(mask)
                        if (lines.paths.isEmpty()) null else state.act { addCenterlineText(lines, scale, color, text.lineSequence().first().take(24)) }
                    }
                    if (added == null) error = noText
                    else { state.updatePanel(PanelTab.PROPERTIES); onDismiss() }
                }
            }) { Text(tr("Add", "افزودن"), color = StudioColors.Accent) }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "انصراف"), color = StudioColors.TextMuted) } },
    )
    if (picking) ColorPickerDialog(color, { color = it }, { picking = false })
}
