package ir.sina.tgsstudio.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ir.sina.tgsstudio.data.ProjectStore
import ir.sina.tgsstudio.editor.PathOps
import ir.sina.tgsstudio.export.Exporter
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun EditorScreen(state: EditorState, store: ProjectStore, lang: Lang, onExit: () -> Unit) {
    val context = LocalContext.current
    SideEffect { state.isFa = lang == Lang.FA }
    val doc = state.session.doc
    var showExport by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showLeave by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var pendingKind by remember { mutableStateOf<ExportKind?>(null) }

    fun save() {
        runCatching { store.save(state.projectId, doc, renderThumbnail(doc)) }
            .onSuccess { state.markSaved(); state.message = if (lang == Lang.FA) "ذخیره شد" else "Saved" }
            .onFailure { state.message = if (lang == Lang.FA) "ذخیره نشد" else "Couldn't save" }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (state.dirty) runCatching { store.save(state.projectId, doc, renderThumbnail(doc)); state.markSaved() } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val kind = pendingKind
        if (uri != null && kind != null) {
            val ok = runCatching {
                val bytes = when (kind) { ExportKind.TGS -> Exporter.tgs(doc); ExportKind.JSON -> Exporter.lottieJson(doc).toByteArray(Charsets.UTF_8); ExportKind.SVG -> Exporter.svgFrame(doc, state.frame).toByteArray(Charsets.UTF_8) }
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
            }.isSuccess
            Toast.makeText(context, if (ok) (if (lang == Lang.FA) "خروجی ذخیره شد" else "Exported") else (if (lang == Lang.FA) "خروجی ناموفق بود" else "Export failed"), Toast.LENGTH_SHORT).show()
        }
    }
    val svgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("no stream")
                state.act { addSvg(text) }
            }.onSuccess { r ->
                state.setPanel(PanelTab.PROPERTIES)
                state.message = when {
                    r == null || r.groups.isEmpty() -> if (lang == Lang.FA) "شکل برداری در این SVG پیدا نشد." else "No vector shapes found in this SVG."
                    r.skipped.isNotEmpty() -> (if (lang == Lang.FA) "وارد شد. پشتیبانی‌نشده: " else "Imported. Not supported: ") + r.skipped.joinToString()
                    else -> if (lang == Lang.FA) "SVG وارد شد" else "SVG imported"
                }
            }.onFailure { state.message = (if (lang == Lang.FA) "SVG خوانده نشد: " else "Couldn't read SVG: ") + (it.message ?: "") }
        }
    }

    // playback
    LaunchedEffect(state.playing) {
        if (!state.playing) return@LaunchedEffect
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = (now - last) / 1e9; last = now
            var f = state.frame + dt * doc.frameRate
            val last1 = doc.outPoint - 1
            if (f > last1) {
                if (state.loop) f = doc.inPoint + (f - doc.inPoint) % max(1.0, doc.outPoint - doc.inPoint)
                else { state.seek(last1); state.playing = false; return@LaunchedEffect }
            }
            state.seek(f)
        }
    }
    LaunchedEffect(state.message) { if (state.message != null) { delay(3200); state.message = null } }
    LaunchedEffect(state.previewMode) { if (state.previewMode) { state.playing = true; state.loop = true; state.viewFitted = false; state.fit(state.fitSize.x, state.fitSize.y) } }

    BackHandler {
        when {
            state.previewMode -> { state.previewMode = false; state.playing = false }
            state.penPath != null -> { state.penPath = null; state.changed() }
            state.panel != null -> state.setPanel(null)
            state.tool != Tool.SELECT -> { state.tool = Tool.SELECT; state.changed() }
            state.dirty -> showLeave = true
            else -> onExit()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(StudioColors.Background).systemBarsPadding()) {
        val wide = maxWidth >= 700.dp && state.workspace == Workspace.ADVANCED && !state.previewMode
        val maxPanel = (maxHeight.value * 0.7f).coerceAtLeast(160f)
        Column(Modifier.fillMaxSize()) {
            if (!state.previewMode) Ltr { TopBar(state, { if (state.dirty) showLeave = true else onExit() }, ::save, { showExport = true }, { menu = true }, menu, { menu = false }, { state.previewMode = true }, { showRename = true }) }
            if (!state.previewMode) Ltr { ToolBar(state, { showText = true }, { svgPicker.launch(arrayOf("*/*")) }) }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.width(250.dp).fillMaxSize().background(StudioColors.Panel)) { LayersPanel(state) }
                        Column(Modifier.weight(1f).fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxWidth()) { CanvasArea(state) }
                            Column(Modifier.height(190.dp).fillMaxWidth().background(StudioColors.Panel)) { TimelinePanel(state) }
                        }
                        Column(Modifier.width(290.dp).fillMaxSize().background(StudioColors.Panel)) { PropertiesPanel(state) }
                    }
                } else CanvasArea(state)

                state.message?.let { m ->
                    Text(m, color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp, start = 12.dp, end = 12.dp).clip(RoundedCornerShape(12.dp)).background(StudioColors.PanelHigh).padding(horizontal = 14.dp, vertical = 8.dp))
                }
                if (state.previewMode) IconBtn(Ic.Close, { state.previewMode = false; state.playing = false }, Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0x99000000), RoundedCornerShape(12.dp)), tint = Color.White)
                if (!state.previewMode) ContextBars(state, Modifier.align(Alignment.BottomCenter))
            }

            Ltr { PlaybackBar(state) }

            if (!state.previewMode && state.panel != null && (!wide || state.panel == PanelTab.ADD)) {
                Column(Modifier.fillMaxWidth().height(state.panelHeightDp.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(StudioColors.Panel)) {
                    PanelHandle(state, maxPanel)
                    Box(Modifier.weight(1f)) {
                        when (state.panel) {
                            PanelTab.LAYERS -> LayersPanel(state)
                            PanelTab.PROPERTIES -> PropertiesPanel(state)
                            PanelTab.TIMELINE -> TimelinePanel(state)
                            PanelTab.ADD -> AddPanel(state, { svgPicker.launch(arrayOf("*/*")) }, { showText = true })
                            null -> Unit
                        }
                    }
                }
            }
            if (!state.previewMode && !wide) Dock(state)
        }
    }

    if (showExport) ExportDialog(doc, { kind -> pendingKind = kind; exportLauncher.launch("${state.projectName}.${kind.ext}") }, { showExport = false })
    if (showText) TextDialog(state) { showText = false }
    if (showRename) NameDialog(tr("Project name", "نام پروژه"), state.projectName, { n -> state.projectName = n; store.rename(state.projectId, n); doc.name = n; showRename = false; state.changed() }, { showRename = false })
    if (showLeave) ConfirmDialog(
        tr("Unsaved changes", "تغییرات ذخیره‌نشده"), tr("Save your changes before leaving?", "قبل از خروج تغییرات ذخیره شود؟"), tr("Save", "ذخیره"),
        { save(); showLeave = false; onExit() }, { showLeave = false }, extra = tr("Discard", "دور انداختن") to { showLeave = false; onExit() },
    )
}

