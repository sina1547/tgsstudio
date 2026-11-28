package ir.sina.tgsstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import ir.sina.tgsstudio.editor.GradientValues
import ir.sina.tgsstudio.editor.LayerTarget
import ir.sina.tgsstudio.editor.PathOps
import ir.sina.tgsstudio.editor.PropEntry
import ir.sina.tgsstudio.editor.PropKind
import ir.sina.tgsstudio.editor.PropWrite
import ir.sina.tgsstudio.editor.ShapeTarget
import ir.sina.tgsstudio.editor.Spaces
import ir.sina.tgsstudio.editor.Target
import ir.sina.tgsstudio.editor.TransformOps
import ir.sina.tgsstudio.editor.TrimAnim
import ir.sina.tgsstudio.renderer.PropEval
import ir.sina.tgsstudio.renderer.Rgba

@Composable
fun propLabel(key: String): String = when (key) {
    "a" -> tr("Anchor", "نقطهٔ لنگر"); "p", "position" -> tr("Position", "موقعیت"); "s" -> tr("Scale", "مقیاس"); "r" -> tr("Rotation", "چرخش")
    "o" -> tr("Opacity", "شفافیت"); "c" -> tr("Color", "رنگ"); "w" -> tr("Stroke width", "ضخامت خط"); "path" -> tr("Path", "مسیر")
    "gradient" -> tr("Gradient stops", "ایستگاه‌های گرادیان"); "start" -> tr("Gradient start", "شروع گرادیان"); "end" -> tr("Gradient end", "پایان گرادیان")
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
    var activeSection by rememberSaveable { mutableStateOf("transform") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Props, StudioColors.Accent, 20.dp)
            Text(tr("Properties", "ویژگی‌ها"), color = StudioColors.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp))
            if (sel.size > 1) Text(tr("${sel.size} selected · first layer", "${sel.size} انتخاب · ویرایش مورد اول"), color = StudioColors.TextMuted, fontSize = 11.sp)
        }
        if (target == null) {
            if (state.tool == Tool.RECT) ShapeSection(state, null)
            else Text(tr("Select an element on the canvas or in Layers.", "یک عنصر را روی بوم یا در لایه‌ها انتخاب کنید."), color = StudioColors.TextMuted, modifier = Modifier.padding(vertical = 16.dp))
            return@Column
        }
        val sections = listOf(
            "transform" to tr("Transform", "تبدیل"),
            "appearance" to tr("Appearance", "ظاهر"),
            "shape" to tr("Shape", "شکل"),
            "animation" to tr("Animation", "انیمیشن"),
            "effects" to tr("Effects", "افکت‌ها"),
            "path" to tr("Path / Mask", "مسیر / ماسک"),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            sections.forEach { (key, label) ->
                SmallButton(label, { activeSection = key }, primary = activeSection == key)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(StudioColors.Divider))
        Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            when (activeSection) {
                "transform" -> {
                    TransformSection(state, target)
                    if (target is LayerTarget) LayerTimingSection(state, target)
                }
                "appearance" -> AppearanceSection(state, target)
                "shape" -> {
                    ShapeSection(state, target)
                    TrimSection(state, target)
                }
                "animation" -> AnimationSection(state, target)
                "effects" -> EffectsSection(state, target)
                "path" -> {
                    PathSection(state, target)
                    if (target is LayerTarget) MaskSection(state, target)
                }
                else -> TransformSection(state, target)
            }
        }
    }
}

