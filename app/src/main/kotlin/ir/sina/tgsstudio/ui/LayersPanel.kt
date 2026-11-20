package ir.sina.tgsstudio.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.sina.tgsstudio.document.LayerNode
import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.LayerType
import ir.sina.tgsstudio.document.ShapeKind
import ir.sina.tgsstudio.document.ShapeRef
import ir.sina.tgsstudio.editor.LayerTarget
import ir.sina.tgsstudio.editor.OpResult
import ir.sina.tgsstudio.editor.ShapeTarget
import ir.sina.tgsstudio.editor.Target
import kotlin.math.roundToInt

private class LayerRow(val target: Target, val depth: Int, val name: String, val icon: Ic, val hidden: Boolean, val expandable: Boolean, val key: String)

private fun buildRows(state: EditorState, expanded: Set<String>): List<LayerRow> {
    val doc = state.session.doc
    val rows = ArrayList<LayerRow>()
    fun shapes(layer: LayerRef, items: List<ir.sina.tgsstudio.document.ShapeItem>, prefix: List<Int>, depth: Int) {
        items.forEachIndexed { i, s ->
            if (s.kind == ShapeKind.TRANSFORM) return@forEachIndexed
            val path = prefix + i
            val key = "s:${layer.scope}:${layer.index}:${path.joinToString(".")}"
            val isGroup = s.kind == ShapeKind.GROUP
            val icon = when (s.kind) { ShapeKind.GROUP -> Ic.Folder; ShapeKind.PATH -> Ic.Path; ShapeKind.RECT -> Ic.Rect; ShapeKind.ELLIPSE -> Ic.Ellipse; ShapeKind.STAR -> Ic.Star; else -> Ic.Shape }
            rows += LayerRow(ShapeTarget(ShapeRef(layer, path)), depth, s.name.ifBlank { s.typeCode ?: "" }, icon, s.hidden, isGroup, key)
            if (isGroup && key in expanded) shapes(layer, s.children, path, depth + 1)
        }
    }
    fun layers(scope: String?, list: List<ir.sina.tgsstudio.document.Layer>, depth: Int) {
        // the parent/child tree: children sit under their parent (a layer's `parent` field), siblings keep document order
        fun emit(node: LayerNode, d: Int) {
            val l = node.layer
            val i = list.indexOfFirst { it === l }
            val ref = LayerRef(scope, i)
            val key = "l:$scope:$i"
            val icon = when (l.type) { LayerType.SHAPE -> Ic.Shape; LayerType.NULL -> Ic.NullLayer; LayerType.PRECOMP -> Ic.Precomp; else -> Ic.Layers }
            val expandable = node.children.isNotEmpty() || l.type == LayerType.SHAPE || l.type == LayerType.PRECOMP
            rows += LayerRow(LayerTarget(ref), d, l.name.ifBlank { "Layer ${l.index ?: i}" }, icon, l.hidden, expandable, key)
            if (key in expanded) {
                node.children.forEach { emit(it, d + 1) }
                if (l.type == LayerType.SHAPE) shapes(ref, l.shapes, emptyList(), d + 1)
                if (l.type == LayerType.PRECOMP) doc.precomps.firstOrNull { it.id == l.refId }?.let { layers(it.id, it.layers, d + 1) }
            }
        }
        LayerNode.build(list).forEach { emit(it, depth) }
    }
    layers(null, doc.layers, 0)
    return rows
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LayersPanel(state: EditorState) {
    val tick = state.tick
    val expanded = remember { mutableStateListOf<String>() }
    val rows = remember(tick, expanded.toList()) { buildRows(state, expanded.toSet()) }
    var renaming by remember { mutableStateOf<Target?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<List<LayerRef>?>(null) }
    val selectedLayers = state.session.selection.filterIsInstance<LayerTarget>().map { it.layer }
    val rowHeight = 48.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }

    Column2 {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Layers, StudioColors.Accent, 20.dp)
            Text(tr("Layers", "لایه‌ها"), color = StudioColors.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp).weight(1f))
            SmallButton(tr("Multi", "چندتایی"), { state.multiSelect = !state.multiSelect }, primary = state.multiSelect)
            Spacer(Modifier.width(6.dp))
            if (selectedLayers.isNotEmpty()) { SmallButton(tr("Parent", "والد"), { picking = selectedLayers }, ic = Ic.Link); Spacer(Modifier.width(6.dp)) }
            SmallButton(tr("Group", "گروه"), { handleResult(state, state.act { groupSelection() }) }, enabled = state.session.selection.count { it is ShapeTarget } >= 1)
            Spacer(Modifier.width(6.dp))
            IconBtn(Ic.Plus, { state.updatePanel(PanelTab.ADD) }, size = 38.dp, iconSize = 20.dp)
        }
        if (rows.isEmpty()) Text(tr("No layers yet. Use Add to create a shape.", "هنوز لایه‌ای نیست. با «افزودن» یک شکل بسازید."), color = StudioColors.TextMuted, modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(rows, key = { it.key }) { row ->
                val selected = row.target in state.session.selection
                val locked = state.session.isLocked(row.target.layer) && row.target is LayerTarget
                var dragY by remember { mutableStateOf(0f) }
                Row(
                    Modifier.fillMaxWidth().height(rowHeight)
                        .background(if (selected) StudioColors.AccentSoft else StudioColors.Panel)
                        .combinedClickable(
                            onClick = {
                                val cur = state.session.selection
                                state.act { select(if (state.multiSelect) (if (row.target in cur) cur - row.target else cur + row.target) else listOf(row.target)) }
                            },
                            onLongClick = { state.multiSelect = true; state.act { select(state.session.selection + row.target) } },
                        )
                        .pointerInput(row.key) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragY = 0f },
                                onDrag = { _, d -> dragY += d.y },
                                onDragEnd = { val delta = (dragY / rowPx).roundToInt(); if (delta != 0) state.act { move(row.target, delta) } },
                            )
                        }
                        .padding(start = (8 + row.depth * 18).dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.expandable) {
                        IconBtn(if (row.key in expanded) Ic.ChevronDown else Ic.Chevron, { if (row.key in expanded) expanded.remove(row.key) else expanded.add(row.key) }, size = 32.dp, iconSize = 16.dp)
                    } else Spacer(Modifier.width(32.dp))
                    Icon(row.icon, if (row.hidden) StudioColors.TextMuted else StudioColors.TextPrimary, 20.dp)
                    Text(
                        row.name, color = if (row.hidden) StudioColors.TextMuted else StudioColors.TextPrimary, fontSize = 14.sp, maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp).weight(1f),
                    )
                    IconBtn(if (row.hidden) Ic.EyeOff else Ic.Eye, { state.act { setHidden(row.target, !row.hidden) } }, size = 38.dp, iconSize = 18.dp)
                    if (row.target is LayerTarget) IconBtn(if (locked) Ic.Lock else Ic.Unlock, { state.act { toggleLock(row.target.layer) } }, size = 38.dp, iconSize = 18.dp, tint = if (locked) StudioColors.Warning else null)
                    Box {
                        IconBtn(Ic.More, { menuFor = row.key }, size = 38.dp, iconSize = 18.dp)
                        DropdownMenu(menuFor == row.key, { menuFor = null }, containerColor = StudioColors.PanelRaised) {
                            @Composable fun item(label: String, onClick: () -> Unit) = DropdownMenuItem(text = { Text(label, color = StudioColors.TextPrimary) }, onClick = { menuFor = null; onClick() })
                            item(tr("Rename", "تغییر نام")) { renaming = row.target }
                            if (row.target is LayerTarget) {
                                val sel = if (row.target.layer in selectedLayers) selectedLayers else listOf(row.target.layer)
                                item(tr("Set parent…", "تعیین والد…")) { picking = sel }
                                if (sel.any { state.session.parentOf(it) != null }) item(tr("Detach from parent", "جدا کردن از والد")) { reportParent(state, state.act { setParent(sel, null) }, expanded, null) }
                                item(tr("Create parent (Null)", "ساخت والد (Null)")) { state.act { createNullParent(sel) } }
                            }
                            if (row.target is LayerTarget && row.target.layer in selectedLayers && selectedLayers.size == 2) {
                                val source = selectedLayers.first { it != row.target.layer }
                                item(tr("Replace contents from other selected layer", "جایگزینی محتوا از لایه انتخاب‌شده دیگر")) {
                                    handleResult(state, state.act { replaceLayerContents(row.target.layer, source) })
                                }
                            }
                            item(tr("Duplicate", "تکثیر")) { state.act { select(listOf(row.target)); duplicateSelection() } }
                            item(tr("Move up", "بالا بردن")) { state.act { move(row.target, -1) } }
                            item(tr("Move down", "پایین بردن")) { state.act { move(row.target, 1) } }
                            if (row.target is ShapeTarget) {
                                if (row.icon == Ic.Folder) item(tr("Ungroup", "خارج کردن از گروه")) { state.act { select(listOf(row.target)); handleResult(state, ungroupSelection()) } }
                                if (row.target.ref.parent != null) item(tr("Move out of group", "انتقال به بیرون گروه")) { handleResult(state, state.act { moveShapeOut(row.target) }) }
                                val sel = state.session.selection.filterIsInstance<ShapeTarget>().filter { it != row.target }
                                if (row.icon == Ic.Folder && sel.isNotEmpty()) item(tr("Move selected into this group", "انتقال انتخاب‌شده‌ها به این گروه")) { sel.forEach { s -> handleResult(state, state.act { moveShapeInto(s, row.target) }) } }
                            }
                            item(tr("Delete", "حذف")) { state.act { select(listOf(row.target)); deleteSelection() } }
                        }
                    }
                }
            }
        }
    }

    picking?.let { kids ->
        ParentPickerDialog(state, kids, onPick = { parent ->
            picking = null
            reportParent(state, state.act { setParent(kids, parent) }, expanded, parent)
        }, onDismiss = { picking = null })
    }

    renaming?.let { t ->
        var text by remember(t) { mutableStateOf(state.session.let { Targets2.name(it, t) }) }
        AlertDialog(
            onDismissRequest = { renaming = null }, containerColor = StudioColors.Panel,
            title = { Text(tr("Rename", "تغییر نام"), color = StudioColors.TextPrimary) },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton({ state.act { rename(t, text.ifBlank { "Layer" }) }; renaming = null }) { Text(tr("OK", "تأیید"), color = StudioColors.Accent) } },
            dismissButton = { TextButton({ renaming = null }) { Text(tr("Cancel", "انصراف"), color = StudioColors.TextMuted) } },
        )
    }
}

