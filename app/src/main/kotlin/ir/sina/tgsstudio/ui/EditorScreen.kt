package ir.sina.tgsstudio.ui

import android.app.Activity
import android.content.ContextWrapper
import android.view.View
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import ir.sina.tgsstudio.editor.Creator
import ir.sina.tgsstudio.export.Exporter
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun EditorScreen(state: EditorState, store: ProjectStore, lang: Lang, onExit: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
    }
    val decorView = activity?.window?.decorView
    val immersiveFlags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
        View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    DisposableEffect(decorView) {
        val previousFlags = decorView?.systemUiVisibility ?: 0
        decorView?.systemUiVisibility = immersiveFlags
        onDispose { decorView?.systemUiVisibility = previousFlags }
    }
    SideEffect {
        state.isFa = lang == Lang.FA
        decorView?.systemUiVisibility = immersiveFlags
    }
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

    fun writeExport(uri: android.net.Uri?, kind: ExportKind) {
        if (uri == null) return
        val ok = runCatching {
            val bytes = when (kind) {
                ExportKind.TGS -> Exporter.tgs(doc)
                ExportKind.JSON -> Exporter.lottieJson(doc).toByteArray(Charsets.UTF_8)
                ExportKind.SVG -> Exporter.svgFrame(doc, state.frame).toByteArray(Charsets.UTF_8)
            }
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
        }.isSuccess
        Toast.makeText(context, if (ok) (if (lang == Lang.FA) "خروجی ${kind.ext.uppercase()} ذخیره شد" else "${kind.ext.uppercase()} exported") else (if (lang == Lang.FA) "خروجی ناموفق بود" else "Export failed"), Toast.LENGTH_SHORT).show()
    }
    val tgsExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportKind.TGS.mime)) { writeExport(it, ExportKind.TGS) }
    val jsonExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportKind.JSON.mime)) { writeExport(it, ExportKind.JSON) }
    val svgExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportKind.SVG.mime)) { writeExport(it, ExportKind.SVG) }
    fun launchExport(kind: ExportKind) {
        val name = "${state.projectName}.${kind.ext}"
        when (kind) {
            ExportKind.TGS -> tgsExportLauncher.launch(name)
            ExportKind.JSON -> jsonExportLauncher.launch(name)
            ExportKind.SVG -> svgExportLauncher.launch(name)
        }
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            var target: java.io.File? = null
            runCatching {
                val fontsDir = java.io.File(context.filesDir, "fonts").apply { mkdirs() }
                val pickedName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty()
                val ext = pickedName.substringAfterLast('.', "ttf").lowercase().takeIf { it == "ttf" || it == "otf" } ?: "ttf"
                target = java.io.File(fontsDir, "custom-font-" + System.currentTimeMillis() + "." + ext)
                context.contentResolver.openInputStream(uri)?.use { input -> target!!.outputStream().use(input::copyTo) } ?: error("no font stream")
                android.graphics.Typeface.createFromFile(target!!)
                state.persistImportedFontPath(target!!.absolutePath)
                state.message = if (lang == Lang.FA) "فونت وارد شد و ذخیره شد" else "Font imported and saved"
            }.onFailure {
                target?.delete()
                state.message = (if (lang == Lang.FA) "وارد کردن فونت ناموفق بود: " else "Font import failed: ") + (it.message ?: "")
            }
        }
    }

    val svgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<android.net.Uri> ->
        if (uris.isNotEmpty()) {
            var imported = 0
            var unsupported = 0
            var failed = 0
            uris.forEach { uri ->
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("no stream")
                    state.act { addSvg(text) }
                }.onSuccess { result ->
                    if (result == null || result.groups.isEmpty()) unsupported++ else imported++
                }.onFailure { failed++ }
            }
            state.openRightPanel(PanelTab.PROPERTIES)
            state.message = if (lang == Lang.FA) {
                "$imported فایل SVG وارد شد؛ $unsupported بدون شکل برداری؛ $failed خطا"
            } else {
                "$imported SVG imported; $unsupported contained no supported vectors; $failed failed"
            }
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
            state.rightPanelOpen -> state.rightPanelOpen = false
            state.panel != null -> state.updatePanel(null)
            state.tool != Tool.SELECT -> { state.tool = Tool.SELECT; state.changed() }
            state.dirty -> showLeave = true
            else -> onExit()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(StudioColors.Background)) {
        val wide = maxWidth >= 700.dp && state.workspace == Workspace.ADVANCED && !state.previewMode
        val maxPanel = (maxHeight.value * 0.7f).coerceAtLeast(160f)
        Column(Modifier.fillMaxSize()) {
            if (!state.previewMode) Ltr { TopBar(state, { if (state.dirty) showLeave = true else onExit() }, ::save, { showExport = true }, { menu = true }, menu, { menu = false }, { state.previewMode = true }, { showRename = true }) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (wide) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) { CanvasArea(state, { showText = true }, { svgPicker.launch(arrayOf("*/*")) }) }
                        Column(Modifier.height(state.panelHeightDp.dp.coerceAtLeast(150.dp)).fillMaxWidth().background(StudioColors.Panel)) {
                            PanelHandle(state, maxPanel)
                            Box(Modifier.weight(1f)) { TimelinePanel(state) }
                        }
                    }
                } else CanvasArea(state, { showText = true }, { svgPicker.launch(arrayOf("*/*")) })

                state.message?.let { m ->
                    Text(m, color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp, start = 12.dp, end = 12.dp).clip(RoundedCornerShape(12.dp)).background(StudioColors.PanelHigh).padding(horizontal = 14.dp, vertical = 8.dp))
                }
                if (state.previewMode) IconBtn(Ic.Close, { state.previewMode = false; state.playing = false }, Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0x99000000), RoundedCornerShape(12.dp)), tint = Color.White)
                if (!state.previewMode) ContextBars(state, Modifier.align(Alignment.BottomCenter))
            }

            Ltr { PlaybackBar(state) }

            if (!state.previewMode && state.panel != null && state.panel != PanelTab.LAYERS && state.panel != PanelTab.PROPERTIES && (!wide || state.panel == PanelTab.EFFECTS)) {
                Column(Modifier.fillMaxWidth().height(state.panelHeightDp.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(StudioColors.Panel)) {
                    PanelHandle(state, maxPanel)
                    Box(Modifier.weight(1f)) {
                        when (state.panel) {
                            PanelTab.LAYERS -> LayersPanel(state)
                            PanelTab.PROPERTIES -> PropertiesPanel(state)
                            PanelTab.TIMELINE -> TimelinePanel(state)
                            PanelTab.EFFECTS -> EffectsPanel(state)
                            null -> Unit
                        }
                    }
                }
            }
            if (!state.previewMode && !wide) Dock(state)
        }
    }

    if (showExport) ExportDialog(doc, { kind -> showExport = false; launchExport(kind) }, { showExport = false })
    if (showText) TextDialog(state, { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) }) { showText = false }
    if (showRename) NameDialog(tr("Project name", "نام پروژه"), state.projectName, { n -> state.projectName = n; store.rename(state.projectId, n); doc.name = n; showRename = false; state.changed() }, { showRename = false })
    if (showLeave) ConfirmDialog(
        tr("Unsaved changes", "تغییرات ذخیره‌نشده"), tr("Save your changes before leaving?", "قبل از خروج تغییرات ذخیره شود؟"), tr("Save", "ذخیره"),
        { save(); showLeave = false; onExit() }, { showLeave = false }, extra = tr("Discard", "دور انداختن") to { showLeave = false; onExit() },
    )
}

