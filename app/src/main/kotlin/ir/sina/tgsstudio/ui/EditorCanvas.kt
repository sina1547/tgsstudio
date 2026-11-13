package ir.sina.tgsstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import ir.sina.tgsstudio.editor.Box
import ir.sina.tgsstudio.editor.PathOps
import ir.sina.tgsstudio.document.ShapeRef
import ir.sina.tgsstudio.renderer.Affine
import ir.sina.tgsstudio.renderer.Bezier
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

private enum class Mode { NONE, PAN, MOVE, SCALE, ROTATE, NODE_VERTEX, NODE_HANDLE_IN, NODE_HANDLE_OUT, SHAPE, PEN, PINCH }

private fun checkerTile(): ImageBitmap {
    val bmp = ImageBitmap(32, 32)
    val c = androidx.compose.ui.graphics.Canvas(bmp)
    val a = Paint().apply { color = StudioColors.CheckerA }; val b = Paint().apply { color = StudioColors.CheckerB }
    c.drawRect(0f, 0f, 32f, 32f, a)
    c.drawRect(16f, 0f, 32f, 16f, b); c.drawRect(0f, 16f, 16f, 32f, b)
    return bmp
}

@Composable
fun EditorCanvas(state: EditorState, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val tile = remember { checkerTile() }
    val checker = remember(tile) { ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)) }
    val tick = state.tick                                           // re-read the session on every change
    val frame = state.frame
    val scene = remember(tick, frame) { state.session.scene(frame) }
    val box = remember(tick, frame, state.tool) { if (state.tool == Tool.SELECT) state.session.selectionBox() else null }
    val docW = state.session.doc.width.toFloat(); val docH = state.session.doc.height.toFloat()

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { s ->
                state.fitSize = Offset(s.width.toFloat(), s.height.toFloat())
                if (!state.viewFitted) state.fit(s.width.toFloat(), s.height.toFloat())
            }
            .pointerInput(state, state.tool, density) { canvasGestures(state, density) },
    ) {
        withTransform({ translate(state.viewOffset.x, state.viewOffset.y); scale(state.viewScale, state.viewScale, Offset.Zero) }) {
            drawRect(checker, size = Size(docW, docH))
            clipRect(0f, 0f, docW, docH) { drawScene(scene) }
            drawRect(Color(0x55FFFFFF), size = Size(docW, docH), style = Stroke(1f / state.viewScale))
        }
        drawOverlay(state, box, density)
    }
}

// ------------------------------------------------------------------------------------------------------
// overlays (screen space)

private fun DrawScope.drawOverlay(state: EditorState, box: Box?, density: Float) {
    val accent = StudioColors.Accent
    val handle = 5f * density
    if (state.tool == Tool.SELECT && box != null) {
        val tl = state.toScreen(box.minX, box.minY); val br = state.toScreen(box.maxX, box.maxY)
        drawRect(accent, tl, Size(br.x - tl.x, br.y - tl.y), style = Stroke(1.5f * density))
        for (c in corners(tl, br)) { drawRect(Color.White, Offset(c.x - handle, c.y - handle), Size(handle * 2, handle * 2)); drawRect(accent, Offset(c.x - handle, c.y - handle), Size(handle * 2, handle * 2), style = Stroke(1.5f * density)) }
        val top = Offset((tl.x + br.x) / 2, tl.y); val rot = Offset(top.x, top.y - 28f * density)
        drawLine(accent, top, rot, 1.5f * density)
        drawCircle(accent, 9f * density, rot); drawCircle(Color.White, 3f * density, rot)
    }
    if (state.tool == Tool.NODE) drawNodes(state, density)
    state.penPath?.let { b -> drawPenPreview(state, b, density) }
    state.dragRect?.let { (a, b) ->
        val p0 = state.toScreen(a.x.toDouble(), a.y.toDouble()); val p1 = state.toScreen(b.x.toDouble(), b.y.toDouble())
        drawRect(accent, Offset(minOf(p0.x, p1.x), minOf(p0.y, p1.y)), Size(kotlin.math.abs(p1.x - p0.x), kotlin.math.abs(p1.y - p0.y)), style = Stroke(1.5f * density))
    }
}

private fun corners(tl: Offset, br: Offset) = listOf(tl, Offset(br.x, tl.y), br, Offset(tl.x, br.y))

