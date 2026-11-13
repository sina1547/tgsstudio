package ir.sina.tgsstudio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.sina.tgsstudio.editor.AnimatedProps
import ir.sina.tgsstudio.editor.KeyframeOps
import ir.sina.tgsstudio.editor.LayerTarget
import ir.sina.tgsstudio.editor.PathOps
import ir.sina.tgsstudio.editor.PropEntry
import ir.sina.tgsstudio.editor.PropKind
import ir.sina.tgsstudio.editor.PropWrite
import ir.sina.tgsstudio.editor.ShapeTarget
import ir.sina.tgsstudio.editor.Spaces
import ir.sina.tgsstudio.editor.Target
import ir.sina.tgsstudio.editor.TransformOps
import ir.sina.tgsstudio.renderer.PropEval
import ir.sina.tgsstudio.renderer.Rgba

@Composable
fun propLabel(key: String): String = when (key) {
    "a" -> tr("Anchor", "نقطهٔ لنگر"); "p", "position" -> tr("Position", "موقعیت"); "s" -> tr("Scale", "مقیاس"); "r" -> tr("Rotation", "چرخش")
    "o" -> tr("Opacity", "شفافیت"); "c" -> tr("Color", "رنگ"); "w" -> tr("Stroke width", "ضخامت خط"); "path" -> tr("Path", "مسیر")
    "size" -> tr("Size", "اندازه"); "roundness" -> tr("Roundness", "گردی گوشه"); "points" -> tr("Points", "تعداد نقاط")
    "outer" -> tr("Outer radius", "شعاع بیرونی"); "inner" -> tr("Inner radius", "شعاع درونی")
    "start" -> tr("Trim start %", "شروع برش ٪"); "end" -> tr("Trim end %", "پایان برش ٪"); "offset" -> tr("Trim offset °", "جابه‌جایی برش °")
    else -> key
}

@Composable
fun PropertiesPanel(state: EditorState) {
    val tick = state.tick
    val sel = state.session.selection
    val target = sel.firstOrNull()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Props, StudioColors.Accent, 20.dp)
            Text(tr("Properties", "ویژگی‌ها"), color = StudioColors.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp))
            if (sel.size > 1) Text(tr("${sel.size} selected (editing the first)", "${sel.size} مورد انتخاب شده (ویرایش اولی)"), color = StudioColors.TextMuted, fontSize = 12.sp)
        }
        if (target == null) {
            Text(tr("Select an element on the canvas or in Layers.", "یک عنصر را روی بوم یا در لایه‌ها انتخاب کنید."), color = StudioColors.TextMuted, modifier = Modifier.padding(vertical = 16.dp))
            return@Column
        }
        key(tick) { TransformSection(state, target) }
        key(tick) { AppearanceSection(state, target) }
        key(tick) { ShapeSection(state, target) }
        key(tick) { PathSection(state, target) }
        key(tick) { AnimationSection(state, target) }
        key(tick) { AdvancedSection(state, target) }
    }
}

@Composable
private fun Section(name: String, defaultOpen: Boolean = true, content: @Composable () -> Unit) {
    var open by rememberSaveable(name) { mutableStateOf(defaultOpen) }
    SectionHeader(name, open) { open = !open }
    if (open) Column(Modifier.padding(bottom = 6.dp)) { content() }
}

@Composable
private fun TransformSection(state: EditorState, t: Target) {
    val v = state.session.transformOf(t) ?: return
    var linked by rememberSaveable { mutableStateOf(true) }
    val box = state.session.let { s -> if (t is LayerTarget) ir.sina.tgsstudio.editor.Hit.bounds(s.scene(), t.layer) else ir.sina.tgsstudio.editor.Hit.bounds(s.scene(), t.layer, (t as ShapeTarget).ref.path) }
    Section(tr("Transform", "تبدیل")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(v.x, { state.act { setPosition(t, it, v.y) } }, Modifier.weight(1f), label = "X")
            NumberField(v.y, { state.act { setPosition(t, v.x, it) } }, Modifier.weight(1f), label = "Y")
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(box?.width ?: 0.0, { nw ->
                val b = box ?: return@NumberField
                if (b.width > 0.01) { val f = nw / b.width; if (linked) state.act { scaleBy(b.centerX, b.centerY, f) } else state.act { setScale(t, v.scaleX * f, v.scaleY) } }
            }, Modifier.weight(1f), label = "W")
            NumberField(box?.height ?: 0.0, { nh ->
                val b = box ?: return@NumberField
                if (b.height > 0.01) { val f = nh / b.height; if (linked) state.act { scaleBy(b.centerX, b.centerY, f) } else state.act { setScale(t, v.scaleX, v.scaleY * f) } }
            }, Modifier.weight(1f), label = "H")
            IconBtn(if (linked) Ic.Lock else Ic.Unlock, { linked = !linked }, size = 38.dp, iconSize = 18.dp)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(v.scaleX, { state.act { setScale(t, it, if (linked) it else v.scaleY) } }, Modifier.weight(1f), label = "Sx", suffix = "%")
            NumberField(v.scaleY, { state.act { setScale(t, if (linked) it else v.scaleX, it) } }, Modifier.weight(1f), label = "Sy", suffix = "%")
        }
        Labeled(tr("Rotation", "چرخش")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(v.rotation, { state.act { setRotation(t, it) } }, Modifier.weight(0.45f), suffix = "°")
                GestureSlider(v.rotation.toFloat().coerceIn(-360f, 360f), -360f..360f, { state.session.beginGesture("Rotation") }, { a -> state.session.setRotation(t, a.toDouble()); state.changed() }, { state.session.endGesture(); state.changed() }, Modifier.weight(0.55f))
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(v.anchorX, { state.act { setAnchor(t, it, v.anchorY) } }, Modifier.weight(1f), label = "Ax")
            NumberField(v.anchorY, { state.act { setAnchor(t, v.anchorX, it) } }, Modifier.weight(1f), label = "Ay")
        }
    }
}