@Composable
private fun CanvasArea(state: EditorState, onText: () -> Unit, onSvg: () -> Unit) {
    val toolsOpen = state.toolsDrawerOpen
    var showUtilities by remember { mutableStateOf(false) }
    var expandedToolGroups by remember { mutableStateOf(setOf("selection", "paths", "shapes", "drawing")) }
    fun toggleToolGroup(key: String) { expandedToolGroups = if (key in expandedToolGroups) expandedToolGroups - key else expandedToolGroups + key }
    val doc = state.session.doc
    val centerX = doc.width / 2.0
    val centerY = doc.height / 2.0
    fun finishAdd() {
        state.tool = Tool.SELECT
        state.openRightPanel(PanelTab.PROPERTIES)
    }
    fun addObject(block: ir.sina.tgsstudio.editor.EditorSession.() -> Unit) {
        state.act(block)
        finishAdd()
    }
    fun addNull() {
        state.act {
            edit("Add null") {
                val layer = Creator.nullLayer(doc, doc.root.arr("layers"), "Null")
                Creator.insertLayer(doc, null, layer)
            }
        }
        finishAdd()
    }
    fun addLinkedNull() {
        val children = state.session.selection.map { it.layer }.distinct()
        if (children.isEmpty()) {
            state.message = if (state.isFa) "ابتدا یک یا چند لایه را انتخاب کنید." else "Select one or more layers first."
        } else {
            state.act { createNullParent(children) }
            finishAdd()
        }
    }
    Box(Modifier.fillMaxSize()) {
        EditorCanvas(state)
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
                .width(if (toolsOpen) 214.dp else 48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(StudioColors.Panel.copy(alpha = 0.97f))
                .padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (toolsOpen) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.Tools, StudioColors.Accent, 19.dp)
                    Text(tr("Tools", "ابزارها"), color = StudioColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconBtn(Ic.Chevron, { state.toolsDrawerOpen = false }, size = 36.dp, iconSize = 18.dp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(StudioColors.Divider))
                Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
                    ToolGroupHeader(tr("Selection & navigation", "انتخاب و جابه‌جایی"), "selection" in expandedToolGroups) { toggleToolGroup("selection") }
                    if ("selection" in expandedToolGroups) {
                        ToolDrawerItem(state, Tool.SELECT, Ic.Select, tr("Select", "انتخاب"), { state.toolsDrawerOpen = false })
                        ToolDrawerItem(state, Tool.HAND, Ic.Hand, tr("Pan / Hand", "جابجایی بوم"), { state.toolsDrawerOpen = false })
                    }
                    ToolGroupHeader(tr("Path & node editing", "مسیر و ویرایش گره"), "paths" in expandedToolGroups) { toggleToolGroup("paths") }
                    if ("paths" in expandedToolGroups) {
                        ToolDrawerItem(state, Tool.PEN, Ic.Pen, tr("Bezier Pen", "قلم بزیه"), { state.toolsDrawerOpen = false })
                        ToolDrawerItem(state, Tool.NODE, Ic.Node, tr("Node Editor", "ویرایش گره‌ها"), { state.toolsDrawerOpen = false })
                        ToolDrawerItem(state, Tool.ADD_NODE, Ic.Add, tr("Add Anchor Point", "افزودن نقطه اتصال"), { state.toolsDrawerOpen = false })
                        ToolDrawerItem(state, Tool.DELETE_NODE, Ic.Trash, tr("Delete Anchor Point", "حذف نقطه اتصال"), { state.toolsDrawerOpen = false })
                        ToolDrawerItem(state, Tool.SMOOTH_NODE, Ic.Draw, tr("Smooth Point", "نقطه منحنی"), { state.toolsDrawerOpen = false })
                        ToolDrawerItem(state, Tool.CORNER_NODE, Ic.Node, tr("Corner Point", "نقطه گوشه"), { state.toolsDrawerOpen = false })
                        ToolDrawerItem(state, Tool.LINE, Ic.Draw, tr("Line", "خط"), { state.toolsDrawerOpen = false })
                    }
                    ToolGroupHeader(tr("Geometric shapes", "اشکال هندسی"), "shapes" in expandedToolGroups) { toggleToolGroup("shapes") }
                    if ("shapes" in expandedToolGroups) {
                        ToolDrawerItem(state, Tool.RECT, Ic.Rect, tr("Rectangle", "مستطیل"), { state.toolsDrawerOpen = false })
                        ToolDrawerItem(state, Tool.ROUNDED_RECT, Ic.Rect, tr("Rounded Rectangle", "مستطیل گوشه‌گرد"), { state.toolsDrawerOpen = false })
                        ToolDrawerItem(state, Tool.ELLIPSE, Ic.Ellipse, tr("Ellipse", "بیضی"), { state.toolsDrawerOpen = false })
                        ToolDrawerItem(state, Tool.STAR, Ic.Star, tr("Star", "ستاره"), { state.toolsDrawerOpen = false })
                        ToolDrawerItem(state, Tool.POLYGON, Ic.Polygon, tr("Polygon", "چندضلعی"), { state.toolsDrawerOpen = false })
                        ToolDrawerItem(state, Tool.ARC, Ic.Draw, tr("Arc", "کمان"), { state.toolsDrawerOpen = false })
                        ToolDrawerItem(state, Tool.SPIRAL, Ic.Draw, tr("Spiral", "مارپیچ"), { state.toolsDrawerOpen = false })
                    }
                    ToolGroupHeader(tr("Freehand drawing", "طراحی آزاد"), "drawing" in expandedToolGroups) { toggleToolGroup("drawing") }
                    if ("drawing" in expandedToolGroups) {
                        ToolDrawerItem(state, Tool.DRAW, Ic.Draw, tr("Freehand", "طراحی آزاد"), { state.toolsDrawerOpen = false })
                        ToolDrawerItem(state, Tool.ERASE, Ic.Eraser, tr("Stroke Eraser", "پاک‌کن خطوط"), { state.toolsDrawerOpen = false })
                    }
                    SmallButton(tr(if (showUtilities) "Hide workspace options" else "Workspace & canvas options", if (showUtilities) "بستن امکانات محیط" else "تنظیمات محیط و بوم"), { showUtilities = !showUtilities }, primary = showUtilities, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp))
                    if (showUtilities) {
                    ToolGroupLabel(tr("Quick add", "افزودن سریع"))
                    ToolDrawerAction(Ic.Rect, tr("Add rectangle", "افزودن مستطیل")) { addObject { addRect(centerX, centerY, 220.0, 160.0) } }
                    ToolDrawerAction(Ic.Ellipse, tr("Add ellipse", "افزودن بیضی")) { addObject { addEllipse(centerX, centerY, 200.0, 200.0) } }
                    ToolDrawerAction(Ic.Star, tr("Add star", "افزودن ستاره")) { addObject { addStar(centerX, centerY, 110.0) } }
                    ToolDrawerAction(Ic.Polygon, tr("Add polygon", "افزودن چندضلعی")) { addObject { addPolygon(centerX, centerY, 110.0) } }
                    ToolGroupLabel(tr("Objects & groups", "آبجکت‌ها و گروه‌ها"))
                    ToolDrawerAction(Ic.NullLayer, tr("Null layer", "لایهٔ Null"), ::addNull)
                    ToolDrawerAction(Ic.Link, tr("Linked Null", "Null متصل"), ::addLinkedNull)
                    ToolDrawerAction(Ic.Group, tr("Empty Group", "گروه خالی")) { addObject { addEmptyGroup(centerX, centerY) } }
                    ToolGroupLabel(tr("Create & import", "ساخت و ورود"))
                    ToolDrawerAction(Ic.Text, tr("Text", "متن"), onText)
                    ToolDrawerAction(Ic.Svg, tr("Import SVG", "ورود SVG"), onSvg)
                    ToolDrawerAction(Ic.Effects, tr("Effects", "افکت‌ها")) {
                        state.updatePanel(PanelTab.EFFECTS)
                        state.toolsDrawerOpen = false
                    }
                    ToolDrawerAction(Ic.Fit, tr("Fit canvas", "برازش بوم")) { state.fit(state.fitSize.x, state.fitSize.y) }
                    ToolGroupLabel(tr("Canvas guides", "راهنماهای بوم"))
                    SmallButton(tr(if (state.showGrid) "Grid ✓" else "Grid", if (state.showGrid) "شبکه ✓" else "شبکه"), { state.persistShowGrid(!state.showGrid) }, primary = state.showGrid, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                    SmallButton(tr(if (state.showRulers) "Rulers ✓" else "Rulers", if (state.showRulers) "خط‌کش ✓" else "خط‌کش"), { state.persistShowRulers(!state.showRulers) }, primary = state.showRulers, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                    SmallButton(tr(if (state.snapToGrid) "Snap ✓" else "Snap to grid", if (state.snapToGrid) "چسبیدن ✓" else "چسبیدن به شبکه"), { state.persistSnapToGrid(!state.snapToGrid) }, primary = state.snapToGrid, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SmallButton("−", { state.persistGridSpacing(state.gridSpacing / 2f) }, modifier = Modifier.weight(1f))
                        SmallButton("${state.gridSpacing.toInt()} px", {}, modifier = Modifier.weight(2f))
                        SmallButton("+", { state.persistGridSpacing(state.gridSpacing * 2f) }, modifier = Modifier.weight(1f))
                    }
                    Text(
                        "${(state.viewScale / (minOf(state.fitSize.x / state.session.doc.width, state.fitSize.y / state.session.doc.height).coerceAtLeast(0.0001f) * 0.92f) * 100).toInt()}%",
                        color = StudioColors.TextMuted, fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.End).padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                    ToolGroupLabel(tr("Workspace", "محیط کاری"))
                    listOf(Workspace.DESIGN to tr("Design", "طراحی"), Workspace.ANIMATION to tr("Animation", "انیمیشن"), Workspace.ADVANCED to tr("Advanced", "پیشرفته")).forEach { (w, label) ->
                        SmallButton(label, { state.setWorkspaceMode(w); state.toolsDrawerOpen = false }, primary = state.workspace == w, modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                    }
                }
            } else {
                IconBtn(Ic.Tools, { state.openToolsDrawer() }, active = true, size = 38.dp, iconSize = 21.dp)
            }
        }

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                .width(if (state.rightPanelOpen) 276.dp else 46.dp)
                .then(if (state.rightPanelOpen) Modifier.fillMaxHeight(0.82f).heightIn(max = 620.dp) else Modifier.heightIn(min = 96.dp, max = 180.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(StudioColors.Panel.copy(alpha = 0.97f))
                .padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.rightPanelOpen) {
                Row(Modifier.fillMaxWidth().padding(start = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (state.rightPanelTab == PanelTab.LAYERS) Ic.Layers else Ic.Props, StudioColors.Accent, 19.dp)
                    Text(
                        if (state.rightPanelTab == PanelTab.LAYERS) tr("Layers", "لایه‌ها") else tr("Properties", "ویژگی‌ها"),
                        color = StudioColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    IconBtn(Ic.Chevron, { state.rightPanelOpen = false }, size = 36.dp, iconSize = 18.dp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(StudioColors.Divider))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Ic.Layers, { state.rightPanelTab = PanelTab.LAYERS }, active = state.rightPanelTab == PanelTab.LAYERS, size = 38.dp, iconSize = 19.dp)
                    IconBtn(Ic.Props, { state.rightPanelTab = PanelTab.PROPERTIES }, active = state.rightPanelTab == PanelTab.PROPERTIES, size = 38.dp, iconSize = 19.dp)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (state.rightPanelTab == PanelTab.LAYERS) LayersPanel(state) else PropertiesPanel(state)
                }
            } else {
                IconBtn(Ic.Layers, { state.openRightPanel(PanelTab.LAYERS) }, active = state.rightPanelTab == PanelTab.LAYERS, size = 38.dp, iconSize = 21.dp)
                IconBtn(Ic.Props, { state.openRightPanel(PanelTab.PROPERTIES) }, active = state.rightPanelTab == PanelTab.PROPERTIES, size = 38.dp, iconSize = 21.dp)
            }
        }
    }
}

@Composable
private fun ToolGroupHeader(label: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .padding(start = 8.dp, end = 5.dp, top = 9.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = StudioColors.TextMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Icon(if (expanded) Ic.ChevronDown else Ic.Chevron, StudioColors.TextMuted, 14.dp)
    }
}

@Composable
private fun ToolGroupLabel(label: String) {
    Text(label, color = StudioColors.TextMuted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth().padding(start = 9.dp, top = 10.dp, bottom = 3.dp))
}

@Composable
private fun ToolDrawerItem(state: EditorState, tool: Tool, icon: Ic, label: String, close: () -> Unit) {
    val active = state.tool == tool
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(9.dp))
            .background(if (active) StudioColors.AccentSoft else Color.Transparent)
            .clickable {
                state.tool = tool
                if (tool != Tool.PEN) state.penPath = null
                state.changed()
                close()
            }.padding(horizontal = 7.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, if (active) StudioColors.Accent else StudioColors.TextPrimary, 19.dp)
        Text(label, color = if (active) StudioColors.Accent else StudioColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.padding(start = 9.dp))
        if (active) {
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Icon(Ic.Check, StudioColors.Accent, 15.dp)
        }
    }
}

