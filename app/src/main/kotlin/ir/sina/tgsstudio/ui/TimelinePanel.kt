package ir.sina.tgsstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.sina.tgsstudio.editor.Easing
import ir.sina.tgsstudio.editor.Interp
import ir.sina.tgsstudio.editor.KeyframeOps
import ir.sina.tgsstudio.editor.PropEntry
import ir.sina.tgsstudio.editor.Spaces
import kotlin.math.abs
import kotlin.math.hypot

private val LABEL_W = 132.dp
private val ROW_H = 40.dp

/** Time-based editor for the keyframes of the selected element. Frames run left to right in both languages. */
@Composable
fun TimelinePanel(state: EditorState) {
    val tick = state.tick
    val doc = state.session.doc
    val target = state.session.selection.firstOrNull()
    val rows = remember(tick, target) { if (target == null) emptyList() else state.session.animatedProps(target) }
    val scope = target?.layer?.scope
    val density = LocalDensity.current.density
    val ppf = state.timelinePxPerFrame
    val totalFrames = (doc.outPoint - doc.inPoint).coerceAtLeast(1.0)
    val contentWidthDp = (totalFrames * ppf / density).toFloat().dp + 40.dp
    val hScroll = rememberScrollState()

    Ltr {
        Column(Modifier.fillMaxWidth()) {
            // toolbar for the selected keyframe
            val sk = state.selectedKey
            val selEntry = sk?.let { rows.getOrNull(it.first) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Ic.Timeline, StudioColors.Accent, 20.dp)
                Text(tr("Timeline", "تایم‌لاین"), color = StudioColors.TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                IconBtn(Ic.Minus, { state.timelinePxPerFrame = (state.timelinePxPerFrame / 1.4f).coerceAtLeast(1.5f) }, size = 36.dp, iconSize = 18.dp)
                IconBtn(Ic.Plus, { state.timelinePxPerFrame = (state.timelinePxPerFrame * 1.4f).coerceAtMost(40f) }, size = 36.dp, iconSize = 18.dp)
            }
            if (selEntry != null && sk != null) KeyframeBar(state, selEntry, sk.second)

            if (target == null || rows.isEmpty()) {
                Text(tr("Select a layer or shape to see its animatable properties.", "یک لایه یا شکل را انتخاب کنید تا ویژگی‌های قابل انیمیشنش را ببینید."), color = StudioColors.TextMuted, modifier = Modifier.padding(16.dp))
                return@Column
            }
            Row(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Column(Modifier.width(LABEL_W)) {
                    Box(Modifier.height(28.dp))
                    rows.forEach { r -> Row(Modifier.height(ROW_H).padding(start = (4 + r.depth * 8).dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${propLabel(r.key)}", color = StudioColors.TextPrimary, fontSize = 12.sp, maxLines = 1)
                    } }
                }
                Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                    TimelineCanvas(state, rows, scope, contentWidthDp)
                }
            }
        }
    }
}