@Composable
private fun MaskSection(state: EditorState, target: LayerTarget) {
    val masks = state.session.maskInversions(target.layer)
    val selectedPath = state.session.selection.filterIsInstance<ShapeTarget>()
        .firstOrNull { it.layer == target.layer && it.ref.path.size == 1 }
    Section(tr("Masks", "ماسک‌ها"), defaultOpen = false) {
        SmallButton(
            tr("Create from selected path", "ساخت از مسیر انتخاب‌شده"),
            {
                val ok = selectedPath?.let { state.act { addMaskFromPath(target.layer, it.ref) } } ?: false
                state.message = if (ok) (if (state.isFa) "ماسک ساخته شد" else "Mask created") else (if (state.isFa) "یک مسیر مستقیم از همین لایه انتخاب کنید" else "Select a top-level path in this layer")
            },
            enabled = selectedPath != null,
            ic = Ic.Add,
        )
        if (masks.isEmpty()) {
            Text(tr("No masks in this layer.", "این لایه ماسکی ندارد."), color = StudioColors.TextMuted, fontSize = 12.sp)
        } else {
            masks.forEachIndexed { index, inverted ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${tr("Mask", "ماسک")} ${index + 1}", color = StudioColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(tr("Invert", "معکوس"), color = StudioColors.TextMuted, fontSize = 11.sp)
                    Switch(
                        checked = inverted,
                        onCheckedChange = { state.act { setMaskInverted(target.layer, index, it) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = StudioColors.Accent),
                    )
                    IconBtn(Ic.Trash, { state.act { removeMask(target.layer, index) } }, size = 34.dp, iconSize = 17.dp)
                }
            }
        }
    }
}