private fun DrawScope.drawPathOutline(state: EditorState, b: Bezier, m: Affine, color: Color, width: Float) {
    val pts = b.toContour().transformed(m)
    val p = listOf(pts).toPath()
    withTransform({ translate(state.viewOffset.x, state.viewOffset.y); scale(state.viewScale, state.viewScale, Offset.Zero) }) {
        drawPath(p, color, style = Stroke(width / state.viewScale))
    }
}

private fun DrawScope.drawNodes(state: EditorState, density: Float) {
    val ref = state.session.pathShape() ?: return
    val b = state.session.pathBezier(ref) ?: return
    val m = state.session.pathToDoc(ref)
    drawPathOutline(state, b, m, StudioColors.Accent, 1.5f * density)
    fun at(i: Int, dx: Double = 0.0, dy: Double = 0.0): Offset {
        val x = b.v[2 * i] + dx; val y = b.v[2 * i + 1] + dy
        return state.toScreen(m.mapX(x, y), m.mapY(x, y))
    }
    val sel = state.nodeIndex
    if (sel in 0 until b.count) {
        val v = at(sel); val o = at(sel, b.outT[2 * sel], b.outT[2 * sel + 1]); val i = at(sel, b.inT[2 * sel], b.inT[2 * sel + 1])
        if (b.outT[2 * sel] != 0.0 || b.outT[2 * sel + 1] != 0.0) { drawLine(Color.White, v, o, 1f * density); drawCircle(Color.White, 4.5f * density, o) }
        if (b.inT[2 * sel] != 0.0 || b.inT[2 * sel + 1] != 0.0) { drawLine(Color.White, v, i, 1f * density); drawCircle(Color.White, 4.5f * density, i) }
    }
    val r = 5f * density
    for (i in 0 until b.count) {
        val p = at(i)
        val selected = i == sel
        drawRect(if (selected) StudioColors.Accent else Color.White, Offset(p.x - r, p.y - r), Size(r * 2, r * 2))
        drawRect(StudioColors.Accent, Offset(p.x - r, p.y - r), Size(r * 2, r * 2), style = Stroke(1.5f * density))
    }
}

private fun DrawScope.drawPenPreview(state: EditorState, b: Bezier, density: Float) {
    if (b.count >= 2) drawPathOutline(state, b, Affine.IDENTITY, StudioColors.Accent, 2f * density)
    for (i in 0 until b.count) {
        val p = state.toScreen(b.v[2 * i], b.v[2 * i + 1])
        drawCircle(if (i == 0) StudioColors.Warning else Color.White, 5f * density, p)
        if (b.outT[2 * i] != 0.0 || b.outT[2 * i + 1] != 0.0) drawLine(Color.White, p, state.toScreen(b.v[2 * i] + b.outT[2 * i], b.v[2 * i + 1] + b.outT[2 * i + 1]), 1f * density)
    }
}