@Composable
private fun CanvasArea(state: EditorState) {
    Box(Modifier.fillMaxSize()) { EditorCanvas(state) }
}

@Composable
private fun TopBar(state: EditorState, onBack: () -> Unit, onSave: () -> Unit, onExport: () -> Unit, onMore: () -> Unit, menuOpen: Boolean, onMenuClose: () -> Unit, onPreview: () -> Unit, onRename: () -> Unit) {
    val tick = state.tick
    Row(Modifier.fillMaxWidth().height(52.dp).background(StudioColors.Panel).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Ic.Back, onBack, size = 40.dp)
        Row(Modifier.weight(1f).clickable(onClick = onRename).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.projectName, color = StudioColors.TextPrimary, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            if (state.dirty) Box(Modifier.padding(start = 6.dp)) { Dot(StudioColors.Warning, 8.dp) }
        }
        IconBtn(Ic.Undo, { state.act { undo() } }, enabled = state.session.history.canUndo && tick >= 0, size = 40.dp)
        IconBtn(Ic.Redo, { state.act { redo() } }, enabled = state.session.history.canRedo && tick >= 0, size = 40.dp)
        IconBtn(Ic.Save, onSave, enabled = state.dirty, size = 40.dp)
        IconBtn(Ic.Export, onExport, size = 40.dp)
        Box {
            IconBtn(Ic.More, onMore, size = 40.dp)
            DropdownMenu(menuOpen, onMenuClose, containerColor = StudioColors.PanelRaised) {
                DropdownMenuItem(text = { Text(tr("Preview", "پیش‌نمایش"), color = StudioColors.TextPrimary) }, onClick = { onMenuClose(); onPreview() })
                DropdownMenuItem(text = { Text(tr("Rename project", "تغییر نام پروژه"), color = StudioColors.TextPrimary) }, onClick = { onMenuClose(); onRename() })
                DropdownMenuItem(text = { Text(tr("Fit to screen", "جای‌دهی در صفحه"), color = StudioColors.TextPrimary) }, onClick = { onMenuClose(); state.fit(state.fitSize.x, state.fitSize.y) })
            }
        }
    }
}

