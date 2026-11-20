package ir.sina.tgsstudio.ui

import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import java.io.File
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AddTile(Ic.Draw, tr("Draw", "نقاشی"), { state.tool = Tool.DRAW; state.updatePanel(null); state.changed() }, Modifier.weight(1f))
            AddTile(Ic.Eraser, tr("Eraser", "پاک‌کن"), { state.tool = Tool.ERASE; state.updatePanel(null); state.changed() }, Modifier.weight(1f))
            AddTile(Ic.Effects, tr("Effects", "افکت‌ها"), { state.updatePanel(PanelTab.EFFECTS) }, Modifier.weight(1f))
            Box(Modifier.weight(1f))
        }
    }
}

/** Text becomes vector strokes: the glyphs are rasterised, reduced to a centerline and added as stroked paths. */
@Composable
fun TextDialog(state: EditorState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var bold by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf(Rgba(1.0, 1.0, 1.0, 1.0)) }
    var picking by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf("220") }
    var fontPath by remember { mutableStateOf<String?>(null) }
    var fontLabel by remember { mutableStateOf("System default") }
    var error by remember { mutableStateOf<String?>(null) }
    val noText = tr("Couldn't turn this text into vectors.", "نتوانستم متن را به مسیر برداری تبدیل کنم.")
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val dir = File(context.filesDir, "fonts").apply { mkdirs() }
            val ext = uri.lastPathSegment?.substringAfterLast('.', "ttf")?.lowercase()?.takeIf { it in setOf("ttf", "otf") } ?: "ttf"
            val file = File(dir, "font_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } } ?: throw IllegalStateException("Cannot read font")
            Typeface.createFromFile(file)
            fontPath = file.absolutePath
            fontLabel = uri.lastPathSegment?.substringAfterLast('/') ?: file.name
            error = null
        }.onFailure { error = (if (state.isFa) "فونت بارگذاری نشد: " else "Font import failed: ") + (it.message ?: "") }
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = StudioColors.Panel,
        title = { Text(tr("Add text", "افزودن متن"), color = StudioColors.TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { text = it; error = null }, minLines = 2, maxLines = 4, label = { Text(tr("Text", "متن")) }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(tr("Import TTF / OTF", "واردکردن فونت"), { fontPicker.launch(arrayOf("*/*")) })
                    Text(fontLabel, color = StudioColors.TextMuted, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(fontSize, { fontSize = it.filter { ch -> ch.isDigit() || ch == '.' } }, singleLine = true, label = { Text(tr("Font size (px)", "اندازه فونت (پیکسل)")) }, modifier = Modifier.fillMaxWidth())
                Text(tr("Text is shaped by Android, then converted to editable vector strokes because TGS does not support live text layers.", "متن با موتور حروف‌چینی اندروید شکل‌دهی و سپس به مسیر برداری قابل ویرایش تبدیل می‌شود؛ TGS لایه متن زنده ندارد."), color = StudioColors.TextMuted, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Swatch(color.toColor(), { picking = true })
                    SmallButton(tr("Bold", "ضخیم"), { bold = !bold }, primary = bold)
                }
                error?.let { Text(it, color = StudioColors.Danger, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton({
                val baseTypeface = fontPath?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() } ?: Typeface.DEFAULT
                val px = fontSize.toFloatOrNull()?.coerceIn(12f, 600f) ?: 220f
                val mask = TextRaster.mask(text, px, bold, baseTypeface)
                val lines = mask?.let { Skeleton.centerline(it) }
                if (mask == null || lines == null || lines.paths.isEmpty()) { error = noText }
                else {
                    val scale = minOf(380.0 / mask.width, 200.0 / mask.height).coerceAtMost(1.0)
                    state.act { addCenterlineText(lines, scale, color, text.lineSequence().first().take(24)) }
                    state.updatePanel(PanelTab.PROPERTIES); onDismiss()
                }
            }) { Text(tr("Add", "افزودن"), color = StudioColors.Accent) }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "انصراف"), color = StudioColors.TextMuted) } },
    )
    if (picking) ColorPickerDialog(color, { color = it }, { picking = false })
}