// ------------------------------------------------------------------------------------------------------
// gestures

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.canvasGestures(state: EditorState, density: Float) {
    val slop = 8f * density
    val hitRadius = 22f * density
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tool = state.tool
        val session = state.session
        var mode = Mode.NONE
        var last = down.position
        var travelled = 0f
        val startDoc = state.toDoc(down.position)

        // gesture-specific state
        var pivotX = 0.0; var pivotY = 0.0; var prevDist = 1.0; var prevAngle = 0.0
        var nodeRef: ShapeRef? = null; var nodeToLocal: Affine? = null; var dragNode = -1

        fun docDist(a: Offset, b: Offset) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

        when (tool) {
            Tool.HAND -> mode = Mode.PAN
            Tool.SELECT -> {
                val box = session.selectionBox()
                if (box != null) {
                    val tl = state.toScreen(box.minX, box.minY); val br = state.toScreen(box.maxX, box.maxY)
                    val rot = Offset((tl.x + br.x) / 2, tl.y - 28f * density)
                    val cs = corners(tl, br)
                    val ci = cs.indexOfFirst { hypot((it.x - down.position.x).toDouble(), (it.y - down.position.y).toDouble()) <= hitRadius }
                    when {
                        hypot((rot.x - down.position.x).toDouble(), (rot.y - down.position.y).toDouble()) <= hitRadius -> {
                            mode = Mode.ROTATE; pivotX = box.centerX; pivotY = box.centerY
                            prevAngle = atan2(startDoc.y - pivotY, startDoc.x - pivotX); session.beginGesture("Rotate")
                        }
                        ci >= 0 -> {
                            val opp = state.toDoc(cs[(ci + 2) % 4]); pivotX = opp.x.toDouble(); pivotY = opp.y.toDouble()
                            prevDist = docDist(startDoc, opp).coerceAtLeast(1.0); mode = Mode.SCALE; session.beginGesture("Scale")
                        }
                    }
                }
                if (mode == Mode.NONE) {
                    // press on an object: select it (if it is not already) and get ready to drag it
                    val before = session.selection
                    val tolDoc = hitRadius / state.viewScale
                    val inBox = box != null && startDoc.x >= box.minX && startDoc.x <= box.maxX && startDoc.y >= box.minY && startDoc.y <= box.maxY
                    if (!inBox) { session.tap(startDoc.x.toDouble(), startDoc.y.toDouble(), tolDoc.toDouble(), state.multiSelect); state.changed() }
                    val box2 = session.selectionBox()
                    val onSel = box2 != null && startDoc.x >= box2.minX && startDoc.x <= box2.maxX && startDoc.y >= box2.minY && startDoc.y <= box2.maxY
                    mode = if (onSel && session.selection.isNotEmpty() && (before != session.selection || inBox)) Mode.MOVE else Mode.PAN
                    if (mode == Mode.MOVE) session.beginGesture("Move")
                }
            }
            Tool.NODE -> {
                val ref = session.pathShape()
                val b = ref?.let { session.pathBezier(it) }
                if (ref != null && b != null) {
                    val m = session.pathToDoc(ref); val inv = m.inverse()
                    if (inv != null) {
                        nodeRef = ref; nodeToLocal = inv
                        val lx = inv.mapX(startDoc.x.toDouble(), startDoc.y.toDouble()); val ly = inv.mapY(startDoc.x.toDouble(), startDoc.y.toDouble())
                        val scaleLocal = sqrt(kotlin.math.abs(m.determinant)).coerceAtLeast(1e-6)
                        val tol = hitRadius / state.viewScale / scaleLocal
                        val hit = PathOps.hit(b, lx, ly, tol, state.nodeIndex)
                        when (hit?.type) {
                            PathOps.HitType.VERTEX -> { state.nodeIndex = hit.index; dragNode = hit.index; mode = Mode.NODE_VERTEX; session.beginGesture("Move node") }
                            PathOps.HitType.OUT_HANDLE -> { dragNode = hit.index; mode = Mode.NODE_HANDLE_OUT; session.beginGesture("Move handle") }
                            PathOps.HitType.IN_HANDLE -> { dragNode = hit.index; mode = Mode.NODE_HANDLE_IN; session.beginGesture("Move handle") }
                            PathOps.HitType.SEGMENT -> {
                                if (session.structuralPathEdit(ref, "Add node") { PathOps.insert(it, hit.index, hit.t) }) { state.nodeIndex = hit.index + 1; dragNode = state.nodeIndex; mode = Mode.NODE_VERTEX; session.beginGesture("Move node") }
                                state.changed()
                            }
                            null -> mode = Mode.PAN
                        }
                    } else mode = Mode.PAN
                } else mode = Mode.PAN
            }
            Tool.PEN -> {
                val cur = state.penPath ?: Bezier(false, DoubleArray(0), DoubleArray(0), DoubleArray(0))
                val first = if (cur.count > 0) state.toScreen(cur.v[0], cur.v[1]) else null
                if (first != null && cur.count >= 3 && hypot((first.x - down.position.x).toDouble(), (first.y - down.position.y).toDouble()) <= hitRadius) {
                    session.addFreeform(PathOps.setClosed(cur, true)); state.penPath = null; state.tool = Tool.SELECT; state.changed(); mode = Mode.NONE
                } else { state.penPath = PathOps.append(cur, startDoc.x.toDouble(), startDoc.y.toDouble()); mode = Mode.PEN }
            }
            Tool.RECT, Tool.ELLIPSE, Tool.STAR, Tool.POLYGON -> { mode = Mode.SHAPE; state.dragRect = startDoc to startDoc }
        }

        // ---- event loop ----
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) {
                if (mode != Mode.PINCH) { if (session.history.isGestureOpen) { session.endGesture(); state.changed() }; state.dragRect = null; mode = Mode.PINCH }
                state.zoomPan(event.calculateCentroid(), event.calculateZoom(), event.calculatePan())
                event.changes.forEach { it.consume() }
                continue
            }
            if (mode == Mode.PINCH) { if (event.changes.all { !it.pressed }) break else { event.changes.forEach { it.consume() }; continue } }
            val ch = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
            val pos = ch.position
            val d = pos - last
            travelled += hypot(d.x.toDouble(), d.y.toDouble()).toFloat()
            val doc = state.toDoc(pos)

            if (ch.changedToUp()) {
                when (mode) {
                    Mode.NONE, Mode.PAN -> if (travelled < slop && tool == Tool.SELECT) {
                        session.tap(startDoc.x.toDouble(), startDoc.y.toDouble(), (hitRadius / state.viewScale).toDouble(), state.multiSelect); state.changed()
                    } else if (travelled < slop && tool == Tool.NODE) { state.nodeIndex = -1; state.changed() }
                    Mode.MOVE, Mode.SCALE, Mode.ROTATE, Mode.NODE_VERTEX, Mode.NODE_HANDLE_IN, Mode.NODE_HANDLE_OUT -> { session.endGesture(); state.changed() }
                    Mode.SHAPE -> finishShape(state, tool, startDoc, doc)
                    Mode.PEN -> state.changed()
                    Mode.PINCH -> Unit
                }
                ch.consume(); break
            }

            when (mode) {
                Mode.PAN -> state.viewOffset = state.viewOffset + d
                Mode.MOVE -> { session.dragBy((d.x / state.viewScale).toDouble(), (d.y / state.viewScale).toDouble()); state.changed() }
                Mode.SCALE -> {
                    val dist = docDist(doc, Offset(pivotX.toFloat(), pivotY.toFloat())).coerceAtLeast(1.0)
                    session.scaleBy(pivotX, pivotY, dist / prevDist); prevDist = dist; state.changed()
                }
                Mode.ROTATE -> {
                    val ang = atan2(doc.y - pivotY, doc.x - pivotX)
                    session.rotateBy(pivotX, pivotY, Math.toDegrees(ang - prevAngle)); prevAngle = ang; state.changed()
                }
                Mode.NODE_VERTEX, Mode.NODE_HANDLE_IN, Mode.NODE_HANDLE_OUT -> {
                    val ref = nodeRef; val inv = nodeToLocal
                    val b = ref?.let { session.pathBezier(it) }
                    if (ref != null && inv != null && b != null && dragNode in 0 until b.count) {
                        val lx = inv.mapX(doc.x.toDouble(), doc.y.toDouble()); val ly = inv.mapY(doc.x.toDouble(), doc.y.toDouble())
                        val nb = when (mode) {
                            Mode.NODE_VERTEX -> PathOps.moveVertex(b, dragNode, lx, ly)
                            Mode.NODE_HANDLE_OUT -> PathOps.moveHandle(b, dragNode, true, lx, ly)
                            else -> PathOps.moveHandle(b, dragNode, false, lx, ly)
                        }
                        session.writePath(ref, nb); state.changed()
                    }
                }
                Mode.SHAPE -> state.dragRect = startDoc to doc
                Mode.PEN -> state.penPath?.let { b ->
                    val i = b.count - 1
                    if (i >= 0 && travelled > slop) {
                        val dx = doc.x - b.v[2 * i]; val dy = doc.y - b.v[2 * i + 1]
                        val ins = b.inT.copyOf(); val outs = b.outT.copyOf()
                        outs[2 * i] = dx.toDouble(); outs[2 * i + 1] = dy.toDouble(); ins[2 * i] = -dx.toDouble(); ins[2 * i + 1] = -dy.toDouble()
                        state.penPath = Bezier(false, b.v, ins, outs)
                    }
                }
                else -> Unit
            }
            last = pos
            ch.consume()
        }
        if (session.history.isGestureOpen) { session.endGesture(); state.changed() }
        state.dragRect = null
    }
}

private fun finishShape(state: EditorState, tool: Tool, a: Offset, b: Offset) {
    state.dragRect = null
    val w = kotlin.math.abs(b.x - a.x).toDouble(); val h = kotlin.math.abs(b.y - a.y).toDouble()
    if (max(w, h) < 8.0) return
    val cx = ((a.x + b.x) / 2).toDouble(); val cy = ((a.y + b.y) / 2).toDouble()
    state.act {
        when (tool) {
            Tool.RECT -> addRect(cx, cy, w.coerceAtLeast(2.0), h.coerceAtLeast(2.0))
            Tool.ELLIPSE -> addEllipse(cx, cy, w.coerceAtLeast(2.0), h.coerceAtLeast(2.0))
            Tool.STAR -> addStar(cx, cy, max(w, h) / 2)
            Tool.POLYGON -> addPolygon(cx, cy, max(w, h) / 2)
            else -> null
        }
    }
    state.tool = Tool.SELECT
}