@Composable
private fun AppearanceSection(state: EditorState, t: Target) {
    val v = state.session.transformOf(t) ?: return
    var picking by remember { mutableStateOf<String?>(null) }
    val fill = state.session.fillColor(t); val stroke = state.session.strokeColor(t)
    Section(tr("Appearance", "ظاهر")) {
        Labeled(tr("Opacity", "شفافیت")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GestureSlider(v.opacity.toFloat().coerceIn(0f, 100f), 0f..100f, { state.session.beginGesture("Opacity") }, { a -> state.session.setOpacity(t, a.toDouble()); state.changed() }, { state.session.endGesture(); state.changed() }, Modifier.weight(1f))
                NumberField(v.opacity, { state.act { setOpacity(t, it) } }, Modifier.weight(0.4f), decimals = 0, suffix = "%")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Fill", "پرکننده"), color = StudioColors.TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Swatch(fill?.toColor(), { picking = "fill" })
            if (fill != null) IconBtn(Ic.Trash, { state.act { removeFill(t) } }, size = 36.dp, iconSize = 18.dp)
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Stroke", "خط دور"), color = StudioColors.TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Swatch(stroke?.toColor(), { picking = "stroke" })
            if (stroke != null) IconBtn(Ic.Trash, { state.act { removeStroke(t) } }, size = 36.dp, iconSize = 18.dp)
        }
        state.session.strokeWidth(t)?.let { w ->
            Labeled(tr("Stroke width", "ضخامت خط")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GestureSlider(w.toFloat().coerceIn(0f, 60f), 0f..60f, { state.session.beginGesture("Stroke width") }, { a -> state.session.setStrokeWidth(t, a.toDouble()); state.changed() }, { state.session.endGesture(); state.changed() }, Modifier.weight(1f))
                    NumberField(w, { state.act { setStrokeWidth(t, it) } }, Modifier.weight(0.4f))
                }
            }
        }
    }
    picking?.let { which ->
        val initial = (if (which == "fill") fill else stroke) ?: Rgba(0.12, 0.48, 1.0, 1.0)
        ColorPickerDialog(
            initial,
            onPick = { c -> if (!state.session.history.isGestureOpen) state.session.beginGesture("Color"); if (which == "fill") state.session.setFillColor(t, c) else state.session.setStrokeColor(t, c); state.changed() },
            onDone = { state.session.endGesture(); picking = null; state.changed() },
        )
    }
}