@Composable
private fun ToolDrawerAction(icon: Ic, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick).padding(horizontal = 7.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, StudioColors.TextPrimary, 19.dp)
        Text(label, color = StudioColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.padding(start = 9.dp))
    }
}

@Composable
private fun TopBar(state: EditorState, onBack: () -> Unit, onSave: () -> Unit, onExport: () -> Unit, onMore: () -> Unit, menuOpen: Boolean, onMenuClose: () -> Unit, onPreview: () -> Unit, onRename: () -> Unit) {
    val tick = state.tick
    Row(
        Modifier.fillMaxWidth().height(52.dp).background(StudioColors.Panel).padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Ic.Back, onBack, size = 40.dp)
        Column(
            Modifier.weight(1f).clickable(onClick = onRename).padding(start = 7.dp, end = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.projectName, color = StudioColors.TextPrimary, fontSize = 14.sp, maxLines = 1)
                if (state.dirty) Dot(StudioColors.Warning, 7.dp)
            }
            Text(
                "TGS  ·  ${state.session.doc.width} × ${state.session.doc.height}  ·  ${fmt(state.session.doc.frameRate, 0)} FPS",
                color = StudioColors.TextMuted, fontSize = 10.sp, maxLines = 1,
            )
        }
        Box(Modifier.width(1.dp).height(28.dp).background(StudioColors.Divider))
        IconBtn(Ic.Undo, { state.act { undo() } }, enabled = state.session.history.canUndo && tick >= 0, size = 38.dp, iconSize = 19.dp)
        IconBtn(Ic.Redo, { state.act { redo() } }, enabled = state.session.history.canRedo && tick >= 0, size = 38.dp, iconSize = 19.dp)
        IconBtn(Ic.Save, onSave, enabled = state.dirty, size = 38.dp, iconSize = 19.dp)
        IconBtn(Ic.Export, onExport, size = 38.dp, iconSize = 19.dp, tint = StudioColors.Accent)
        Box {
            IconBtn(Ic.More, onMore, size = 38.dp)
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
    val tools = listOf(
        Triple(Tool.SELECT, Ic.Select, tr("Select", "انتخاب")),
        Triple(Tool.HAND, Ic.Hand, tr("Pan", "جابجایی")),
        Triple(Tool.PEN, Ic.Pen, tr("Pen", "قلم")),
        Triple(Tool.NODE, Ic.Node, tr("Node", "گره")),
        Triple(Tool.ADD_NODE, Ic.AddNode, tr("Add node", "افزودن گره")),
        Triple(Tool.DELETE_NODE, Ic.DeleteNode, tr("Delete node", "حذف گره")),
        Triple(Tool.SMOOTH_NODE, Ic.SmoothNode, tr("Smooth", "نرم")),
        Triple(Tool.CORNER_NODE, Ic.CornerNode, tr("Corner", "گوشه")),
        Triple(Tool.LINE, Ic.Line, tr("Line", "خط")),
        Triple(Tool.RECT, Ic.Rect, tr("Rectangle", "مستطیل")),
        Triple(Tool.ROUNDED_RECT, Ic.RoundedRect, tr("Round rect", "مستطیل گرد")),
        Triple(Tool.ELLIPSE, Ic.Ellipse, tr("Ellipse", "بیضی")),
        Triple(Tool.STAR, Ic.Star, tr("Star", "ستاره")),
        Triple(Tool.POLYGON, Ic.Polygon, tr("Polygon", "چندضلعی")),
        Triple(Tool.ARC, Ic.Arc, tr("Arc", "کمان")),
        Triple(Tool.SPIRAL, Ic.Spiral, tr("Spiral", "مارپیچ")),
        Triple(Tool.DRAW, Ic.Draw, tr("Draw", "نقاشی")),
        Triple(Tool.ERASE, Ic.Eraser, tr("Eraser", "پاک‌کن")),
    )
    Row(
        Modifier.fillMaxWidth().height(66.dp).background(StudioColors.Background).horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(StudioColors.Panel).padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            tools.forEach { (t, ic, label) ->
                Column(
                    Modifier.width(48.dp).clip(RoundedCornerShape(9.dp))
                        .background(if (state.tool == t) StudioColors.AccentSoft else Color.Transparent)
                        .clickable { state.tool = t; if (t != Tool.PEN) state.penPath = null; state.changed() }
                        .padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(ic, if (state.tool == t) StudioColors.Accent else StudioColors.TextPrimary, 20.dp)
                    Text(label, color = if (state.tool == t) StudioColors.Accent else StudioColors.TextMuted, fontSize = 8.sp, maxLines = 1)
                }
            }
        }
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(StudioColors.Panel).padding(horizontal = 3.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(Ic.Text, onText, size = 40.dp, iconSize = 20.dp)
            IconBtn(Ic.Svg, onSvg, size = 40.dp, iconSize = 20.dp)
            IconBtn(Ic.Fit, { state.fit(state.fitSize.x, state.fitSize.y) }, size = 40.dp, iconSize = 20.dp)
        }
        Text(
            "${(state.viewScale / (minOf(state.fitSize.x / state.session.doc.width, state.fitSize.y / state.session.doc.height).coerceAtLeast(0.0001f) * 0.92f) * 100).toInt()}%",
            color = StudioColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 2.dp),
        )
        Box(Modifier.width(1.dp).height(28.dp).background(StudioColors.Divider))
        listOf(Workspace.DESIGN to tr("Design", "طراحی"), Workspace.ANIMATION to tr("Animation", "انیمیشن"), Workspace.ADVANCED to tr("Advanced", "پیشرفته")).forEach { (w, label) ->
            SmallButton(label, { state.setWorkspaceMode(w) }, primary = state.workspace == w, modifier = Modifier.padding(horizontal = 1.dp))
        }
    }
}