@Composable
private fun LayerTimingSection(state: EditorState, target: LayerTarget) {
    val timing = state.session.layerTiming(target.layer) ?: return
    Section(tr("Layer timing", "زمان‌بندی لایه"), defaultOpen = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(timing.inPoint, { state.act { setLayerTiming(target.layer, timing.copy(inPoint = it)) } }, Modifier.weight(1f), decimals = 0, label = "In")
            NumberField(timing.outPoint, { state.act { setLayerTiming(target.layer, timing.copy(outPoint = it)) } }, Modifier.weight(1f), decimals = 0, label = "Out")
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(timing.startTime, { state.act { setLayerTiming(target.layer, timing.copy(startTime = it)) } }, Modifier.weight(1f), decimals = 1, label = "Start")
            NumberField(timing.stretch, { state.act { setLayerTiming(target.layer, timing.copy(stretch = it)) } }, Modifier.weight(1f), decimals = 2, label = "Stretch")
        }
        Text(tr("Timing values are in composition frames; stretch 1.0 is normal speed.", "مقادیر زمان برحسب فریم کامپوزیشن هستند؛ ضریب ۱ سرعت عادی است."), color = StudioColors.TextMuted, fontSize = 11.sp)
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
            StepperField(v.rotation, { state.act { setRotation(t, it) } }, Modifier.fillMaxWidth(), decimals = 1, suffix = "°", step = 1.0, min = -360.0, max = 360.0)
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
    val gradient = state.session.gradientOf(t)
    Section(tr("Appearance", "ظاهر")) {
        Labeled(tr("Opacity", "شفافیت")) {
            StepperField(v.opacity, { state.act { setOpacity(t, it) } }, Modifier.fillMaxWidth(), decimals = 0, suffix = "%", step = 1.0, min = 0.0, max = 100.0)
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
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Gradient", "گرادیان"), color = StudioColors.TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (gradient == null) {
                SmallButton(tr("Add", "افزودن"), { state.act { addGradientFill(t) } }, ic = Ic.Add)
            } else {
                SmallButton(tr("Add stop", "افزودن توقف"), {
                    val sorted = gradient.stops.sortedBy { it.position }
                    val gap = sorted.zipWithNext().maxByOrNull { (a, b) -> b.position - a.position }
                    if (gap != null) {
                        val position = (gap.first.position + gap.second.position) / 2.0
                        val a = gap.first.color; val b = gap.second.color
                        val mixed = Rgba((a.r + b.r) / 2, (a.g + b.g) / 2, (a.b + b.b) / 2, (a.a + b.a) / 2)
                        val next = (sorted + ir.sina.tgsstudio.editor.GradientColorStop(position, mixed)).sortedBy { it.position }
                        state.act { setGradient(t, gradient.copy(stops = next)) }
                    }
                }, ic = Ic.Add)
                IconBtn(Ic.Trash, { state.act { removeGradientFill(t) } }, size = 36.dp, iconSize = 18.dp)
            }
        }
        if (gradient != null) {
            val orderedStops = gradient.stops.sortedBy { it.position }
            if (orderedStops.size >= 2) {
                Box(
                    Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(*orderedStops.map { it.position.toFloat() to it.color.toColor() }.toTypedArray())),
                )
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton(tr("Linear", "خطی"), { state.act { setGradient(t, gradient.copy(radial = false)) } }, primary = !gradient.radial)
                SmallButton(tr("Radial", "شعاعی"), { state.act { setGradient(t, gradient.copy(radial = true)) } }, primary = gradient.radial)
            }
            gradient.stops.forEachIndexed { stopIndex, stop ->
                Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("${(stop.position * 100).toInt()}%", color = StudioColors.TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Swatch(stop.color.toColor(), { picking = "gradientStop:${stopIndex}" })
                    NumberField(stop.position * 100.0, { percent ->
                        val updated = gradient.stops.toMutableList()
                        updated[stopIndex] = stop.copy(position = (percent / 100.0).coerceIn(0.0, 1.0))
                        state.act { setGradient(t, gradient.copy(stops = updated.sortedBy { it.position })) }
                    }, Modifier.weight(1f), decimals = 0, suffix = "%")
                    if (gradient.stops.size > 2) IconBtn(Ic.Trash, {
                        val updated = gradient.stops.toMutableList().also { it.removeAt(stopIndex) }
                        state.act { setGradient(t, gradient.copy(stops = updated)) }
                    }, size = 32.dp, iconSize = 16.dp)
                }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(gradient.startX, { state.act { setGradient(t, gradient.copy(startX = it)) } }, Modifier.weight(1f), label = "X1")
                NumberField(gradient.startY, { state.act { setGradient(t, gradient.copy(startY = it)) } }, Modifier.weight(1f), label = "Y1")
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(gradient.endX, { state.act { setGradient(t, gradient.copy(endX = it)) } }, Modifier.weight(1f), label = "X2")
                NumberField(gradient.endY, { state.act { setGradient(t, gradient.copy(endY = it)) } }, Modifier.weight(1f), label = "Y2")
            }
        }
        state.session.strokeWidth(t)?.let { w ->
            Labeled(tr("Stroke width", "ضخامت خط")) {
                StepperField(w, { state.act { setStrokeWidth(t, it) } }, Modifier.fillMaxWidth(), decimals = 1, step = 0.5, min = 0.0, max = 60.0)
            }
        }
    }
    picking?.let { which ->
        val initial = when (which) {
            "fill" -> fill
            "stroke" -> stroke
            "gradientStart" -> gradient?.startColor
            "gradientEnd" -> gradient?.endColor
            else -> if (which.startsWith("gradientStop:")) gradient?.stops?.getOrNull(which.substringAfter(":").toIntOrNull() ?: -1)?.color else null
        } ?: Rgba(0.12, 0.48, 1.0, 1.0)
        ColorPickerDialog(
            initial,
            onPick = { c ->
                if (!state.session.history.isGestureOpen) state.session.beginGesture("Color")
                when (which) {
                    "fill" -> state.session.setFillColor(t, c)
                    "stroke" -> state.session.setStrokeColor(t, c)
                    "gradientStart" -> gradient?.let { state.session.setGradient(t, it.copy(stops = it.stops.mapIndexed { i, stop -> if (i == 0) stop.copy(color = c) else stop })) }
                    "gradientEnd" -> gradient?.let { state.session.setGradient(t, it.copy(stops = it.stops.mapIndexed { i, stop -> if (i == it.stops.lastIndex) stop.copy(color = c) else stop })) }
                    else -> if (which.startsWith("gradientStop:")) {
                        val stopIndex = which.substringAfter(":").toIntOrNull() ?: -1
                        gradient?.let { g -> if (stopIndex in g.stops.indices) state.session.setGradient(t, g.copy(stops = g.stops.mapIndexed { i, stop -> if (i == stopIndex) stop.copy(color = c) else stop })) }
                    }
                }
                state.changed()
            },
            onDone = { state.session.endGesture(); picking = null; state.changed() },
            onEyedropper = {
                state.eyedropperCallback = { sampled ->
                    if (!state.session.history.isGestureOpen) state.session.beginGesture("Eyedropper color")
                    when (which) {
                        "fill" -> state.session.setFillColor(t, sampled)
                        "stroke" -> state.session.setStrokeColor(t, sampled)
                        "gradientStart" -> gradient?.let { state.session.setGradient(t, it.copy(stops = it.stops.mapIndexed { i, stop -> if (i == 0) stop.copy(color = sampled) else stop })) }
                        "gradientEnd" -> gradient?.let { state.session.setGradient(t, it.copy(stops = it.stops.mapIndexed { i, stop -> if (i == it.stops.lastIndex) stop.copy(color = sampled) else stop })) }
                        else -> if (which.startsWith("gradientStop:")) {
                            val stopIndex = which.substringAfter(":").toIntOrNull() ?: -1
                            gradient?.let { g -> if (stopIndex in g.stops.indices) state.session.setGradient(t, g.copy(stops = g.stops.mapIndexed { i, stop -> if (i == stopIndex) stop.copy(color = sampled) else stop })) }
                        }
                    }
                    state.session.endGesture()
                    state.changed()
                }
                state.eyedropperActive = true
                picking = null
                state.message = if (state.isFa) "روی رنگ موردنظر در بوم بزنید" else "Tap a color on the composition"
            },
        )
    }
}