internal fun handleResult(state: EditorState, r: OpResult) {
    if (r is OpResult.Refused) {
        state.message = when (r.reason) {
            OpResult.Reason.GROUP_HAS_TRANSFORM -> if (state.isFa) "این گروه تبدیل (Transform) دارد؛ اول آن را به حالت پیش‌فرض برگردانید." else "This group has a transform. Reset it before ungrouping."
            OpResult.Reason.WOULD_NEST_INTO_SELF -> if (state.isFa) "یک گروه را نمی‌توان داخل خودش گذاشت." else "A group can't be moved into itself."
            OpResult.Reason.NOT_FOUND -> if (state.isFa) "عنصر پیدا نشد." else "Element not found."
            OpResult.Reason.INCOMPATIBLE -> if (state.isFa) "جایگزینی فقط بین دو لایه برداری (Shape) در یک ترکیب امکان‌پذیر است." else "Replace works only between two vector shape layers in the same composition."
            else -> if (state.isFa) "این کار روی انتخاب فعلی ممکن نیست." else "That isn't possible for the current selection."
        }
        state.changed()
    }
}

internal object Targets2 {
    fun name(s: ir.sina.tgsstudio.editor.EditorSession, t: Target): String = ir.sina.tgsstudio.editor.Targets.name(s.index, t)
}