@Composable
private fun ContextBars(state: EditorState, modifier: Modifier) {
    val tick = state.tick
    val sel = state.session.selection
    Column(modifier.padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val pen = state.penPath
        if (state.tool in setOf(Tool.LINE, Tool.RECT, Tool.ROUNDED_RECT, Tool.ELLIPSE, Tool.STAR, Tool.POLYGON, Tool.ARC, Tool.SPIRAL)) {
            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(StudioColors.PanelRaised).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Shape options", "تنظیمات شکل"), color = StudioColors.TextMuted, fontSize = 11.sp)
                SmallButton(tr("Constrain", "تناسب ثابت"), { state.constrainShape = !state.constrainShape }, primary = state.constrainShape)
                SmallButton(tr("From center", "از مرکز"), { state.drawFromCenter = !state.drawFromCenter }, primary = state.drawFromCenter)
            }
            when (state.tool) {
                Tool.STAR -> Column(Modifier.clip(RoundedCornerShape(12.dp)).background(StudioColors.PanelRaised).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(tr("Star points: ${state.starPoints}", "تعداد پره‌ها: ${state.starPoints}"), color = StudioColors.TextMuted, fontSize = 11.sp)
                    GestureSlider(state.starPoints.toFloat(), 3f..20f, {}, { state.starPoints = it.toInt().coerceIn(3, 20) }, {})
                }
                Tool.POLYGON -> Column(Modifier.clip(RoundedCornerShape(12.dp)).background(StudioColors.PanelRaised).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(tr("Sides: ${state.polygonSides}", "تعداد اضلاع: ${state.polygonSides}"), color = StudioColors.TextMuted, fontSize = 11.sp)
                    GestureSlider(state.polygonSides.toFloat(), 3f..20f, {}, { state.polygonSides = it.toInt().coerceIn(3, 20) }, {})
                }
                Tool.ROUNDED_RECT -> Column(Modifier.clip(RoundedCornerShape(12.dp)).background(StudioColors.PanelRaised).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(tr("Corner radius: ${fmt(state.rectRoundness.toDouble(), 0)} px", "شعاع گوشه: ${fmt(state.rectRoundness.toDouble(), 0)} پیکسل"), color = StudioColors.TextMuted, fontSize = 11.sp)
                    GestureSlider(state.rectRoundness, 0f..120f, {}, { state.rectRoundness = it.coerceIn(0f, 120f) }, {})
                }
                Tool.ARC -> Column(Modifier.clip(RoundedCornerShape(12.dp)).background(StudioColors.PanelRaised).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(tr("Arc angle: ${state.arcDegrees}°", "زاویه کمان: ${state.arcDegrees}°"), color = StudioColors.TextMuted, fontSize = 11.sp)
                    GestureSlider(state.arcDegrees.toFloat(), 30f..360f, {}, { state.arcDegrees = it.toInt().coerceIn(30, 360) }, {})
                }
                Tool.SPIRAL -> Column(Modifier.clip(RoundedCornerShape(12.dp)).background(StudioColors.PanelRaised).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(tr("Turns: ${state.spiralTurns}", "تعداد دور: ${state.spiralTurns}"), color = StudioColors.TextMuted, fontSize = 11.sp)
                    GestureSlider(state.spiralTurns.toFloat(), 1f..6f, {}, { state.spiralTurns = it.toInt().coerceIn(1, 6) }, {})
                }
                else -> Unit
            }
        }
        if (state.tool in setOf(Tool.ADD_NODE, Tool.DELETE_NODE, Tool.SMOOTH_NODE, Tool.CORNER_NODE)) {
            val instruction = when (state.tool) {
                Tool.ADD_NODE -> tr("Tap a path segment to insert an anchor.", "برای افزودن نقطه روی قطعه مسیر بزنید.")
                Tool.DELETE_NODE -> tr("Tap an anchor to remove it.", "برای حذف، روی نقطه اتصال بزنید.")
                Tool.SMOOTH_NODE -> tr("Tap an anchor to create smooth handles.", "برای ساخت دسته منحنی روی نقطه بزنید.")
                else -> tr("Tap an anchor to make it a sharp corner.", "برای تبدیل به گوشه تیز روی نقطه بزنید.")
            }
            Text(instruction, color = StudioColors.TextMuted, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(StudioColors.PanelRaised).padding(horizontal = 12.dp, vertical = 8.dp))
        }
        if (state.tool == Tool.DRAW) PenSettingsBar(state)
        if (state.tool == Tool.ERASE) {
            Text(tr("Drag over pen strokes to erase them. Shapes are never erased.", "روی خط‌های قلم بکشید تا پاک شوند. شکل‌ها پاک نمی‌شوند."), color = StudioColors.TextMuted, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(StudioColors.PanelRaised).padding(horizontal = 12.dp, vertical = 8.dp))
        }
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
                IconBtn(Ic.Props, { state.openRightPanel(PanelTab.PROPERTIES) }, size = 40.dp, iconSize = 20.dp)
                IconBtn(Ic.Trash, { state.act { deleteSelection() }; state.message = if (state.isFa) "حذف شد — با Undo برمی‌گردد" else "Deleted — Undo brings it back" }, size = 40.dp, iconSize = 20.dp, tint = StudioColors.Danger)
            }
        }
    }
}