@Composable
private fun TimelineCanvas(state: EditorState, rows: List<PropEntry>, scope: String?, widthDp: androidx.compose.ui.unit.Dp) {
    val density = LocalDensity.current.density
    val doc = state.session.doc
    val ppf = state.timelinePxPerFrame
    val ip = doc.inPoint
    val rowPx = ROW_H.value * density
    val rulerPx = 28f * density
    val heightDp = 28.dp + ROW_H * rows.size
    val frame = state.frame
    val tick = state.tick

    Canvas(
        Modifier.width(widthDp).height(heightDp).background(StudioColors.Panel)
            .pointerInput(rows, ppf, tick) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val localOf = { f: Double -> Spaces.localFrame(state.session.index, scope, f) }
                    fun frameAt(x: Float) = ip + x / ppf
                    // which keyframe (if any) is under the finger?
                    var hitRow = -1; var hitKey = -1
                    val r = ((down.position.y - rulerPx) / rowPx).toInt()
                    if (down.position.y > rulerPx && r in rows.indices) {
                        KeyframeOps.times(rows[r].prop).forEachIndexed { i, t -> if (abs((t - ip) * ppf - down.position.x) <= 18f * density) { hitRow = r; hitKey = i } }
                    }
                    if (hitRow >= 0) {
                        state.selectedKey = hitRow to hitKey; state.changed()
                        state.session.beginGesture("Move keyframe")
                        var moved = false
                        var curIndex = hitKey
                        while (true) {
                            val ev = awaitPointerEvent(); val ch = ev.changes.first()
                            if (ch.changedToUp()) { ch.consume(); break }
                            val f = Math.rint(frameAt(ch.position.x))
                            val t0 = KeyframeOps.times(rows[hitRow].prop).getOrNull(curIndex) ?: break
                            if (abs(f - t0) >= 1.0 && abs(ch.position.x - down.position.x) > 6f * density || moved) {
                                moved = true
                                val ni = KeyframeOps.move(rows[hitRow].prop, curIndex, f.coerceIn(ip, doc.outPoint))
                                curIndex = ni; state.selectedKey = hitRow to ni; state.changed()
                            }
                            ch.consume()
                        }
                        state.session.endGesture(); state.changed()
                    } else {
                        // scrub the playhead
                        state.playing = false
                        state.seek(Math.rint(frameAt(down.position.x)))
                        while (true) {
                            val ev = awaitPointerEvent(); val ch = ev.changes.first()
                            if (ch.changedToUp()) { ch.consume(); break }
                            state.seek(Math.rint(frameAt(ch.position.x))); ch.consume()
                        }
                        state.selectedKey = null
                    }
                }
            },
    ) {
        // ruler
        val step = niceStep(ppf, density)
        var f = Math.ceil(ip / step) * step
        while (f <= doc.outPoint) {
            val x = ((f - ip) * ppf).toFloat()
            drawLine(StudioColors.Divider, Offset(x, rulerPx - 8f * density), Offset(x, size.height), 1f)
            drawContext.canvas.nativeCanvas.drawText(f.toInt().toString(), x + 3f * density, rulerPx - 10f * density, textPaint(density))
            f += step
        }
        // rows
        rows.forEachIndexed { i, r ->
            val top = rulerPx + i * rowPx
            drawLine(StudioColors.Divider, Offset(0f, top + rowPx), Offset(size.width, top + rowPx), 1f)
            val kfs = KeyframeOps.times(r.prop)
            if (kfs.size >= 2) drawRoundBar(Offset(((kfs.first() - ip) * ppf).toFloat(), top + rowPx * 0.28f), Offset(((kfs.last() - ip) * ppf).toFloat(), top + rowPx * 0.72f), barColor(r.key))
            kfs.forEachIndexed { k, t ->
                val sel = state.selectedKey == (i to k)
                drawDiamond(Offset(((t - ip) * ppf).toFloat(), top + rowPx / 2), (if (sel) 9f else 7f) * density, if (sel) Color.White else Color(0xFFDCE6FF), sel)
            }
        }
        // playhead
        val px = ((frame - ip) * ppf).toFloat()
        drawLine(StudioColors.Accent, Offset(px, 0f), Offset(px, size.height), 2f * density)
        drawCircle(StudioColors.Accent, 6f * density, Offset(px, 6f * density))
    }
}

private fun barColor(key: String) = when (key) { "c", "o" -> StudioColors.Violet; "path", "w" -> StudioColors.Green; else -> StudioColors.Accent }.copy(alpha = 0.55f)

private fun niceStep(ppf: Float, density: Float): Double {
    val minPx = 44f * density
    for (s in listOf(1.0, 2.0, 5.0, 10.0, 15.0, 30.0, 60.0, 120.0)) if (s * ppf >= minPx) return s
    return 120.0
}

private fun textPaint(density: Float) = android.graphics.Paint().apply { color = 0xFF8795B4.toInt(); textSize = 10f * density; isAntiAlias = true }

