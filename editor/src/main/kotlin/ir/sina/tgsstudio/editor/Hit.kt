package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.renderer.Affine
import ir.sina.tgsstudio.renderer.Contour
import ir.sina.tgsstudio.renderer.PaintItem
import ir.sina.tgsstudio.renderer.Scene
import ir.sina.tgsstudio.renderer.SceneLayer
import kotlin.math.hypot
import kotlin.math.sqrt

class HitResult(val layer: LayerRef, val shapePath: List<Int>)

class Box(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val width get() = maxX - minX
    val height get() = maxY - minY
    val centerX get() = (minX + maxX) / 2
    val centerY get() = (minY + maxY) / 2
    fun union(o: Box) = Box(minOf(minX, o.minX), minOf(minY, o.minY), maxOf(maxX, o.maxX), maxOf(maxY, o.maxY))
}

object Hit {
    private const val FLATTEN = 10

    /** Topmost layer/shape under the document-space point (x, y); [tolerance] is in document units. */
    fun at(scene: Scene, x: Double, y: Double, tolerance: Double): HitResult? = hitLayers(scene.layers, Affine.IDENTITY, x, y, tolerance)

    private fun hitLayers(layers: List<SceneLayer>, outer: Affine, x: Double, y: Double, tol: Double): HitResult? {
        for (layer in layers.asReversed()) {          // scene lists bottom layer first
            val m = outer * layer.matrix
            hitLayers(layer.children, m, x, y, tol)?.let { return it }
            for (item in layer.items.asReversed()) {
                if (hitItem(item, m * item.matrix, x, y, tol)) return HitResult(layer.ref, item.shapePath.take(1))
            }
        }
        return null
    }

    private fun hitItem(item: PaintItem, m: Affine, x: Double, y: Double, tol: Double): Boolean {
        val inv = m.inverse() ?: return false
        val lx = inv.mapX(x, y); val ly = inv.mapY(x, y)
        val scale = sqrt(Math.abs(m.determinant)).takeIf { it > 1e-9 } ?: return false
        val st = item.stroke
        if (st == null) {
            if (item.paint.opacity <= 0.0) return false
            // inside any closed/open contour (open contours are filled by closing them, as renderers do)
            val windings = item.contours.sumOf { winding(it, lx, ly) }
            return if (item.evenOdd) windings % 2 != 0 else windings != 0
        }
        val reach = (st.width / 2) + tol / scale
        return item.contours.any { distanceTo(it, lx, ly) <= reach }
    }

    private fun winding(c: Contour, px: Double, py: Double): Int {
        var w = 0
        for (s in c.segments) {
            val pts = s.flatten(FLATTEN)
            for (i in 0 until pts.size / 2 - 1) w += cross(pts[2 * i], pts[2 * i + 1], pts[2 * i + 2], pts[2 * i + 3], px, py)
        }
        val first = c.segments.firstOrNull(); val last = c.segments.lastOrNull()
        if (first != null && last != null && !c.closed) w += cross(last.x3, last.y3, first.x0, first.y0, px, py)
        return w
    }

    private fun cross(x0: Double, y0: Double, x1: Double, y1: Double, px: Double, py: Double): Int {
        if (y0 <= py) { if (y1 > py && (x1 - x0) * (py - y0) - (px - x0) * (y1 - y0) > 0) return 1 }
        else if (y1 <= py && (x1 - x0) * (py - y0) - (px - x0) * (y1 - y0) < 0) return -1
        return 0
    }

    private fun distanceTo(c: Contour, px: Double, py: Double): Double {
        var best = Double.MAX_VALUE
        for (s in c.segments) {
            val p = s.flatten(FLATTEN)
            for (i in 0 until p.size / 2 - 1) best = minOf(best, segDist(p[2 * i], p[2 * i + 1], p[2 * i + 2], p[2 * i + 3], px, py))
        }
        return best
    }

    private fun segDist(x0: Double, y0: Double, x1: Double, y1: Double, px: Double, py: Double): Double {
        val dx = x1 - x0; val dy = y1 - y0
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - x0) * dx + (py - y0) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(px - (x0 + t * dx), py - (y0 + t * dy))
    }

    /** Document-space bounds of everything a layer (or one shape under it) paints; null if it paints nothing. */
    fun bounds(scene: Scene, layer: LayerRef, shapePrefix: List<Int>? = null): Box? = boundsIn(scene.layers, Affine.IDENTITY, layer, shapePrefix)

    private fun boundsIn(layers: List<SceneLayer>, outer: Affine, ref: LayerRef, prefix: List<Int>?): Box? {
        var box: Box? = null
        for (l in layers) {
            val m = outer * l.matrix
            if (l.ref == ref) {
                for (item in l.items) {
                    if (prefix != null && !(item.shapePath.size >= prefix.size && item.shapePath.take(prefix.size) == prefix)) continue
                    box = union(box, itemBox(item, m * item.matrix))
                }
                if (prefix == null) l.children.forEach { c -> box = union(box, allBox(c, m * c.matrix)) }
            }
            box = union(box, boundsIn(l.children, m, ref, prefix))
        }
        return box
    }

    private fun allBox(l: SceneLayer, m: Affine): Box? {
        var b: Box? = null
        l.items.forEach { b = union(b, itemBox(it, m * it.matrix)) }
        l.children.forEach { b = union(b, allBox(it, m * it.matrix)) }
        return b
    }

    private fun itemBox(item: PaintItem, m: Affine): Box? {
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (c in item.contours) for (s in c.segments) {
            val p = s.flatten(8)
            for (i in 0 until p.size / 2) {
                val x = m.mapX(p[2 * i], p[2 * i + 1]); val y = m.mapY(p[2 * i], p[2 * i + 1])
                minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
            }
        }
        if (minX > maxX) return null
        val pad = (item.stroke?.width ?: 0.0) / 2 * sqrt(Math.abs(m.determinant))
        return Box(minX - pad, minY - pad, maxX + pad, maxY + pad)
    }

    private fun union(a: Box?, b: Box?): Box? = if (a == null) b else if (b == null) a else a.union(b)
}