@Composable
private fun PlaybackBar(state: EditorState) {
    val doc = state.session.doc
    val lo = doc.inPoint.toFloat(); val hi = max(lo + 1f, (doc.outPoint - 1).toFloat())
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(StudioColors.Panel).padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconBtn(Ic.Prev, { state.playing = false; state.seek(Math.floor(state.frame) - 1) }, size = 38.dp, iconSize = 19.dp)
        IconBtn(if (state.playing) Ic.Pause else Ic.Play, { state.playing = !state.playing }, active = true, size = 40.dp, iconSize = 19.dp)
        IconBtn(Ic.Next, { state.playing = false; state.seek(Math.floor(state.frame) + 1) }, size = 38.dp, iconSize = 19.dp)
        IconBtn(Ic.Loop, { state.loop = !state.loop }, active = state.loop, size = 38.dp, iconSize = 18.dp)
        Slider(
            value = state.frame.toFloat().coerceIn(lo, hi), onValueChange = { state.playing = false; state.seek(Math.rint(it.toDouble())) }, valueRange = lo..hi,
            colors = SliderDefaults.colors(thumbColor = StudioColors.Accent, activeTrackColor = StudioColors.Accent, inactiveTrackColor = StudioColors.PanelHigh),
            modifier = Modifier.weight(1f).padding(horizontal = 5.dp),
        )
        Column(
            Modifier.clip(RoundedCornerShape(9.dp)).background(StudioColors.PanelHigh).padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.frame.toInt().toString(), color = StudioColors.TextPrimary, fontSize = 12.sp)
            Text("/ ${(doc.outPoint - 1).toInt()}", color = StudioColors.TextMuted, fontSize = 9.sp)
        }
        SmallButton("${fmt(doc.frameRate, 0)} fps", { state.act { setFrameRate(if (doc.frameRate == 60.0) 30.0 else 60.0) } }, modifier = Modifier.padding(start = 3.dp))
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
    val items = listOf(PanelTab.TIMELINE to (Ic.Timeline to tr("Timeline", "تایم‌لاین")))
    Row(
        Modifier.fillMaxWidth().height(66.dp).background(StudioColors.Panel).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        items.forEach { (tab, pair) ->
            val active = state.panel == tab
            Column(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(12.dp))
                    .background(if (active) StudioColors.AccentSoft else Color.Transparent)
                    .clickable { state.updatePanel(if (state.panel == tab) null else tab) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(pair.first, if (active) StudioColors.Accent else StudioColors.TextMuted, 21.dp)
                Text(pair.second, color = if (active) StudioColors.Accent else StudioColors.TextMuted, fontSize = 10.sp)
            }
        }
    }
}