@Composable
private fun ToolBar(state: EditorState, onText: () -> Unit, onSvg: () -> Unit) {
    val tools = listOf(Tool.SELECT to Ic.Select, Tool.HAND to Ic.Hand, Tool.PEN to Ic.Pen, Tool.NODE to Ic.Node, Tool.RECT to Ic.Rect, Tool.ELLIPSE to Ic.Ellipse, Tool.STAR to Ic.Star, Tool.POLYGON to Ic.Polygon)
    Row(Modifier.fillMaxWidth().background(StudioColors.Background).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        tools.forEach { (t, ic) -> IconBtn(ic, { state.tool = t; if (t != Tool.PEN) state.penPath = null; state.changed() }, active = state.tool == t, size = 42.dp) }
        IconBtn(Ic.Text, onText, size = 42.dp)
        IconBtn(Ic.Svg, onSvg, size = 42.dp)
        Box(Modifier.width(1.dp).height(24.dp).background(StudioColors.Divider))
        IconBtn(Ic.Fit, { state.fit(state.fitSize.x, state.fitSize.y) }, size = 42.dp)
        Text("${(state.viewScale / (minOf(state.fitSize.x / state.session.doc.width, state.fitSize.y / state.session.doc.height).coerceAtLeast(0.0001f) * 0.92f) * 100).toInt()}%", color = StudioColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
        Box(Modifier.width(1.dp).height(24.dp).background(StudioColors.Divider))
        listOf(Workspace.DESIGN to tr("Design", "طراحی"), Workspace.ANIMATION to tr("Animation", "انیمیشن"), Workspace.ADVANCED to tr("Advanced", "پیشرفته")).forEach { (w, label) ->
            SmallButton(label, { state.setWorkspaceMode(w) }, primary = state.workspace == w, modifier = Modifier.padding(horizontal = 2.dp))
        }
    }
}

@Composable
private fun ContextBars(state: EditorState, modifier: Modifier) {
    val tick = state.tick
    val sel = state.session.selection
    Column(modifier.padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val pen = state.penPath
        if (state.tool == Tool.PEN) {
            Ltr {
                Row(Modifier.clip(RoundedCornerShape(14.dp)).background(StudioColors.PanelRaised).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (pen == null) tr("Tap to place points; drag to curve. Tap the first point to close.", "برای گذاشتن نقطه بزنید؛ برای منحنی بکشید. برای بستن، نقطهٔ اول را بزنید.") else tr("${pen.count} points", "${pen.count} نقطه"), color = StudioColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp).align(Alignment.CenterVertically))
                    if (pen != null) {
                        SmallButton(tr("Done", "پایان"), { state.act { addFreeform(pen, filled = false) }; state.penPath = null; state.tool = Tool.SELECT; state.changed() }, primary = true, enabled = pen.count >= 2)
                        SmallButton(tr("Close", "بستن"), { state.act { addFreeform(PathOps.setClosed(pen, true)) }; state.penPath = null; state.tool = Tool.SELECT; state.changed() }, enabled = pen.count >= 3)
                        SmallButton(tr("Cancel", "انصراف"), { state.penPath = null; state.changed() })
                    }
                }
            }
        }
        if (state.tool == Tool.NODE && tick >= 0 && state.session.pathShape() == null) {
            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(StudioColors.PanelRaised).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.session.convertibleShape() != null) tr("Convert this shape to a path to edit its points.", "برای ویرایش نقاط، شکل را به مسیر تبدیل کنید.") else tr("Select a path to edit its points.", "برای ویرایش نقاط یک مسیر را انتخاب کنید."), color = StudioColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
                if (state.session.convertibleShape() != null) SmallButton(tr("Convert", "تبدیل"), { state.act { convertToPath() } }, primary = true)
            }
        }
        if (state.tool == Tool.NODE && tick >= 0) state.session.pathShape()?.let { ref ->
            val b = state.session.pathBezier(ref)
            val n = state.nodeIndex
            if (b != null && n in 0 until b.count) Ltr {
                Row(Modifier.clip(RoundedCornerShape(14.dp)).background(StudioColors.PanelRaised).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallButton(tr("Smooth", "نرم"), { state.act { edit("Smooth node") { writePath(ref, PathOps.toSmooth(b, n)) } } })
                    SmallButton(tr("Corner", "گوشه"), { state.act { edit("Corner node") { writePath(ref, PathOps.toCorner(b, n)) } } })
                    SmallButton(tr("Delete", "حذف"), { if (state.act { structuralPathEdit(ref, "Delete node") { PathOps.delete(it, n) } }) state.nodeIndex = -1 else state.message = if (state.isFa) "مسیر حداقل دو نقطه لازم دارد." else "A path needs at least two points." })
                }
            }
        }
        if (state.tool == Tool.SELECT && sel.isNotEmpty() && tick >= 0) Ltr {
            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(StudioColors.PanelRaised).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconBtn(Ic.Copy, { state.act { duplicateSelection() } }, size = 40.dp, iconSize = 20.dp)
                IconBtn(Ic.Up, { sel.singleOrNull()?.let { t -> state.act { move(t, -1) } } }, enabled = sel.size == 1, size = 40.dp, iconSize = 20.dp)
                IconBtn(Ic.Down, { sel.singleOrNull()?.let { t -> state.act { move(t, 1) } } }, enabled = sel.size == 1, size = 40.dp, iconSize = 20.dp)
                IconBtn(Ic.Props, { state.setPanel(PanelTab.PROPERTIES) }, size = 40.dp, iconSize = 20.dp)
                IconBtn(Ic.Trash, { state.act { deleteSelection() }; state.message = if (state.isFa) "حذف شد — با Undo برمی‌گردد" else "Deleted — Undo brings it back" }, size = 40.dp, iconSize = 20.dp, tint = StudioColors.Danger)
            }
        }
    }
}