@Composable
private fun ShapeSection(state: EditorState, t: Target) {
    val entries = state.session.animatedProps(t).filter { it.depth <= 1 && it.key in SHAPE_KEYS && it.kind != PropKind.COLOR && it.kind != PropKind.PATH }
    if (entries.isEmpty()) return
    Section(tr("Shape", "شکل")) {
        for (e in entries) {
            val vals = state.session.valueAt(e) ?: continue
            Labeled("${propLabel(e.key)}  ·  ${e.owner}") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vals.forEachIndexed { i, d ->
                        if (i < 2 || e.kind == PropKind.NUMBER) NumberField(d, { nv -> val copy = vals.copyOf(); copy[i] = nv; state.act { edit("Shape property") { PropWrite.setValues(e.prop, Spaces.localFrame(index, t.layer.scope, frame), copy) } } }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
private val SHAPE_KEYS = setOf("size", "roundness", "points", "outer", "inner", "start", "end", "offset", "r")

@Composable
private fun PathSection(state: EditorState, t: Target) {
    val ref = state.session.pathShape(); val conv = state.session.convertibleShape()
    if (ref == null && conv == null) return
    Section(tr("Path", "مسیر"), defaultOpen = false) {
        if (ref != null) {
            val b = state.session.pathBezier(ref)
            Text(tr("${b?.count ?: 0} points · ${if (b?.closed == true) "closed" else "open"}", "${b?.count ?: 0} نقطه · ${if (b?.closed == true) "بسته" else "باز"}"), color = StudioColors.TextMuted, fontSize = 13.sp)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(tr("Edit nodes", "ویرایش نقاط"), { state.tool = Tool.NODE; state.changed() }, primary = true, ic = Ic.Node)
                SmallButton(if (b?.closed == true) tr("Open", "باز کردن") else tr("Close", "بستن"), { if (b != null) { state.act { edit("Open/close path") { writePath(ref, PathOps.setClosed(b, !b.closed)) } } } })
            }
            val n = state.nodeIndex
            if (b != null && n in 0 until b.count) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(tr("Smooth", "نرم"), { state.act { edit("Smooth node") { writePath(ref, PathOps.toSmooth(b, n)) } } })
                    SmallButton(tr("Corner", "گوشه"), { state.act { edit("Corner node") { writePath(ref, PathOps.toCorner(b, n)) } } })
                    SmallButton(tr("Delete point", "حذف نقطه"), { if (state.act { structuralPathEdit(ref, "Delete node") { PathOps.delete(it, n) } }) state.nodeIndex = -1 else state.message = if (state.isFa) "مسیر حداقل دو نقطه لازم دارد." else "A path needs at least two points." })
                }
            }
        } else if (conv != null) {
            Text(tr("Convert this shape to a path to edit its points. Animation of its size is baked at the current frame.", "برای ویرایش نقاط، شکل را به مسیر تبدیل کنید. انیمیشنِ اندازهٔ آن در فریم فعلی ثابت می‌شود."), color = StudioColors.TextMuted, fontSize = 13.sp)
            SmallButton(tr("Convert to path", "تبدیل به مسیر"), { state.act { convertToPath() }; state.tool = Tool.NODE }, primary = true, modifier = Modifier.padding(top = 8.dp), ic = Ic.Path)
        }
    }
}

@Composable
private fun AnimationSection(state: EditorState, t: Target) {
    val entries = state.session.animatedProps(t).filter { it.depth == 0 }
    if (entries.isEmpty()) return
    Section(tr("Animation", "انیمیشن"), defaultOpen = false) {
        for (e in entries) {
            val times = KeyframeOps.times(e.prop)
            val hasHere = times.any { kotlin.math.abs(it - Spaces.localFrame(state.session.index, t.layer.scope, state.frame)) < 0.5 }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${propLabel(e.key)}  (${times.size})", color = StudioColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                IconBtn(Ic.Key, {
                    state.act {
                        val lf = Spaces.localFrame(index, t.layer.scope, frame)
                        if (hasHere) removeKeyframe(e, times.indexOfFirst { kotlin.math.abs(it - lf) < 0.5 }) else addKeyframe(e, t.layer.scope)
                    }
                }, size = 38.dp, iconSize = 18.dp, active = hasHere)
            }
        }
    }
}

@Composable
private fun AdvancedSection(state: EditorState, t: Target) {
    val doc = state.session.doc
    Section(tr("Advanced", "پیشرفته"), defaultOpen = false) {
        Text(tr("Canvas ${doc.width}×${doc.height} · ${fmt(doc.frameRate, 0)} fps · ${fmt(doc.durationSeconds, 2)} s", "بوم ${doc.width}×${doc.height} · ${fmt(doc.frameRate, 0)} فریم/ثانیه · ${fmt(doc.durationSeconds, 2)} ثانیه"), color = StudioColors.TextMuted, fontSize = 13.sp)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("30 fps", { state.act { setFrameRate(30.0) } }, primary = doc.frameRate == 30.0)
            SmallButton("60 fps", { state.act { setFrameRate(60.0) } }, primary = doc.frameRate == 60.0)
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Hidden", "پنهان"), color = StudioColors.TextPrimary, modifier = Modifier.weight(1f))
            val hidden = (t as? LayerTarget)?.let { state.session.index.layer(it.layer)?.hidden } ?: (t as? ShapeTarget)?.let { state.session.index.shape(it.ref)?.hidden } ?: false
            Switch(hidden, { state.act { setHidden(t, it) } }, colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.Accent))
        }
    }
}
