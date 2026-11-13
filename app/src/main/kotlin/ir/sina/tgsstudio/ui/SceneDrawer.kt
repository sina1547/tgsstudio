package ir.sina.tgsstudio.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import ir.sina.tgsstudio.renderer.Affine
import ir.sina.tgsstudio.renderer.Cap
import ir.sina.tgsstudio.renderer.Contour
import ir.sina.tgsstudio.renderer.Gradient
import ir.sina.tgsstudio.renderer.Join
import ir.sina.tgsstudio.renderer.PaintItem
import ir.sina.tgsstudio.renderer.Scene
import ir.sina.tgsstudio.renderer.SceneLayer

fun Affine.toMatrix(): Matrix = Matrix(
    floatArrayOf(
        a.toFloat(), b.toFloat(), 0f, 0f,
        c.toFloat(), d.toFloat(), 0f, 0f,
        0f, 0f, 1f, 0f,
        e.toFloat(), f.toFloat(), 0f, 1f,
    ),
)

fun List<Contour>.toPath(evenOdd: Boolean = false): Path = Path().also { p ->
    p.fillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
    for (c in this) {
        val first = c.segments.firstOrNull() ?: continue
        p.moveTo(first.x0.toFloat(), first.y0.toFloat())
        for (s in c.segments) p.cubicTo(s.x1.toFloat(), s.y1.toFloat(), s.x2.toFloat(), s.y2.toFloat(), s.x3.toFloat(), s.y3.toFloat())
        if (c.closed) p.close()
    }
}

private val BIG = Rect(-20000f, -20000f, 20000f, 20000f)

/** Draws a [Scene] in document coordinates. Alpha, masks and alpha mattes use offscreen layers only where needed. */
fun DrawScope.drawScene(scene: Scene) {
    for (l in scene.layers) drawSceneLayer(l)
}

private fun DrawScope.drawSceneLayer(l: SceneLayer) {
    val needsLayer = l.opacity < 0.999 || l.matte != null
    if (!needsLayer) { drawLayerBody(l); return }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(BIG, Paint().apply { alpha = l.opacity.toFloat().coerceIn(0f, 1f) })
        drawLayerBody(l)
        l.matte?.let { m ->
            canvas.saveLayer(BIG, Paint().apply { blendMode = if (m.inverted) BlendMode.DstOut else BlendMode.DstIn })
            drawLayerBody(m.layer)
            canvas.restore()
        }
        canvas.restore()
    }
}

private fun DrawScope.drawLayerBody(l: SceneLayer) {
    withTransform({ transform(l.matrix.toMatrix()) }) {
        val add = l.masks.filter { !it.inverted }
        val inv = l.masks.filter { it.inverted }
        val body: DrawScope.() -> Unit = {
            for (item in l.items) drawItem(item)
            for (child in l.children) drawSceneLayer(child)
        }
        var content: DrawScope.() -> Unit = body
        if (add.isNotEmpty()) { val clip = add.flatMap { it.contours }.toPath(); val inner = content; content = { clipPath(clip) { inner() } } }
        for (m in inv) { val clip = m.contours.toPath(); val inner = content; content = { clipPath(clip, ClipOp.Difference) { inner() } } }
        content()
    }
}

private fun DrawScope.drawItem(item: PaintItem) {
    if (item.contours.isEmpty()) return
    val path = item.contours.toPath(item.evenOdd)
    val st = item.stroke
    val style = if (st == null) androidx.compose.ui.graphics.drawscope.Fill else Stroke(
        width = st.width.toFloat(), miter = st.miterLimit.toFloat(),
        cap = when (st.cap) { Cap.BUTT -> StrokeCap.Butt; Cap.ROUND -> StrokeCap.Round; Cap.SQUARE -> StrokeCap.Square },
        join = when (st.join) { Join.MITER -> StrokeJoin.Miter; Join.ROUND -> StrokeJoin.Round; Join.BEVEL -> StrokeJoin.Bevel },
        pathEffect = st.dashes?.let { PathEffect.dashPathEffect(FloatArray(it.size) { i -> it[i].toFloat().coerceAtLeast(0.01f) }, st.dashPhase.toFloat()) },
    )
    withTransform({ transform(item.matrix.toMatrix()) }) {
        val color = item.paint.color
        if (color != null) {
            drawPath(path, Color(color.r.toFloat().coerceIn(0f, 1f), color.g.toFloat().coerceIn(0f, 1f), color.b.toFloat().coerceIn(0f, 1f)), (color.a * item.paint.opacity).toFloat().coerceIn(0f, 1f), style)
        } else item.paint.gradient?.let { g -> drawPath(path, brushOf(g), item.paint.opacity.toFloat().coerceIn(0f, 1f), style) }
    }
}

private fun brushOf(g: Gradient): Brush {
    val stops = g.stops.map { it.position.toFloat().coerceIn(0f, 1f) to Color(it.color.r.toFloat().coerceIn(0f, 1f), it.color.g.toFloat().coerceIn(0f, 1f), it.color.b.toFloat().coerceIn(0f, 1f), it.color.a.toFloat().coerceIn(0f, 1f)) }
        .ifEmpty { listOf(0f to Color.Transparent, 1f to Color.Transparent) }.toTypedArray()
    val start = Offset(g.x0.toFloat(), g.y0.toFloat()); val end = Offset(g.x1.toFloat(), g.y1.toFloat())
    return if (g.radial) Brush.radialGradient(colorStops = stops, center = start, radius = (end - start).getDistance().coerceAtLeast(0.01f))
    else Brush.linearGradient(colorStops = stops, start = start, end = end)
}