@Composable
private fun ShapeSection(state: EditorState, t: Target?) {
    val entries = t?.let { target ->
        state.session.animatedProps(target).filter {
            it.depth <= 3 && it.key in SHAPE_KEYS && it.kind != PropKind.COLOR && it.kind != PropKind.PATH
        }
    } ?: emptyList()
    if (entries.isEmpty() && state.tool != Tool.RECT) return
    val rectangleRadius = entries.firstOrNull {
        it.key == "roundness" || (it.key == "r" && it.owner.contains("rect", ignoreCase = true))
    }
    Section(tr("Shape", "شکل")) {
        // Creation default: available before drawing a rectangle.
        if (state.tool == Tool.RECT) {
            Text(tr("Corner radius (0 = square)", "گردی گوشه‌ها (صفر = مستطیل معمولی)"), color = StudioColors.TextMuted, fontSize = 12.sp)
            StepperField(state.rectRoundness.toDouble(), { state.rectRoundness = it.toFloat().coerceIn(0f, 120f) }, Modifier.fillMaxWidth(), decimals = 0, suffix = "px", step = 1.0, min = 0.0, max = 120.0)
        }

        // Selected rectangle: edit the actual Rectangle Path roundness property after creation.
        if (t != null && rectangleRadius != null) {
            val radius = (state.session.valueAt(rectangleRadius)?.firstOrNull() ?: 0.0).coerceIn(0.0, 120.0)
            Labeled(tr("Corner radius", "گردی گوشه‌ها")) {
                StepperField(
                    radius,
                    { value ->
                        val next = value.coerceIn(0.0, 120.0)
                        state.act {
                            edit("Rectangle corner radius") {
                                PropWrite.setValues(
                                    rectangleRadius.prop,
                                    Spaces.localFrame(index, t.layer.scope, frame),
                                    doubleArrayOf(next),
                                )
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                    decimals = 0,
                    suffix = "px",
                    step = 1.0,
                    min = 0.0,
                    max = 120.0,
                )
            }
        }

        if (t != null) {
            for (e in entries.filterNot { it === rectangleRadius }) {
                val vals = state.session.valueAt(e) ?: continue
                Labeled("${propLabel(e.key)}  ·  ${e.owner}") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vals.forEachIndexed { i, d ->
                            if (i < 2 || e.kind == PropKind.NUMBER) NumberField(d, { nv ->
                                val copy = vals.copyOf(); copy[i] = nv
                                state.act {
                                    edit("Shape property") {
                                        PropWrite.setValues(e.prop, Spaces.localFrame(index, t.layer.scope, frame), copy)
                                    }
                                }
                            }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
private val SHAPE_KEYS = setOf("size", "roundness", "points", "outer", "inner", "r")

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
            SmallButton(
                tr("Reverse direction", "معکوس‌کردن جهت مسیر"),
                {
                    val changed = state.act { structuralPathEdit(ref, "Reverse path direction") { PathOps.reverse(it) } }
                    if (changed) state.nodeIndex = -1
                },
                modifier = Modifier.padding(top = 6.dp),
                enabled = (b?.count ?: 0) >= 2,
                ic = Ic.Path,
            )
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
    val entries = state.session.animatedProps(t).filter { it.depth <= 3 }
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
private fun TrimSection(state: EditorState, t: Target) {
    val session = state.session
    if (!session.canTrim(t)) return
    var duration by rememberSaveable { mutableStateOf(45.0) }
    val has = session.hasTrim(t)
    Section(tr("Trim Paths", "برش مسیر"), defaultOpen = has) {
        if (!has) {
            Text(tr("Draws or erases a path gradually. Start, End and Offset can all be keyframed.", "مسیر را به‌تدریج می‌کشد یا پاک می‌کند. شروع، پایان و جابه‌جایی هر سه کیفریم‌پذیرند."), color = StudioColors.TextMuted, fontSize = 13.sp)
            SmallButton(tr("Add Trim Paths", "افزودن برش مسیر"), { state.act { addTrim(t) } }, primary = true, modifier = Modifier.padding(top = 8.dp), ic = Ic.Path)
            return@Section
        }
        val lf = Spaces.localFrame(session.index, t.layer.scope, state.frame)
        for (e in session.animatedProps(t).filter { it.key == "start" || it.key == "end" || it.key == "offset" }) {
            val v = session.valueAt(e)?.firstOrNull() ?: 0.0
            val here = KeyframeOps.times(e.prop).indexOfFirst { kotlin.math.abs(it - lf) < 0.5 }
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when (e.key) { "start" -> tr("Trim start %", "شروع برش ٪"); "end" -> tr("Trim end %", "پایان برش ٪"); else -> tr("Trim offset °", "جابه‌جایی برش °") }, color = StudioColors.TextMuted, fontSize = 12.sp, modifier = Modifier.weight(0.9f))
                NumberField(v, { nv -> state.act { edit("Trim") { PropWrite.setValues(e.prop, lf, doubleArrayOf(nv)) } } }, Modifier.weight(1f), suffix = if (e.key == "offset") "°" else "%")
                IconBtn(Ic.Key, { state.act { if (here >= 0) removeKeyframe(e, here) else addKeyframe(e, t.layer.scope) } }, size = 38.dp, iconSize = 18.dp, active = here >= 0)
            }
        }
        Labeled(tr("Duration", "مدت")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(duration, { duration = it.coerceAtLeast(2.0) }, Modifier.weight(1f), decimals = 0, suffix = tr("frames", "فریم"))
                Text("= ${fmt(duration / session.doc.frameRate, 2)} ${tr("s", "ثانیه")}", color = StudioColors.TextMuted, fontSize = 12.sp)
            }
        }
        Text(tr("Plays from the playhead (frame ${state.frame.toInt()}):", "از جای پخش‌کننده (فریم ${state.frame.toInt()}) اجرا می‌شود:"), color = StudioColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton(tr("Draw on", "کشیده شدن"), { state.act { animateTrim(t, TrimAnim.DRAW_ON, state.frame, duration) }; state.seek(state.frame); state.playing = true }, primary = true)
            SmallButton(tr("Draw off", "جمع شدن"), { state.act { animateTrim(t, TrimAnim.DRAW_OFF, state.frame, duration) }; state.seek(state.frame); state.playing = true })
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton(tr("Erase from start", "پاک شدن از ابتدا"), { state.act { animateTrim(t, TrimAnim.ERASE_FROM_START, state.frame, duration) }; state.seek(state.frame); state.playing = true })
            SmallButton(tr("Remove", "حذف برش"), { state.act { removeTrim(t) } })
        }
    }
}

@Composable
private fun EffectsSection(state: EditorState, t: Target) {
    val nulls = state.session.effectNullsOf(t.layer)
    Section(tr("Effects", "افکت‌ها"), defaultOpen = nulls.isNotEmpty()) {
        for (n in nulls) {
            val title = state.session.doc.layers.getOrNull(n.index)?.name.orEmpty().removePrefix(ir.sina.tgsstudio.editor.EffectEngine.FX_PREFIX)
            val label = ir.sina.tgsstudio.editor.EffectKind.entries.firstOrNull { it.title == title }?.let { kindLabel(it) } ?: title
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Effects, StudioColors.Accent, 18.dp)
                Text(label, color = StudioColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                IconBtn(Ic.Trash, { state.act { removeEffectNull(n) } }, size = 38.dp, iconSize = 18.dp, tint = StudioColors.Danger)
            }
        }
        SmallButton(tr("Add effect…", "افزودن افکت…"), { state.updatePanel(PanelTab.EFFECTS) }, primary = true, modifier = Modifier.padding(top = 6.dp), ic = Ic.Effects)
    }
}
