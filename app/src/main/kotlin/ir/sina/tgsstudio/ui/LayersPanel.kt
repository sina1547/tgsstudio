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
        list.forEachIndexed { i, l ->
            val ref = LayerRef(scope, i)
            val key = "l:$scope:$i"
            val icon = when (l.type) { LayerType.SHAPE -> Ic.Shape; LayerType.NULL -> Ic.NullLayer; LayerType.PRECOMP -> Ic.Precomp; else -> Ic.Layers }
            val expandable = l.type == LayerType.SHAPE || l.type == LayerType.PRECOMP
            rows += LayerRow(LayerTarget(ref), depth, l.name.ifBlank { "Layer ${l.index ?: i}" }, icon, l.hidden, expandable, key)
            if (key in expanded) {
                if (l.type == LayerType.SHAPE) shapes(ref, l.shapes, emptyList(), depth + 1)
                if (l.type == LayerType.PRECOMP) doc.precomps.firstOrNull { it.id == l.refId }?.let { layers(it.id, it.layers, depth + 1) }
            }
        }
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
    val rowHeight = 48.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }

    Column2 {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Layers, StudioColors.Accent, 20.dp)
            Text(tr("Layers", "لایه‌ها"), color = StudioColors.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp).weight(1f))
            SmallButton(tr("Multi", "چندتایی"), { state.multiSelect = !state.multiSelect }, primary = state.multiSelect)
            Spacer(Modifier.width(6.dp))
            SmallButton(tr("Group", "گروه"), { handleResult(state, state.act { groupSelection() }) }, enabled = state.session.selection.count { it is ShapeTarget } >= 1)
            Spacer(Modifier.width(6.dp))
            IconBtn(Ic.Plus, { state.setPanel(PanelTab.ADD) }, size = 38.dp, iconSize = 20.dp)
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