/** Colour, thickness, opacity and write-on options of the freehand pen. */
@Composable
private fun PenSettingsBar(state: EditorState) {
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(14.dp)).background(StudioColors.PanelRaised).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Swatch(state.penColor.toColor(), { picking = true }, size = 32.dp)
            Column(Modifier.weight(1f)) {
                Text("${tr("Width", "ضخامت")} ${fmt(state.penWidth.toDouble(), 0)}", color = StudioColors.TextMuted, fontSize = 11.sp)
                GestureSlider(state.penWidth, 1f..40f, {}, { state.penWidth = it }, {})
            }
            Column(Modifier.weight(1f)) {
                Text("${tr("Opacity", "شفافیت")} ${fmt(state.penOpacity.toDouble(), 0)}%", color = StudioColors.TextMuted, fontSize = 11.sp)
                GestureSlider(state.penOpacity, 10f..100f, {}, { state.penOpacity = it }, {})
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton(tr("Write-on", "نوشته‌شدن تدریجی"), { state.penWriteOn = !state.penWriteOn; state.penNextStart = null }, primary = state.penWriteOn, ic = Ic.Draw)
            if (state.penWriteOn) Column(Modifier.weight(1f)) {
                Text("${tr("Speed", "سرعت")} ${fmt(state.penSpeed.toDouble(), 0)} px/f", color = StudioColors.TextMuted, fontSize = 11.sp)
                GestureSlider(state.penSpeed, 2f..40f, {}, { state.penSpeed = it }, {})
            } else Text(tr("Each stroke is drawn gradually when on.", "وقتی روشن است هر خط به‌تدریج کشیده می‌شود."), color = StudioColors.TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        }
    }
    if (picking) ColorPickerDialog(state.penColor, { state.penColor = it }, { picking = false })
}