@Composable
internal fun Column2(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth(), content = content)

/** Tells the user what a parent/detach operation did; opens the parent so the new hierarchy is visible. */
internal fun reportParent(state: EditorState, r: ir.sina.tgsstudio.editor.ParentResult, expanded: MutableList<String>, parent: LayerRef?) {
    if (r.refused != null) {
        state.message = when (r.refused) {
            OpResult.Reason.WOULD_NEST_INTO_SELF -> if (state.isFa) "این اتصال یک حلقه می‌سازد: یک لایه نمی‌تواند والدِ والدِ خودش باشد." else "That would create a loop: a layer can't be the parent of its own parent."
            else -> if (state.isFa) "این اتصال ممکن نیست." else "That link isn't possible."
        }
    } else {
        parent?.let { p -> val k = "l:${p.scope}:${p.index}"; if (k !in expanded) expanded.add(k) }
        state.message = when {
            r.movedLayers.isNotEmpty() -> if (state.isFa) "${r.movedLayers.size} لایه انیمیشن یا کجی دارند؛ مقدارهایشان حفظ شد و ممکن است جابه‌جا شوند." else "${r.movedLayers.size} layer(s) have animation or skew; their values were kept, so they may shift."
            parent == null -> if (state.isFa) "از والد جدا شد؛ جایش تغییر نکرد." else "Detached; position unchanged."
            else -> if (state.isFa) "وصل شد؛ جای لایه تغییر نکرد." else "Linked; position unchanged."
        }
    }
    state.changed()
}

@Composable
private fun ParentPickerDialog(state: EditorState, children: List<LayerRef>, onPick: (LayerRef?) -> Unit, onDismiss: () -> Unit) {
    val doc = state.session.doc
    val candidates = remember(state.tick, children) { state.session.parentCandidates(children) }
    val names = remember(state.tick) { doc.layers.map { it.name.ifBlank { "Layer" } } }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = StudioColors.Panel,
        title = { Text(tr("Choose a parent", "والد را انتخاب کنید"), color = StudioColors.TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    tr("The layer follows its parent's movement, rotation and scale and keeps its place. Opacity is not inherited.", "لایه حرکت، چرخش و اندازهٔ والد را دنبال می‌کند و جایش تغییر نمی‌کند. شفافیت منتقل نمی‌شود."),
                    color = StudioColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(tr("None (detach)", "هیچ‌کدام (جدا کردن)"), color = StudioColors.Danger, modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 12.dp))
                candidates.forEach { c ->
                    Text(names.getOrElse(c.index) { "Layer" }, color = StudioColors.TextPrimary, modifier = Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 12.dp))
                }
                if (candidates.isEmpty()) Text(tr("No other layer can be a parent.", "لایهٔ دیگری برای والد شدن نیست."), color = StudioColors.TextMuted)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "انصراف"), color = StudioColors.TextMuted) } },
    )
}