private fun DrawScope.drawRoundBar(a: Offset, b: Offset, c: Color) { drawRoundRect(c, a, Size(b.x - a.x, b.y - a.y), androidx.compose.ui.geometry.CornerRadius(6f)) }

private fun DrawScope.drawDiamond(c: Offset, r: Float, color: Color, outline: Boolean) {
    val p = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }
    drawPath(p, color, style = Fill)
    if (outline) drawPath(p, StudioColors.Accent, style = Stroke(2f))
}

/** Actions for the selected keyframe: interpolation, easing, copy/paste, delete, value. */
@Composable
private fun KeyframeBar(state: EditorState, entry: PropEntry, index: Int) {
    val mode = KeyframeOps.interpolation(entry.prop, index)
    val times = KeyframeOps.times(entry.prop)
    if (index !in times.indices) return
    Column(Modifier.fillMaxWidth().background(StudioColors.PanelRaised).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${propLabel(entry.key)} · ${tr("frame", "فریم")} ${fmt(times[index], 0)}", color = StudioColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconBtn(Ic.Copy, { state.copiedKeyframe = KeyframeOps.copy(entry.prop, index); state.message = if (state.isFa) "کیفریم کپی شد" else "Keyframe copied"; state.changed() }, size = 36.dp, iconSize = 18.dp)
            IconBtn(Ic.Add, { state.copiedKeyframe?.let { kf -> state.act { pasteKeyframe(entry, kf) } } }, enabled = state.copiedKeyframe != null, size = 36.dp, iconSize = 18.dp)
            IconBtn(Ic.Trash, { state.act { removeKeyframe(entry, index) }; state.selectedKey = null }, size = 36.dp, iconSize = 18.dp)
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton(tr("Linear", "خطی"), { state.act { setInterpolation(entry, index, Interp.LINEAR) } }, primary = mode == Interp.LINEAR)
            SmallButton(tr("Hold", "ثابت"), { state.act { setInterpolation(entry, index, Interp.HOLD) } }, primary = mode == Interp.HOLD)
            SmallButton(tr("Ease", "نرم"), { state.act { setInterpolation(entry, index, Interp.BEZIER) } }, primary = mode == Interp.BEZIER)
        }
        if (mode == Interp.BEZIER) {
            val e = KeyframeOps.easing(entry.prop, index) ?: Easing.EASE_IN_OUT
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton(tr("In-Out", "ورود-خروج"), { state.act { setEasing(entry, index, Easing.EASE_IN_OUT) } })
                SmallButton(tr("In", "ورود"), { state.act { setEasing(entry, index, Easing.EASE_IN) } })
                SmallButton(tr("Out", "خروج"), { state.act { setEasing(entry, index, Easing.EASE_OUT) } })
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField(e.ox, { state.act { setEasing(entry, index, e.copy(ox = it)) } }, Modifier.weight(1f), decimals = 2, label = "x1")
                NumberField(e.oy, { state.act { setEasing(entry, index, e.copy(oy = it)) } }, Modifier.weight(1f), decimals = 2, label = "y1")
                NumberField(e.ix, { state.act { setEasing(entry, index, e.copy(ix = it)) } }, Modifier.weight(1f), decimals = 2, label = "x2")
                NumberField(e.iy, { state.act { setEasing(entry, index, e.copy(iy = it)) } }, Modifier.weight(1f), decimals = 2, label = "y2")
            }
        }
        if (entry.kind != ir.sina.tgsstudio.editor.PropKind.PATH) {
            val vals = (KeyframeOps.list(entry.prop).getOrNull(index)?.get("s") as? ir.sina.tgsstudio.document.json.JsonArr)?.items?.mapNotNull { (it as? ir.sina.tgsstudio.document.json.JsonNum)?.value?.toDouble() }?.toDoubleArray()
            if (vals != null) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                vals.take(4).forEachIndexed { i, d -> NumberField(d, { nv -> val c = vals.copyOf(); c[i] = nv; state.act { setKeyframeValue(entry, index, c) } }, Modifier.weight(1f), decimals = 2) }
            }
        }
    }
}
