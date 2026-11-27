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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

private enum class Mode { NONE, PAN, MOVE, SCALE, ROTATE, NODE_VERTEX, NODE_HANDLE_IN, NODE_HANDLE_OUT, SHAPE, PEN, DRAW, ERASE, PINCH }

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
    val backgroundHex = state.session.doc.root.string("bg")
    val compositionBackground = remember(backgroundHex) {
        backgroundHex?.removePrefix("#")?.takeIf { it.matches(Regex("^[0-9A-Fa-f]{6}$")) }
            ?.let { hex -> runCatching { Color(android.graphics.Color.parseColor("#" + hex)) }.getOrNull() }
    }

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
            if (compositionBackground != null) drawRect(compositionBackground, size = Size(docW, docH)) else drawRect(checker, size = Size(docW, docH))
            if (state.showGrid) {
                val step = state.gridSpacing.coerceAtLeast(5f)
                var x = step
                while (x < docW) {
                    drawLine(Color(0x557A91B8), Offset(x, 0f), Offset(x, docH), 0.7f / state.viewScale)
                    x += step
                }
                var y = step
                while (y < docH) {
                    drawLine(Color(0x557A91B8), Offset(0f, y), Offset(docW, y), 0.7f / state.viewScale)
                    y += step
                }
            }
            clipRect(0f, 0f, docW, docH) { drawScene(scene) }
            drawRect(Color(0x88FFFFFF), size = Size(docW, docH), style = Stroke(1f / state.viewScale))
        }
        if (state.showRulers) drawCanvasRulers(state, docW, docH, density)
        drawOverlay(state, box, density)
    }
}

// ------------------------------------------------------------------------------------------------------
// Screen-space rulers around the fixed composition frame.
private fun DrawScope.drawCanvasRulers(state: EditorState, docW: Float, docH: Float, density: Float) {
    val tl = state.toScreen(0.0, 0.0)
    val br = state.toScreen(docW.toDouble(), docH.toDouble())
    val band = 18f * density
    val left = (tl.x - band).coerceAtLeast(0f)
    val top = (tl.y - band).coerceAtLeast(0f)
    drawRect(StudioColors.Panel.copy(alpha = 0.94f), Offset(left, top), Size((br.x - left).coerceAtLeast(0f), (tl.y - top).coerceAtLeast(0f)))
    drawRect(StudioColors.Panel.copy(alpha = 0.94f), Offset(left, top), Size((tl.x - left).coerceAtLeast(0f), (br.y - top).coerceAtLeast(0f)))
    val candidates = listOf(10f, 20f, 50f, 100f, 200f, 500f, 1000f)
    val step = candidates.firstOrNull { it * state.viewScale >= 48f * density } ?: 1000f
    var x = 0f
    while (x <= docW) {
        val sx = tl.x + x * state.viewScale
        if (sx >= tl.x && sx <= br.x) {
            drawLine(StudioColors.TextMuted, Offset(sx, tl.y - 7f * density), Offset(sx, tl.y), 1f * density)
            if ((x / step).toInt() % 2 == 0) drawContext.canvas.nativeCanvas.drawText(x.toInt().toString(), sx + 2f * density, tl.y - 8f * density, rulerTextPaint(density))
        }
        x += step
    }
    var y = 0f
    while (y <= docH) {
        val sy = tl.y + y * state.viewScale
        if (sy >= tl.y && sy <= br.y) {
            drawLine(StudioColors.TextMuted, Offset(tl.x - 7f * density, sy), Offset(tl.x, sy), 1f * density)
            if ((y / step).toInt() % 2 == 0) drawContext.canvas.nativeCanvas.drawText(y.toInt().toString(), maxOf(0f, tl.x - 17f * density), sy - 2f * density, rulerTextPaint(density))
        }
        y += step
    }
}