@Composable
private fun PlaybackBar(state: EditorState) {
    val doc = state.session.doc
    val lo = doc.inPoint.toFloat(); val hi = max(lo + 1f, (doc.outPoint - 1).toFloat())
    Row(Modifier.fillMaxWidth().height(48.dp).background(StudioColors.Panel).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Ic.Prev, { state.playing = false; state.seek(Math.floor(state.frame) - 1) }, size = 40.dp, iconSize = 20.dp)
        IconBtn(if (state.playing) Ic.Pause else Ic.Play, { state.playing = !state.playing }, active = true, size = 40.dp, iconSize = 20.dp)
        IconBtn(Ic.Next, { state.playing = false; state.seek(Math.floor(state.frame) + 1) }, size = 40.dp, iconSize = 20.dp)
        IconBtn(Ic.Loop, { state.loop = !state.loop }, active = state.loop, size = 40.dp, iconSize = 20.dp)
        Slider(
            value = state.frame.toFloat().coerceIn(lo, hi), onValueChange = { state.playing = false; state.seek(Math.rint(it.toDouble())) }, valueRange = lo..hi,
            colors = SliderDefaults.colors(thumbColor = StudioColors.Accent, activeTrackColor = StudioColors.Accent, inactiveTrackColor = StudioColors.PanelHigh),
            modifier = Modifier.weight(1f),
        )
        Text("${state.frame.toInt()}/${(doc.outPoint - 1).toInt()}", color = StudioColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
        SmallButton("${fmt(doc.frameRate, 0)} fps", { state.act { setFrameRate(if (doc.frameRate == 60.0) 30.0 else 60.0) } })
    }
}

@Composable
private fun PanelHandle(state: EditorState, maxDp: Float) {
    val density = LocalDensity.current.density
    Box(
        Modifier.fillMaxWidth().height(22.dp).pointerInput(Unit) {
            detectVerticalDragGestures(onDragEnd = { state.savePanelHeight() }) { _, dy -> state.panelHeightDp = (state.panelHeightDp - dy / density).coerceIn(120f, maxDp) }
        },
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.width(44.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(StudioColors.PanelHigh)) }
}

@Composable
private fun Dock(state: EditorState) {
    val items = listOf(PanelTab.LAYERS to (Ic.Layers to tr("Layers", "لایه‌ها")), PanelTab.PROPERTIES to (Ic.Props to tr("Properties", "ویژگی‌ها")), PanelTab.TIMELINE to (Ic.Timeline to tr("Timeline", "تایم‌لاین")), PanelTab.ADD to (Ic.Add to tr("Add", "افزودن")))
    Row(Modifier.fillMaxWidth().height(58.dp).background(StudioColors.Panel), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (tab, pair) ->
            val active = state.panel == tab
            Column(
                Modifier.weight(1f).fillMaxSize().clickable { state.setPanel(if (state.panel == tab) null else tab) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(pair.first, if (active) StudioColors.Accent else StudioColors.TextMuted, 22.dp)
                Text(pair.second, color = if (active) StudioColors.Accent else StudioColors.TextMuted, fontSize = 11.sp)
            }
        }
    }
}