private fun rulerTextPaint(density: Float) = android.graphics.Paint().apply {
    color = android.graphics.Color.LTGRAY
    textSize = 9f * density
    isAntiAlias = true
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
    if (state.drawPoints.size >= 2) {
        val c = state.penColor
        val path = androidx.compose.ui.graphics.Path()
        state.drawPoints.forEachIndexed { i, p -> val sp = state.toScreen(p.x.toDouble(), p.y.toDouble()); if (i == 0) path.moveTo(sp.x, sp.y) else path.lineTo(sp.x, sp.y) }
        drawPath(
            path, Color(c.r.toFloat().coerceIn(0f, 1f), c.g.toFloat().coerceIn(0f, 1f), c.b.toFloat().coerceIn(0f, 1f), (state.penOpacity / 100f).coerceIn(0.05f, 1f)),
            style = Stroke(width = state.penWidth * state.viewScale, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
    }
    state.dragRect?.let { (a, b) ->
        val p0 = state.toScreen(a.x.toDouble(), a.y.toDouble()); val p1 = state.toScreen(b.x.toDouble(), b.y.toDouble())
        val topLeft = Offset(minOf(p0.x, p1.x), minOf(p0.y, p1.y))
        val shapeSize = Size(kotlin.math.abs(p1.x - p0.x), kotlin.math.abs(p1.y - p0.y))
        val radius = (state.rectRoundness * state.viewScale).coerceIn(0f, minOf(shapeSize.width, shapeSize.height) / 2f)
        if (state.tool == Tool.RECT && radius > 0f) {
            drawRoundRect(accent, topLeft, shapeSize, CornerRadius(radius, radius), style = Stroke(1.5f * density))
        } else {
            drawRect(accent, topLeft, shapeSize, style = Stroke(1.5f * density))
        }
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

/**
 * Render the current composition off-screen and sample its actual composited pixel.
 * Sampling uses document coordinates and the same SceneDrawer as the visible canvas.
 */
private fun sampleCompositionColor(state: EditorState, point: Offset): ir.sina.tgsstudio.renderer.Rgba? {
    val doc = state.session.doc
    if (point.x < 0f || point.y < 0f || point.x >= doc.width || point.y >= doc.height) return null
    val maxSide = 2048
    val scale = minOf(1f, maxSide.toFloat() / maxOf(doc.width, doc.height).coerceAtLeast(1))
    val width = (doc.width * scale).toInt().coerceAtLeast(1)
    val height = (doc.height * scale).toInt().coerceAtLeast(1)
    val bitmap = ImageBitmap(width, height)
    val scene = state.session.scene(state.frame)
    val bg = doc.root.string("bg")?.removePrefix("#")
        ?.takeIf { it.matches(Regex("^[0-9A-Fa-f]{6}$")) }
        ?.let { runCatching { Color(android.graphics.Color.parseColor("#$it")) }.getOrNull() }
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        if (bg != null) drawRect(bg, size = Size(width.toFloat(), height.toFloat()))
        withTransform({ scale(scale, scale, Offset.Zero) }) { drawScene(scene) }
    }
    val x = (point.x * scale).toInt().coerceIn(0, width - 1)
    val y = (point.y * scale).toInt().coerceIn(0, height - 1)
    val pixel = bitmap.asAndroidBitmap().getPixel(x, y)
    return ir.sina.tgsstudio.renderer.Rgba(
        android.graphics.Color.red(pixel) / 255.0,
        android.graphics.Color.green(pixel) / 255.0,
        android.graphics.Color.blue(pixel) / 255.0,
        android.graphics.Color.alpha(pixel) / 255.0,
    )
}

// ------------------------------------------------------------------------------------------------------
// gestures

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.canvasGestures(state: EditorState, density: Float) {
    val slop = 8f * density
    val hitRadius = 22f * density
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tool = if (state.fxPreviewing) Tool.HAND else state.tool   // while an effect preview is open the canvas only pans
        val session = state.session
        var mode = Mode.NONE
        var last = down.position
        var travelled = 0f
        val startDoc = state.toDoc(down.position)

        if (state.eyedropperActive) {
            val sampled = sampleCompositionColor(state, startDoc)
            if (sampled != null) state.eyedropperCallback?.invoke(sampled)
            state.eyedropperActive = false
            state.eyedropperCallback = null
            state.message = if (state.isFa) { if (sampled == null) "داخل محدودهٔ کامپوزیشن نمونه‌برداری کنید" else "رنگ نمونه‌برداری شد" } else { if (sampled == null) "Tap inside the composition" else "Color sampled" }
            state.changed()
            return@awaitEachGesture
        }

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
            Tool.ADD_NODE, Tool.DELETE_NODE, Tool.SMOOTH_NODE, Tool.CORNER_NODE -> {
                applyPathPointTool(state, tool, startDoc, hitRadius)
                mode = Mode.NONE
            }
            Tool.PEN -> {
                val cur = state.penPath ?: Bezier(false, DoubleArray(0), DoubleArray(0), DoubleArray(0))
                val first = if (cur.count > 0) state.toScreen(cur.v[0], cur.v[1]) else null
                if (first != null && cur.count >= 3 && hypot((first.x - down.position.x).toDouble(), (first.y - down.position.y).toDouble()) <= hitRadius) {
                    session.addFreeform(PathOps.setClosed(cur, true)); state.penPath = null; state.tool = Tool.SELECT; state.changed(); mode = Mode.NONE
                } else { state.penPath = PathOps.append(cur, startDoc.x.toDouble(), startDoc.y.toDouble()); mode = Mode.PEN }
            }
            Tool.LINE, Tool.RECT, Tool.ELLIPSE, Tool.STAR, Tool.POLYGON, Tool.ARC, Tool.SPIRAL -> { mode = Mode.SHAPE; state.dragRect = startDoc to startDoc }
            Tool.DRAW -> { mode = Mode.DRAW; state.drawPoints = listOf(startDoc) }
            Tool.ERASE -> {
                mode = Mode.ERASE; session.beginGesture("Erase")
                if (session.eraseStrokeAt(startDoc.x.toDouble(), startDoc.y.toDouble(), (hitRadius / state.viewScale).toDouble())) state.changed()
            }
        }

        // ---- event loop ----
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) {
                if (mode != Mode.PINCH) { if (session.history.isGestureOpen) { session.endGesture(); state.changed() }; state.dragRect = null; state.drawPoints = emptyList(); mode = Mode.PINCH }
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
                    Mode.DRAW -> finishDraw(state)
                    Mode.ERASE -> { session.endGesture(); state.changed() }
                    Mode.PEN -> state.changed()
                    Mode.PINCH -> Unit
                }
                ch.consume(); break
            }

            when (mode) {
                Mode.PAN -> Unit // Keep the document canvas anchored; empty-space drags do not move the canvas.
                Mode.MOVE -> {
                    val dx = if (state.snapToGrid) {
                        val step = state.gridSpacing.coerceAtLeast(5f)
                        val current = kotlin.math.round(doc.x / step) * step
                        val previousDoc = state.toDoc(last)
                        val previous = kotlin.math.round(previousDoc.x / step) * step
                        (current - previous).toDouble()
                    } else (d.x / state.viewScale).toDouble()
                    val dy = if (state.snapToGrid) {
                        val step = state.gridSpacing.coerceAtLeast(5f)
                        val current = kotlin.math.round(doc.y / step) * step
                        val previousDoc = state.toDoc(last)
                        val previous = kotlin.math.round(previousDoc.y / step) * step
                        (current - previous).toDouble()
                    } else (d.y / state.viewScale).toDouble()
                    session.dragBy(dx, dy); state.changed()
                }
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
                Mode.SHAPE -> state.dragRect = shapeBounds(state, startDoc, doc)
                Mode.DRAW -> {
                    val prev = state.drawPoints.lastOrNull()
                    if (prev == null || hypot((doc.x - prev.x).toDouble(), (doc.y - prev.y).toDouble()) * state.viewScale >= 1.5) state.drawPoints = state.drawPoints + doc
                }
                Mode.ERASE -> if (session.eraseStrokeAt(doc.x.toDouble(), doc.y.toDouble(), (hitRadius / state.viewScale).toDouble())) state.changed()
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

/** Apply a one-shot anchor-point operation to the currently selected editable path. */
private fun applyPathPointTool(state: EditorState, tool: Tool, point: Offset, hitRadius: Float) {
    val ref = state.session.pathShape()
    val b = ref?.let { state.session.pathBezier(it) }
    val inverse = ref?.let { state.session.pathToDoc(it).inverse() }
    if (ref == null || b == null || inverse == null) {
        state.message = if (state.isFa) "ابتدا یک مسیر قابل ویرایش انتخاب کنید." else "Select an editable path first."
        return
    }
    val x = inverse.mapX(point.x.toDouble(), point.y.toDouble())
    val y = inverse.mapY(point.x.toDouble(), point.y.toDouble())
    val scaleLocal = sqrt(kotlin.math.abs(state.session.pathToDoc(ref).determinant)).coerceAtLeast(1e-6)
    val tolerance = hitRadius / state.viewScale / scaleLocal
    val hit = PathOps.hit(b, x, y, tolerance, state.nodeIndex)
    val changed = when (tool) {
        Tool.ADD_NODE -> if (hit?.type == PathOps.HitType.SEGMENT) {
            val inserted = state.session.structuralPathEdit(ref, "Add anchor point") { PathOps.insert(it, hit.index, hit.t) }
            if (inserted) state.nodeIndex = hit.index + 1
            inserted
        } else false
        Tool.DELETE_NODE -> if (hit?.type == PathOps.HitType.VERTEX) {
            val deleted = state.session.structuralPathEdit(ref, "Delete anchor point") { PathOps.delete(it, hit.index) }
            if (deleted) state.nodeIndex = -1
            deleted
        } else false
        Tool.SMOOTH_NODE -> if (hit?.type == PathOps.HitType.VERTEX) {
            state.nodeIndex = hit.index
            state.session.edit("Smooth anchor point") { state.session.writePath(ref, PathOps.toSmooth(b, hit.index)) }
            true
        } else false
        Tool.CORNER_NODE -> if (hit?.type == PathOps.HitType.VERTEX) {
            state.nodeIndex = hit.index
            state.session.edit("Corner anchor point") { state.session.writePath(ref, PathOps.toCorner(b, hit.index)) }
            true
        } else false
        else -> false
    }
    state.message = when {
        changed -> if (state.isFa) "نقطهٔ مسیر ویرایش شد" else "Anchor point updated"
        tool == Tool.DELETE_NODE && hit?.type == PathOps.HitType.VERTEX -> if (state.isFa) "مسیر حداقل دو نقطه لازم دارد." else "A path needs at least two points."
        else -> if (state.isFa) "روی نقطه یا قطعهٔ مسیر بزنید." else "Tap an anchor point or path segment."
    }
    state.changed()
}

private fun shapeBounds(state: EditorState, a: Offset, b: Offset): Pair<Offset, Offset> {
    var dx = b.x - a.x
    var dy = b.y - a.y
    if (state.constrainShape) {
        val side = max(kotlin.math.abs(dx), kotlin.math.abs(dy))
        dx = kotlin.math.sign(dx) * side
        dy = kotlin.math.sign(dy) * side
    }
    return if (state.drawFromCenter) {
        Offset(a.x - dx, a.y - dy) to Offset(a.x + dx, a.y + dy)
    } else a to Offset(a.x + dx, a.y + dy)
}

private fun finishShape(state: EditorState, tool: Tool, a: Offset, b: Offset) {
    state.dragRect = null
    val (p0, p1) = shapeBounds(state, a, b)
    val w = kotlin.math.abs(p1.x - p0.x).toDouble(); val h = kotlin.math.abs(p1.y - p0.y).toDouble()
    if (max(w, h) < 8.0) return
    val cx = ((p0.x + p1.x) / 2).toDouble(); val cy = ((p0.y + p1.y) / 2).toDouble()
    state.act {
        when (tool) {
            Tool.LINE -> addFreeform(Bezier(false, doubleArrayOf(p0.x.toDouble(), p0.y.toDouble(), p1.x.toDouble(), p1.y.toDouble()), DoubleArray(4), DoubleArray(4)), filled = false)
            Tool.RECT -> addRect(cx, cy, w.coerceAtLeast(2.0), h.coerceAtLeast(2.0), roundness = state.rectRoundness.toDouble())
            Tool.ELLIPSE -> addEllipse(cx, cy, w.coerceAtLeast(2.0), h.coerceAtLeast(2.0))
            Tool.STAR -> addStar(cx, cy, max(w, h) / 2, points = state.starPoints)
            Tool.POLYGON -> addPolygon(cx, cy, max(w, h) / 2, points = state.polygonSides)
            Tool.ARC -> addFreeform(parametricCurve(cx, cy, max(w, h) / 2, state.arcDegrees, 1), filled = false)
            Tool.SPIRAL -> addFreeform(parametricCurve(cx, cy, max(w, h) / 2, 360 * state.spiralTurns, state.spiralTurns), filled = false)
            else -> null
        }
    }
    state.tool = Tool.SELECT
}

/** Builds editable open paths for the Arc and Spiral tools from sampled parametric curves. */
private fun parametricCurve(cx: Double, cy: Double, radius: Double, degrees: Int, turns: Int): Bezier {
    val samples = (max(24, degrees / 4)).coerceAtMost(720)
    val vertices = DoubleArray((samples + 1) * 2)
    val incoming = DoubleArray(vertices.size)
    val outgoing = DoubleArray(vertices.size)
    for (i in 0..samples) {
        val t = i.toDouble() / samples
        val angle = -PI / 2.0 + (degrees.coerceIn(30, 2160) * PI / 180.0) * t
        val r = if (turns <= 1) radius else radius * (0.08 + 0.92 * t)
        vertices[2 * i] = cx + cos(angle) * r
        vertices[2 * i + 1] = cy + sin(angle) * r
    }
    // Catmull-Rom tangents become relative cubic Bézier handles, avoiding the polygonal
    // appearance of zero-handle sampled paths while keeping every point editable.
    for (i in 0..samples) {
        val px = vertices[2 * i]; val py = vertices[2 * i + 1]
        if (i == 0) {
            outgoing[0] = (vertices[2] - px) / 3.0
            outgoing[1] = (vertices[3] - py) / 3.0
        } else if (i == samples) {
            incoming[2 * i] = (vertices[2 * (i - 1)] - px) / 3.0
            incoming[2 * i + 1] = (vertices[2 * (i - 1) + 1] - py) / 3.0
        } else {
            val dx = (vertices[2 * (i + 1)] - vertices[2 * (i - 1)]) / 6.0
            val dy = (vertices[2 * (i + 1) + 1] - vertices[2 * (i - 1) + 1]) / 6.0
            outgoing[2 * i] = dx; outgoing[2 * i + 1] = dy
            incoming[2 * i] = -dx; incoming[2 * i + 1] = -dy
        }
    }
    return Bezier(false, vertices, incoming, outgoing)
}

/** Pen up: fit the touch points into a path layer; with write-on, time it from the playhead (or right after the previous stroke). */
private fun finishDraw(state: EditorState) {
    val pts = state.drawPoints.map { it.x.toDouble() to it.y.toDouble() }
    state.drawPoints = emptyList()
    if (pts.size < 2) return
    val doc = state.session.doc
    var writeOn: ir.sina.tgsstudio.editor.WriteOn? = null
    if (state.penWriteOn) {
        val wanted = ir.sina.tgsstudio.editor.Freehand.length(pts) / state.penSpeed.coerceAtLeast(1f)
        val start = Math.rint(state.penNextStart?.takeIf { state.frame <= it } ?: state.frame)
        val frames = Math.rint(minOf(wanted.coerceIn(8.0, 120.0), doc.outPoint - 1 - start))
        if (frames >= 2.0) { writeOn = ir.sina.tgsstudio.editor.WriteOn(start, frames); state.penNextStart = start + frames }
        else state.message = if (state.isFa) "جایی برای انیمیشن نوشتن در خط زمان نمانده است." else "No room left on the timeline for a write-on animation."
    }
    state.act { addStroke(pts, state.penColor, state.penWidth.toDouble(), state.penOpacity.toDouble(), writeOn) }
}
